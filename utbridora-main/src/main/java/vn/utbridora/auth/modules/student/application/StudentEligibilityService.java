package vn.utbridora.auth.modules.student.application;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.modules.student.port.StudentEligibilityPort;
import vn.utbridora.auth.operations.OperationLedger;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@Service
public class StudentEligibilityService implements StudentEligibilityPort {
    public record EligibilityDecision(String studentUserId,String termId,Long expectedVersion,Boolean schoolApproved,String reasonCode,String decisionRef) {}
    public record StudentTermEligibility(String studentUserId,String schoolOrgId,String termId,String studentCode,String majorCode,boolean schoolApproved,long version,String approvedByUserId,Instant approvedAt,String decisionReasonCode) {}
    private final JdbcTemplate jdbc;private final SchoolDelegationAccess scope;private final AuthorizationPolicy core;private final AuditService audit;private final OperationLedger ledger;
    public StudentEligibilityService(JdbcTemplate jdbc,SchoolDelegationAccess scope,AuthorizationPolicy core,AuditService audit,OperationLedger ledger) {this.jdbc=jdbc;this.scope=scope;this.core=core;this.audit=audit;this.ledger=ledger;}
    @Transactional public StudentTermEligibility decide(Authentication auth,String key,EligibilityDecision request) {
        uuid(request.studentUserId());uuid(request.termId());version(request.expectedVersion());reason(request.reasonCode());text(request.decisionRef(),120);
        if(request.schoolApproved()==null) throw failure(400,"SCHEMA_VALIDATION");
        var before=read(request.studentUserId(),request.termId());scope.require(auth,before.schoolOrgId(),before.termId(),"student:eligibility:school:approve");
        return ledger.execute(new OperationLedger.Command(actor(auth),before.schoolOrgId(),"STUDENT_ELIGIBILITY_DECIDE",key,request,200),StudentTermEligibility.class,()->{
            var e=read(request.studentUserId(),request.termId());if(e.version()!=request.expectedVersion()) throw failure(409,"ELIGIBILITY_CHANGED");
            jdbc.update("UPDATE student_term_eligibilities SET school_approved=?,version=version+1,approved_by=?,approved_at=?,reason_code=? WHERE user_id=? AND term_id=?",request.schoolApproved(),request.schoolApproved()?actor(auth):null,request.schoolApproved()?utc(Instant.now()):null,request.reasonCode(),e.studentUserId(),e.termId());
            var result=read(e.studentUserId(),e.termId());audit.workflow(actor(auth),"STUDENT_ELIGIBILITY_CHANGED",jdbc.queryForObject("SELECT id FROM student_term_eligibilities WHERE user_id=? AND term_id=?",String.class,e.studentUserId(),e.termId()),e.schoolOrgId(),Map.of("schoolOrgId",e.schoolOrgId(),"termId",e.termId(),"fromApproved",e.schoolApproved(),"toApproved",result.schoolApproved(),"version",result.version(),"decisionRef",request.decisionRef()));return result;
        },r->null);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY) public void assertEligibleForNomination(Authentication auth,String student,String school,String term) {
        scope.require(auth,school,term,"t5:proposal:manage");var e=read(student,term);
        if(!school.equals(e.schoolOrgId())) throw failure(404,"RESOURCE_NOT_FOUND");
        var subject=core.currentSubject(student).orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
        if(!core.holdOrganization(subject,school,Role.STUDENT,"student_profile:self:read")) throw failure(404,"RESOURCE_NOT_FOUND");
        if(!e.schoolApproved()) throw failure(422,"SCHOOL_APPROVAL_REQUIRED");
        if(!"ACTIVE".equals(jdbc.queryForObject("SELECT status FROM terms WHERE id=? AND school_org_id=? FOR SHARE",String.class,term,school))) throw failure(422,"TERM_INVALID");
    }
    private StudentTermEligibility read(String user,String term) {
        return jdbc.query("SELECT e.*,r.student_code,r.major_code FROM student_term_eligibilities e JOIN student_rosters r ON r.id=e.student_roster_id WHERE e.user_id=? AND e.term_id=? FOR UPDATE",(r,n)->new StudentTermEligibility(r.getString("user_id"),r.getString("school_org_id"),r.getString("term_id"),r.getString("student_code"),r.getString("major_code"),r.getBoolean("school_approved"),r.getLong("version"),r.getString("approved_by"),r.getTimestamp("approved_at")==null?null:instant(r,"approved_at"),r.getString("reason_code")),user,term).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
    }
}
