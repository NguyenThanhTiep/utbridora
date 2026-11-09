package vn.utbridora.auth.modules.cooperation.application;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.SchoolDelegationAccess;
import vn.utbridora.auth.modules.cooperation.port.SchoolSharingPolicyPort;
import vn.utbridora.auth.operations.OperationLedger;
import static vn.utbridora.auth.operations.WorkflowValues.*;
@Service
public class SchoolSharingPolicyService implements SchoolSharingPolicyPort {
    public static final Set<String> FIELDS=Set.of("displayName","studentCode","majorCode","skills","projects","certificates","cv","evidence","email","phone");
    public record Update(Long expectedVersion,List<String> allowedFields) {}
    private final JdbcTemplate jdbc;private final SchoolDelegationAccess scope;private final OperationLedger ledger;private final AuditService audit;
    public SchoolSharingPolicyService(JdbcTemplate jdbc,SchoolDelegationAccess scope,OperationLedger ledger,AuditService audit) {this.jdbc=jdbc;this.scope=scope;this.ledger=ledger;this.audit=audit;}
    @Override @Transactional(propagation=Propagation.MANDATORY) public SchoolSharingPolicy current(String school,String term) {
        // Lock the term even when the policy row is missing; first policy writes serialize with grants.
        if(jdbc.queryForList("SELECT id FROM terms WHERE id=? AND school_org_id=? FOR SHARE",String.class,uuid(term),uuid(school)).isEmpty()) throw failure(404,"RESOURCE_NOT_FOUND");
        return jdbc.query("SELECT * FROM school_sharing_policies WHERE school_org_id=? AND term_id=? FOR SHARE",(r,n)->new SchoolSharingPolicy(school,term,r.getLong("version"),List.of(JSON.readValue(r.getString("allowed_fields"),String[].class)),instant(r,"updated_at"),r.getString("updated_by")),school,term).stream().findFirst().orElse(new SchoolSharingPolicy(school,term,0,List.of("majorCode","skills","projects"),null,null));
    }
    @Transactional public SchoolSharingPolicy get(Authentication auth,String term) {
        String school=school(term);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM student_term_eligibilities e JOIN users u ON u.id=e.user_id JOIN memberships m ON m.user_id=e.user_id AND m.organization_id=e.school_org_id AND m.role='STUDENT' AND m.active=true WHERE e.user_id=? AND e.term_id=? AND e.school_org_id=? AND u.status='ACTIVE' AND u.security_version=?",Integer.class,actor(auth),term,school,((vn.utbridora.auth.security.AppPrincipal)auth.getPrincipal()).securityVersion())==0)
            scope.require(auth,school,term,"student_profile:share:school:manage");
        return current(school,term);
    }
    @Transactional public SchoolSharingPolicy update(Authentication auth,String term,String key,Update input) {
        String school=school(term);scope.require(auth,school,term,"student_profile:share:school:manage");fields(input.allowedFields(),true);
        if(input.expectedVersion()==null || input.expectedVersion()<0) throw failure(400,"SCHEMA_VALIDATION");
        return ledger.execute(new OperationLedger.Command(actor(auth),school,"SCHOOL_SHARING_POLICY_UPDATE",key,Map.of("termId",term,"request",input),200),SchoolSharingPolicy.class,()->{
            jdbc.queryForObject("SELECT id FROM terms WHERE id=? FOR UPDATE",String.class,term);var before=current(school,term);if(before.version()!=input.expectedVersion()) throw failure(409,"VERSION_CONFLICT");
            jdbc.update("INSERT INTO school_sharing_policies VALUES(?,?,?,?,UTC_TIMESTAMP(6),?) AS incoming ON DUPLICATE KEY UPDATE version=incoming.version,allowed_fields=incoming.allowed_fields,updated_at=incoming.updated_at,updated_by=incoming.updated_by",school,term,before.version()+1,JSON.writeValueAsString(input.allowedFields()),actor(auth));
            audit.workflow(actor(auth),"SCHOOL_PROFILE_SHARING_POLICY_CHANGED",term,school,Map.of("schoolOrgId",school,"termId",term,"fieldCodes",input.allowedFields(),"version",before.version()+1));return current(school,term);
        },r->term);
    }
    public static void fields(List<String> fields,boolean emptyAllowed) {
        if(fields==null || fields.stream().anyMatch(Objects::isNull) || fields.size()>12 || !emptyAllowed && fields.isEmpty() || new HashSet<>(fields).size()!=fields.size()) throw failure(400,"SCHEMA_VALIDATION");
        if(!FIELDS.containsAll(fields)) throw failure(422,"PROFILE_FIELD_UNSUPPORTED");
    }
    private String school(String term) {return jdbc.queryForList("SELECT school_org_id FROM terms WHERE id=?",String.class,uuid(term)).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));}
}
