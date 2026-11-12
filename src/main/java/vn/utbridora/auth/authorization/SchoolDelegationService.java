package vn.utbridora.auth.authorization;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.operations.OperationLedger;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@Service
public class SchoolDelegationService {
    public record Grant(String userId,List<String> permissionCodes,List<String> profileReadFields,
                        Instant validFrom,Instant validUntil,String decisionRef) {}
    public record Bootstrap(String userId,String schoolOrgId,String termId,List<String> permissionCodes,
                            List<String> profileReadFields,Instant validFrom,Instant validUntil,String decisionRef) {}
    public record Revoke(Long expectedVersion,String reasonCode) {}
    public record Delegation(String id,String userId,String schoolOrgId,String termId,List<String> permissionCodes,
        List<String> profileReadFields,Instant validFrom,Instant validUntil,boolean active,long version,String grantedBy,String decisionRef) {}
    public record RevocationAck(String id,boolean active,long version) {}
    static final Set<String> FIELDS=Set.of("displayName","studentCode","majorCode","skills","projects","certificates","cv","evidence","email","phone");
    private final JdbcTemplate jdbc;
    private final AuthorizationPolicy core;
    private final SchoolDelegationAccess access;
    private final AccountSecurityPort security;
    private final OperationLedger ledger;
    private final AuditService audit;
    private final AdministrationGuard administration;
    public SchoolDelegationService(JdbcTemplate jdbc,AuthorizationPolicy core,SchoolDelegationAccess access,AccountSecurityPort security,OperationLedger ledger,AuditService audit,AdministrationGuard administration) {
        this.jdbc=jdbc;this.core=core;this.access=access;this.security=security;this.ledger=ledger;this.audit=audit;
        this.administration=administration;
    }
    @Transactional public Delegation bootstrap(Authentication auth,String key,Bootstrap request) {
        String user=actor(auth),school=uuid(request.schoolOrgId()),term=uuid(request.termId());
        if(!core.hasGlobal(auth,"membership:admin:write") || user.equals(request.userId())) throw failure(403,"ACCESS_DENIED");
        administration.lockAndCheckActor(user,"membership:admin:write");
        // Explicitly labelled operational demo bootstrap; ordinary grants require an existing parent.
        if(!text(request.decisionRef(),120).startsWith("DEMO-")) throw failure(422,"DEMO_DECISION_REQUIRED");
        Grant grant=new Grant(request.userId(),request.permissionCodes(),request.profileReadFields(),request.validFrom(),request.validUntil(),request.decisionRef());
        validate(grant);
        return ledger.execute(new OperationLedger.Command(user,school,"SCHOOL_DELEGATION_BOOTSTRAP",key,request,201),Delegation.class,
            ()->insert(auth,school,term,null,grant),Delegation::id);
    }
    @Transactional public Delegation grant(Authentication auth,String term,String key,Grant request) {
        term=uuid(term);validate(request);String user=actor(auth);
        String school=jdbc.queryForList("SELECT school_org_id FROM terms WHERE id=? FOR SHARE",String.class,term).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
        access.require(auth,school,term,"school:delegation:manage");
        final String termId=term;
        return ledger.execute(new OperationLedger.Command(user,school,"SCHOOL_DELEGATION_GRANT",key,Map.of("termId",term,"request",request),201),Delegation.class,()->{
            var candidates=jdbc.queryForList("SELECT id FROM school_delegations WHERE user_id=? AND school_org_id=? AND term_id=? ORDER BY id FOR UPDATE",String.class,user,school,termId);
            for(String parent:candidates) {
                Delegation p=read(parent);
                if(access.currentAssignment(parent,user,school,termId,"school:delegation:manage")
                    && p.permissionCodes().containsAll(request.permissionCodes()) && p.profileReadFields().containsAll(request.profileReadFields())
                    && !request.validFrom().isBefore(p.validFrom()) && !request.validUntil().isAfter(p.validUntil()))
                    return insert(auth,school,termId,parent,request);
            }
            throw failure(403,"ACCESS_DENIED");
        },Delegation::id);
    }
    private Delegation insert(Authentication auth,String school,String term,String parent,Grant g) {
        String user=actor(auth),subject=uuid(g.userId());
        if(subject.equals(user) || jdbc.queryForObject("SELECT COUNT(*) FROM terms WHERE id=? AND school_org_id=?",Integer.class,term,school)!=1) throw failure(403,"ACCESS_DENIED");
        var target=core.currentSubject(subject).orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
        for(String permission:g.permissionCodes()) if(!core.holdOrganization(target,school,Role.SCHOOL_STAFF,permission)) throw failure(403,"ACCESS_DENIED");
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO school_delegations(id,user_id,school_org_id,term_id,parent_id,granted_by,decision_ref,profile_read_fields_json,valid_from,valid_until,active,version) VALUES(?,?,?,?,?,?,?,?,?,?,true,1)",
            id,subject,school,term,parent,user,g.decisionRef(),JSON.writeValueAsString(g.profileReadFields()),utc(g.validFrom()),utc(g.validUntil()));
        for(String permission:g.permissionCodes()) jdbc.update("INSERT INTO school_delegation_permissions VALUES(?,?)",id,permission);
        security.invalidate(List.of(subject));
        audit.workflow(user,"CAPABILITY_GRANTED",id,school,Map.of("scopeKind","SCHOOL","scopeId",school,"termId",term,"assignmentKind","SCHOOL","validFrom",g.validFrom().toString(),"validUntil",g.validUntil().toString(),"decisionRef",g.decisionRef()));
        return read(id);
    }
    @Transactional public RevocationAck revoke(Authentication auth,String id,String key,Revoke request) {
        id=uuid(id);version(request.expectedVersion());reason(request.reasonCode());
        Delegation target=read(id);
        if(core.hasGlobal(auth,"membership:admin:write") && target.decisionRef().startsWith("DEMO-")) administration.lockAndCheckActor(actor(auth),"membership:admin:write");
        else access.require(auth,target.schoolOrgId(),target.termId(),"school:delegation:manage");
        String user=actor(auth),targetId=id;
        if(!target.grantedBy().equals(user) || target.userId().equals(user)) throw failure(403,"ACCESS_DENIED");
        return ledger.execute(new OperationLedger.Command(user,target.schoolOrgId(),"SCHOOL_DELEGATION_REVOKE",key,Map.of("id",id,"request",request),200),RevocationAck.class,()->{
            var affected=jdbc.queryForList("WITH RECURSIVE descendants AS (SELECT id,user_id FROM school_delegations WHERE id=? UNION ALL SELECT d.id,d.user_id FROM school_delegations d JOIN descendants p ON d.parent_id=p.id), workspace AS (SELECT id,user_id FROM internship_context_assignments WHERE school_delegation_id IN (SELECT id FROM descendants) UNION ALL SELECT x.id,x.user_id FROM internship_context_assignments x JOIN workspace w ON x.parent_assignment_id=w.id) SELECT user_id FROM descendants UNION SELECT user_id FROM workspace",String.class,targetId);
            security.invalidate(affected);
            if(jdbc.update("UPDATE school_delegations SET active=false,version=version+1 WHERE id=? AND version=? AND active=true",targetId,request.expectedVersion())!=1) throw failure(409,"VERSION_CONFLICT");
            audit.workflow(user,"CAPABILITY_REVOKED",targetId,target.schoolOrgId(),Map.of("scopeKind","SCHOOL","scopeId",target.schoolOrgId(),"termId",target.termId(),"assignmentKind","SCHOOL","reasonCode",request.reasonCode()));
            return new RevocationAck(targetId,false,request.expectedVersion()+1);
        },RevocationAck::id);
    }
    public Delegation read(String id) {
        return jdbc.query("SELECT * FROM school_delegations WHERE id=? FOR SHARE",(r,n)->new Delegation(r.getString("id"),r.getString("user_id"),r.getString("school_org_id"),r.getString("term_id"),
            jdbc.queryForList("SELECT permission_code FROM school_delegation_permissions WHERE assignment_id=? ORDER BY permission_code",String.class,id),
            List.of(JSON.readValue(r.getString("profile_read_fields_json"),String[].class)),instant(r,"valid_from"),instant(r,"valid_until"),r.getBoolean("active"),r.getLong("version"),r.getString("granted_by"),r.getString("decision_ref")),id).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
    }
    private static void validate(Grant g) {
        uuid(g.userId());period(g.validFrom(),g.validUntil());text(g.decisionRef(),120);
        if(g.permissionCodes()==null || g.permissionCodes().stream().anyMatch(Objects::isNull) || g.permissionCodes().isEmpty() || g.permissionCodes().size()>50 || new HashSet<>(g.permissionCodes()).size()!=g.permissionCodes().size()
            || g.profileReadFields()==null || g.profileReadFields().stream().anyMatch(Objects::isNull) || !FIELDS.containsAll(g.profileReadFields()) || new HashSet<>(g.profileReadFields()).size()!=g.profileReadFields().size()
            || g.permissionCodes().contains("student_profile:school:read")==g.profileReadFields().isEmpty()) throw failure(400,"SCHEMA_VALIDATION");
    }
}
