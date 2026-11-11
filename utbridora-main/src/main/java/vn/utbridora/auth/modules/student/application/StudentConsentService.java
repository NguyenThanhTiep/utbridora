package vn.utbridora.auth.modules.student.application;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.modules.cooperation.port.*;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page;
import vn.utbridora.auth.modules.student.port.StudentConsentPort;
import vn.utbridora.auth.operations.OperationLedger;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.operations.WorkflowValues.*;
import static vn.utbridora.auth.modules.cooperation.application.SchoolSharingPolicyService.fields;
@Service
public class StudentConsentService implements StudentConsentPort {
    public record GrantConsent(String invitationId,String purpose,List<String> fieldWhitelist,Instant expiresAt,String policyVersion,Long expectedSchoolPolicyVersion,Long expectedLineageVersion) {}
    public record Revoke(Long expectedLineageVersion,String reasonCode) {}
    private final JdbcTemplate jdbc;private final InternshipInvitationPort invitations;private final SchoolSharingPolicyPort policies;
    private final AuthorizationPolicy core;private final OperationLedger ledger;private final AuditService audit;
    private final vn.utbridora.auth.authorization.SchoolDelegationAccess scope;
    public StudentConsentService(JdbcTemplate jdbc,InternshipInvitationPort invitations,SchoolSharingPolicyPort policies,AuthorizationPolicy core,OperationLedger ledger,AuditService audit,vn.utbridora.auth.authorization.SchoolDelegationAccess scope) {this.jdbc=jdbc;this.invitations=invitations;this.policies=policies;this.core=core;this.ledger=ledger;this.audit=audit;this.scope=scope;}
    @Transactional public ConsentGrant grant(Authentication auth,String key,GrantConsent request) {
        var invite=invitations.readMine(auth,uuid(request.invitationId()));require(auth,invite.schoolOrgId());fields(request.fieldWhitelist(),false);
        if(!"INTERNSHIP_SELECTION".equals(request.purpose()) || request.expiresAt()==null) throw failure(422,"CONSENT_EXPIRY_INVALID");
        if(request.expectedLineageVersion()==null || request.expectedLineageVersion()<0 || request.expectedLineageVersion()>=9007199254740991L || request.expectedSchoolPolicyVersion()==null || request.expectedSchoolPolicyVersion()<0 || request.expectedSchoolPolicyVersion()>9007199254740991L) throw failure(400,"SCHEMA_VALIDATION");text(request.policyVersion(),64);
        return ledger.execute(new OperationLedger.Command(actor(auth),invite.schoolOrgId(),"CONSENT_GRANT",key,request,201),ConsentGrant.class,()->{
            if(!Instant.now().isBefore(request.expiresAt()) || request.expiresAt().isAfter(Instant.now().plusSeconds(30*86400))) throw failure(422,"CONSENT_EXPIRY_INVALID");
            var policy=policies.current(invite.schoolOrgId(),invite.termId());if(policy.version()!=request.expectedSchoolPolicyVersion()) throw failure(409,"VERSION_CONFLICT");
            if(!policy.allowedFields().containsAll(request.fieldWhitelist())) throw failure(422,"CONSENT_FIELDS_NOT_ALLOWED");
            String lineage=lineage(actor(auth),invite.schoolOrgId(),invite.termId(),invite.enterpriseOrgId(),request.purpose());
            jdbc.update("INSERT IGNORE INTO student_consent_lineages VALUES(?,?,?,?,?,?,0,NULL)",lineage,actor(auth),invite.schoolOrgId(),invite.termId(),invite.enterpriseOrgId(),request.purpose());
            long version=jdbc.queryForObject("SELECT version FROM student_consent_lineages WHERE id=? FOR UPDATE",Long.class,lineage);
            if(version!=request.expectedLineageVersion()) throw failure(409,"CONSENT_CHANGED");
            String old=jdbc.queryForObject("SELECT current_consent_id FROM student_consent_lineages WHERE id=?",String.class,lineage),id=UUID.randomUUID().toString();
            var result=new ConsentGrant(id,1,version+1,actor(auth),invite.schoolOrgId(),invite.termId(),invite.enterpriseOrgId(),invite.invitationId(),request.purpose(),List.copyOf(request.fieldWhitelist()),Instant.now(),request.expiresAt(),null,request.policyVersion(),null);
            if(old!=null) {var previous=storedCurrent(old);write(new ConsentGrant(previous.consentId(),previous.consentVersion()+1,version+1,previous.studentUserId(),previous.schoolOrgId(),previous.termId(),previous.recipientOrgId(),previous.invitationId(),previous.purpose(),previous.fieldWhitelist(),previous.grantedAt(),previous.expiresAt(),previous.revokedAt(),previous.policyVersion(),id));}
            jdbc.update("INSERT INTO student_consents VALUES(?,?,?,?)",id,lineage,actor(auth),JSON.writeValueAsString(result));jdbc.update("UPDATE student_consent_lineages SET version=?,current_consent_id=? WHERE id=?",version+1,id,lineage);
            audit.workflow(actor(auth),"CONSENT_GRANTED",id,invite.schoolOrgId(),Map.of("recipientOrgId",invite.enterpriseOrgId(),"schoolOrgId",invite.schoolOrgId(),"termId",invite.termId(),"purpose",request.purpose(),"fieldCodes",request.fieldWhitelist(),"consentVersion",1,"expiresAt",request.expiresAt().toString()));return result;
        },ConsentGrant::consentId);
    }
    @Transactional public ConsentGrant revoke(Authentication auth,String id,String key,Revoke request) {
        var before=stored(uuid(id));if(!before.studentUserId().equals(actor(auth))) throw failure(404,"RESOURCE_NOT_FOUND");require(auth,before.schoolOrgId());version(request.expectedLineageVersion());reason(request.reasonCode());
        return ledger.execute(new OperationLedger.Command(actor(auth),before.schoolOrgId(),"CONSENT_REVOKE",key,Map.of("id",id,"request",request),200),ConsentGrant.class,()->{
            String lineage=lineage(before.studentUserId(),before.schoolOrgId(),before.termId(),before.recipientOrgId(),before.purpose());long current=jdbc.queryForObject("SELECT version FROM student_consent_lineages WHERE id=? FOR UPDATE",Long.class,lineage);
            if(current!=request.expectedLineageVersion()) throw failure(409,"CONSENT_CHANGED");var c=storedCurrent(id);if(c.replacedByConsentId()!=null || c.revokedAt()!=null) throw failure(409,"CONSENT_INVALID");
            var result=new ConsentGrant(c.consentId(),c.consentVersion()+1,current+1,c.studentUserId(),c.schoolOrgId(),c.termId(),c.recipientOrgId(),c.invitationId(),c.purpose(),c.fieldWhitelist(),c.grantedAt(),c.expiresAt(),Instant.now(),c.policyVersion(),null);write(result);jdbc.update("UPDATE student_consent_lineages SET version=? WHERE id=?",current+1,lineage);
            audit.workflow(actor(auth),"CONSENT_REVOKED",id,c.schoolOrgId(),Map.of("consentVersion",result.consentVersion(),"reasonCode",request.reasonCode()));return result;
        },ConsentGrant::consentId);
    }
    @Transactional public Page<ConsentGrant> mine(Authentication auth,int page,int size) {
        page(page,size);var ids=jdbc.queryForList("SELECT id FROM student_consents WHERE student_user_id=? ORDER BY id",String.class,actor(auth));
        var values=ids.stream().map(this::stored).peek(c->require(auth,c.schoolOrgId())).toList();int from=Math.min(page*size,values.size());return new Page<>(values.subList(from,Math.min(from+size,values.size())),page,size,values.size(),(values.size()+size-1)/size);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY) public GrantValidation lockAndCheckForShare(vn.utbridora.auth.contracts.ActorContext actor,GrantCheck request) {
        if(actor==null) throw failure(401,"SESSION_EXPIRED");
        var auth=actor.authentication();
        if(!core.holdOrganization(auth,request.recipientOrgId(),Role.ENTERPRISE,"student_profile:shared:read")
            && !(core.isSelf(auth,request.studentUserId()) && core.holdOrganization(auth,request.schoolOrgId(),Role.STUDENT,"student:consent:self:manage")))
            scope.require(auth,request.schoolOrgId(),request.termId(),"student_profile:share:school:manage");
        fields(request.requestedFields(),false);var c=stored(uuid(request.consentId()));
        String lineage=lineage(c.studentUserId(),c.schoolOrgId(),c.termId(),c.recipientOrgId(),c.purpose());
        jdbc.queryForObject("SELECT version FROM student_consent_lineages WHERE id=? FOR UPDATE",Long.class,lineage);c=storedCurrent(c.consentId());
        String reason=!c.studentUserId().equals(request.studentUserId()) || !c.schoolOrgId().equals(request.schoolOrgId()) || !c.termId().equals(request.termId()) || !c.recipientOrgId().equals(request.recipientOrgId())?"SCOPE_MISMATCH":
            !c.purpose().equals(request.purpose())?"PURPOSE_MISMATCH":c.replacedByConsentId()!=null?"REPLACED":c.revokedAt()!=null?"REVOKED":!Instant.now().isBefore(c.expiresAt())?"EXPIRED":c.consentVersion()!=request.consentVersion()?"REPLACED":!c.fieldWhitelist().containsAll(request.requestedFields())?"FIELD_DENIED":"ALLOWED";
        return new GrantValidation(reason.equals("ALLOWED"),reason.equals("ALLOWED")?request.requestedFields():List.of(),reason.equals("ALLOWED")?c.expiresAt():null,reason.equals("SCOPE_MISMATCH")?null:c.consentVersion(),reason);
    }
    private void require(Authentication auth,String school) {if(!core.holdOrganization(auth,school,Role.STUDENT,"student:consent:self:manage")) throw failure(403,"ACCESS_DENIED");}
    private ConsentGrant stored(String id) {return jdbc.query("SELECT data_json FROM student_consents WHERE id=?",(r,n)->JSON.readValue(r.getString(1),ConsentGrant.class),id).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));}
    private ConsentGrant storedCurrent(String id) {return jdbc.query("SELECT data_json FROM student_consents WHERE id=? FOR UPDATE",(r,n)->JSON.readValue(r.getString(1),ConsentGrant.class),id).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));}
    private void write(ConsentGrant c) {jdbc.update("UPDATE student_consents SET data_json=? WHERE id=?",JSON.writeValueAsString(c),c.consentId());}
    private static String lineage(String student,String school,String term,String recipient,String purpose) {return UUID.nameUUIDFromBytes(String.join("|",student,school,term,recipient,purpose).getBytes(StandardCharsets.UTF_8)).toString();}
}
