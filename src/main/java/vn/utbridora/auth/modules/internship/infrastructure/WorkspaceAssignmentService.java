package vn.utbridora.auth.modules.internship.infrastructure;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.modules.cooperation.port.OrganizationSchoolVerificationPort;
import vn.utbridora.auth.operations.OperationLedger;
import vn.utbridora.auth.tokens.MentorScopePort;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.operations.WorkflowValues.*;
import vn.utbridora.auth.modules.internship.infrastructure.WorkspaceAuthority.*;
@Service
public class WorkspaceAssignmentService implements MentorScopePort {
    public record ConfirmCoordinator(String userId,String enterpriseOrgId,String termId,Instant validFrom,Instant validUntil,String decisionRef) {}
    public record GrantHrAssignment(String userId,Scope scope,Instant validFrom,Instant validUntil,String decisionRef) {}
    public record GrantMentorAssignment(String userId,Instant validFrom,Instant validUntil,String decisionRef) {}
    public record Handover(String caseId,String newMentorUserId,Long expectedCaseVersion,String reasonCode) {}
    public record MutationAck(String resourceId,long version,Instant changedAt) {}
    public record Revoke(Long expectedVersion,String reasonCode) {}
    public record RevocationAck(String id,boolean active,long version) {}
    private final JdbcTemplate jdbc;private final WorkspaceAuthority authority;private final AuthorizationPolicy core;private final SchoolDelegationAccess delegations;
    private final OrganizationSchoolVerificationPort verification;private final AccountSecurityPort security;private final AuditService audit;private final OperationLedger ledger;
    public WorkspaceAssignmentService(JdbcTemplate jdbc,WorkspaceAuthority authority,AuthorizationPolicy core,SchoolDelegationAccess delegations,OrganizationSchoolVerificationPort verification,AccountSecurityPort security,AuditService audit,OperationLedger ledger) {this.jdbc=jdbc;this.authority=authority;this.core=core;this.delegations=delegations;this.verification=verification;this.security=security;this.audit=audit;this.ledger=ledger;}
    @Transactional public Assignment confirm(Authentication auth,String key,ConfirmCoordinator request) {
        uuid(request.userId());uuid(request.enterpriseOrgId());String term=uuid(request.termId());period(request.validFrom(),request.validUntil());text(request.decisionRef(),120);
        String school=jdbc.queryForList("SELECT school_org_id FROM terms WHERE id=? AND status='ACTIVE' FOR SHARE",String.class,term).stream().findFirst().orElseThrow(()->failure(422,"TERM_INVALID"));
        delegations.require(auth,school,term,"t3:coordinator:confirm");
        return ledger.execute(new OperationLedger.Command(actor(auth),school,"T3_COORDINATOR_CONFIRM",key,request,201),Assignment.class,()->{
            var context=OrganizationSchoolVerificationPort.ReadContext.fromAuthenticatedWorkflow(auth,term,OrganizationSchoolVerificationPort.Purpose.CONFIRM_COORDINATOR);
            if(!(verification.get(context,request.enterpriseOrgId(),school) instanceof OrganizationSchoolVerificationPort.Success ok) || !ok.value().schoolApproved()) throw failure(422,"SCHOOL_VERIFICATION_REQUIRED");
            String source=jdbc.queryForList("SELECT id FROM school_delegations WHERE user_id=? AND school_org_id=? AND term_id=? ORDER BY id",String.class,actor(auth),school,term).stream()
                .filter(id->delegations.currentAssignment(id,actor(auth),school,term,"t3:coordinator:confirm"))
                .filter(id->jdbc.queryForObject("SELECT COUNT(*) FROM school_delegations WHERE id=? AND valid_from<=? AND valid_until>=?",Integer.class,id,utc(request.validFrom()),utc(request.validUntil()))==1)
                .findFirst().orElseThrow(()->failure(403,"ACCESS_DENIED"));
            return insert(auth,request.userId(),"COORDINATOR",new Scope(school,request.enterpriseOrgId(),term,null,null),null,source,request.validFrom(),request.validUntil(),request.decisionRef(),null);
        },Assignment::id);
    }
    @Transactional public Assignment grantHr(Authentication auth,String key,GrantHrAssignment request) {
        uuid(request.userId());period(request.validFrom(),request.validUntil());text(request.decisionRef(),120);
        var p=authority.selected(auth);var s=request.scope();
        if(s==null || !p.kind().equals("COORDINATOR") || !same(p.scope(),s) || (s.groupId()!=null || s.caseId()!=null) && !authority.validGradeScope(s) || request.validFrom().isBefore(p.validFrom()) || request.validUntil().isAfter(p.validUntil()) || !core.holdOrganization(auth,p.scope().enterpriseOrgId(),Role.ENTERPRISE,"t3:hr:manage")) throw failure(403,"ACCESS_DENIED");
        return ledger.execute(new OperationLedger.Command(actor(auth),s.enterpriseOrgId(),"T3_HR_GRANT",key,request,201),Assignment.class,()->insert(auth,request.userId(),"HR",s,p.id(),null,request.validFrom(),request.validUntil(),request.decisionRef(),null),Assignment::id);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY) public InviteScope validateInvite(Authentication auth,String enterprise,String term,List<String> groups) {
        var hr=authority.selected(auth);
        if(!hr.kind().equals("HR") || !hr.scope().enterpriseOrgId().equals(uuid(enterprise)) || !hr.scope().termId().equals(uuid(term)) || groups==null || groups.isEmpty() || groups.size()>100 || new HashSet<>(groups).size()!=groups.size()
            || !core.holdOrganization(auth,enterprise,Role.ENTERPRISE,"t3:mentor:invite")) throw failure(403,"ACCESS_DENIED");
        groups.forEach(id->group(uuid(id),hr.scope()));
        return new InviteScope(hr.id(),actor(auth),enterprise,hr.scope().schoolOrgId(),term,List.copyOf(groups),hr.validUntil());
    }
    @Transactional public Assignment grantMentor(Authentication auth,String group,String key,GrantMentorAssignment request) {
        uuid(group);uuid(request.userId());period(request.validFrom(),request.validUntil());text(request.decisionRef(),120);var hr=authority.selected(auth);
        if(!hr.kind().equals("HR") || !core.holdOrganization(auth,hr.scope().enterpriseOrgId(),Role.ENTERPRISE,"t3:mentors:manage")) throw failure(403,"ACCESS_DENIED");group(group,hr.scope());
        return ledger.execute(new OperationLedger.Command(actor(auth),hr.scope().enterpriseOrgId(),"T3_MENTOR_GRANT",key,Map.of("groupId",group,"request",request),201),Assignment.class,()->{
            var target=core.currentSubject(request.userId()).orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
            var source=authority.assignments(target).stream().filter(a->a.kind().equals("MENTOR") && group.equals(a.scope().groupId()) && actor(auth).equals(a.grantedBy()) && !request.validFrom().isBefore(a.validFrom()) && !request.validUntil().isAfter(a.validUntil()))
                .filter(a->jdbc.queryForObject("SELECT COUNT(*) FROM internship_context_assignments WHERE id=? AND mentor_invitation_id IS NOT NULL",Integer.class,a.id())==1).findFirst().orElseThrow(()->failure(409,"MENTOR_ACCEPTANCE_REQUIRED"));
            String invite=jdbc.queryForObject("SELECT mentor_invitation_id FROM internship_context_assignments WHERE id=?",String.class,source.id());
            return insert(auth,request.userId(),"MENTOR",new Scope(hr.scope().schoolOrgId(),hr.scope().enterpriseOrgId(),hr.scope().termId(),group,null),hr.id(),null,request.validFrom(),request.validUntil(),request.decisionRef(),invite);
        },Assignment::id);
    }
    @Transactional public MutationAck handover(Authentication auth,String caseId,String key,Handover request) {
        if(!uuid(caseId).equals(uuid(request.caseId()))) throw failure(400,"SCHEMA_VALIDATION");uuid(request.newMentorUserId());version(request.expectedCaseVersion());reason(request.reasonCode());var hr=authority.selected(auth);
        if(!hr.kind().equals("HR") || !core.holdOrganization(auth,hr.scope().enterpriseOrgId(),Role.ENTERPRISE,"t3:mentors:manage")) throw failure(403,"ACCESS_DENIED");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM internship_cases WHERE id=? AND school_org_id=? AND enterprise_org_id=? AND term_id=?",Integer.class,caseId,hr.scope().schoolOrgId(),hr.scope().enterpriseOrgId(),hr.scope().termId())!=1) throw failure(404,"RESOURCE_NOT_FOUND");
        return ledger.execute(new OperationLedger.Command(actor(auth),hr.scope().enterpriseOrgId(),"T3_MENTOR_HANDOVER",key,request,200),MutationAck.class,()->{
            long current=jdbc.queryForObject("SELECT version FROM internship_cases WHERE id=? FOR UPDATE",Long.class,caseId);if(current!=request.expectedCaseVersion()) throw failure(409,"VERSION_CONFLICT");
            String group=jdbc.queryForObject("SELECT group_id FROM internship_cohort_cases WHERE case_id=? FOR UPDATE",String.class,caseId);
            var target=core.currentSubject(request.newMentorUserId()).orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
            var mentor=authority.assignments(target).stream().filter(a->a.kind().equals("MENTOR") && same(a.scope(),hr.scope()) && Objects.equals(a.scope().groupId(),group) && (a.scope().caseId()==null || a.scope().caseId().equals(caseId))).findFirst().orElseThrow(()->failure(422,"MENTOR_ASSIGNMENT_REQUIRED"));
            String effect=UUID.randomUUID().toString();
            var oldGroups=jdbc.queryForList("SELECT DISTINCT t.group_id FROM internship_tasks t JOIN internship_task_assignments a ON a.task_id=t.id WHERE a.case_id=? AND t.group_id<>? ORDER BY t.group_id",String.class,caseId,group);
            for(String old:oldGroups) jdbc.update("INSERT INTO internship_mentor_handovers VALUES(?,?,?,?,?,?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE reason_code=reason_code",UUID.randomUUID().toString(),caseId,old,mentor.id(),hr.id(),actor(auth),request.reasonCode());
            jdbc.update("UPDATE internship_cases SET version=version+1 WHERE id=?",caseId);security.invalidate(List.of(request.newMentorUserId()));
            audit.workflow(actor(auth),"CAPABILITY_GRANTED",effect,hr.scope().schoolOrgId(),Map.of("scopeKind","CASE","scopeId",caseId,"termId",hr.scope().termId(),"assignmentKind","MENTOR","decisionRef",request.reasonCode()));return new MutationAck(effect,current+1,Instant.now());
        },MutationAck::resourceId);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY) public List<String> activateAccepted(String invitation,String subject,InviteScope invited) {
        var hr=core.currentSubject(invited.hrUserId()).orElseThrow(()->failure(403,"ACCESS_DENIED"));
        var parent=authority.assignments(hr).stream().filter(a->a.id().equals(invited.hrAssignmentId()) && a.kind().equals("HR")).findFirst().orElseThrow(()->failure(409,"STATE_CONFLICT"));
        if(!parent.scope().schoolOrgId().equals(invited.schoolOrgId()) || !parent.scope().enterpriseOrgId().equals(invited.enterpriseOrgId()) || !parent.scope().termId().equals(invited.termId())) throw failure(403,"ACCESS_DENIED");
        var end=jdbc.queryForList("SELECT end_date FROM terms WHERE id=? AND status='ACTIVE' FOR SHARE",java.sql.Date.class,invited.termId()).stream().findFirst().orElseThrow(()->failure(422,"TERM_INVALID"));
        Instant now=Instant.now(),limit=end.toLocalDate().plusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();if(parent.validUntil().isBefore(limit)) limit=parent.validUntil();if(!now.isBefore(limit)) throw failure(409,"STATE_CONFLICT");
        var result=new ArrayList<String>();
        for(String group:invited.groupIds()) {group(group,parent.scope());result.add(insert(hr,subject,"MENTOR",new Scope(invited.schoolOrgId(),invited.enterpriseOrgId(),invited.termId(),group,null),parent.id(),null,now,limit,"MENTOR-OWNER-ACCEPTED",invitation).id());}
        return result;
    }
    @Override @Transactional(propagation=Propagation.MANDATORY) public void revokeAccepted(String invitation,InviteScope invited) {
        var ids=jdbc.queryForList("SELECT user_id FROM internship_context_assignments WHERE mentor_invitation_id=? AND active=true",String.class,invitation);security.invalidate(ids);
        for(String id:jdbc.queryForList("SELECT id FROM internship_context_assignments WHERE mentor_invitation_id=? AND active=true FOR UPDATE",String.class,invitation)) {
            jdbc.update("UPDATE internship_context_assignments SET active=false,version=version+1 WHERE id=?",id);audit.workflow(invited.hrUserId(),"CAPABILITY_REVOKED",id,invited.schoolOrgId(),Map.of("scopeKind","GROUP","scopeId",invited.enterpriseOrgId(),"termId",invited.termId(),"assignmentKind","MENTOR","reasonCode","INVITATION_REVOKED"));
        }
    }
    @Transactional public RevocationAck revoke(Authentication auth,String id,String key,Revoke request) {
        uuid(id);version(request.expectedVersion());reason(request.reasonCode());
        var row=jdbc.queryForMap("SELECT * FROM internship_context_assignments WHERE id=? FOR SHARE",id);
        var targetScope=new Scope((String)row.get("school_org_id"),(String)row.get("enterprise_org_id"),(String)row.get("term_id"),(String)row.get("group_id"),(String)row.get("case_id"));
        if(!actor(auth).equals(row.get("granted_by"))) throw failure(404,"RESOURCE_NOT_FOUND");
        if(row.get("kind").equals("COORDINATOR")) delegations.require(auth,targetScope.schoolOrgId(),targetScope.termId(),"t3:coordinator:confirm");
        else {
            var selected=authority.selected(auth);if(!same(selected.scope(),targetScope)) throw failure(404,"RESOURCE_NOT_FOUND");
            String permission=String.valueOf(row.get("kind")).equals("HR")?"t3:hr:manage":"t3:mentors:manage";
            if(!core.holdOrganization(auth,targetScope.enterpriseOrgId(),Role.ENTERPRISE,permission)) throw failure(403,"ACCESS_DENIED");
        }
        return ledger.execute(new OperationLedger.Command(actor(auth),targetScope.enterpriseOrgId(),"T3_ASSIGNMENT_REVOKE",key,Map.of("id",id,"request",request),200),RevocationAck.class,()->{
            var users=jdbc.queryForList("WITH RECURSIVE d AS (SELECT id,user_id FROM internship_context_assignments WHERE id=? UNION ALL SELECT x.id,x.user_id FROM internship_context_assignments x JOIN d ON x.parent_assignment_id=d.id) SELECT DISTINCT user_id FROM d",String.class,id);security.invalidate(users);
            if(jdbc.update("UPDATE internship_context_assignments SET active=false,version=version+1 WHERE id=? AND version=? AND active=true",id,request.expectedVersion())!=1) throw failure(409,"VERSION_CONFLICT");
            audit.workflow(actor(auth),"CAPABILITY_REVOKED",id,targetScope.schoolOrgId(),Map.of("scopeKind","ENTERPRISE","scopeId",targetScope.enterpriseOrgId(),"termId",targetScope.termId(),"assignmentKind",row.get("kind"),"reasonCode",request.reasonCode()));return new RevocationAck(id,false,request.expectedVersion()+1);
        },RevocationAck::id);
    }
    private Assignment insert(Authentication auth,String subject,String kind,Scope scope,String parent,String delegation,Instant from,Instant until,String decision,String invitation) {
        if(actor(auth).equals(subject)) throw failure(403,"ACCESS_DENIED");
        var target=core.currentSubject(subject).orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));if(!core.holdOrganization(target,scope.enterpriseOrgId(),Role.ENTERPRISE,"t3:workspace:read")) throw failure(403,"ACCESS_DENIED");
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO internship_context_assignments(id,kind,user_id,enterprise_org_id,school_org_id,term_id,group_id,case_id,parent_assignment_id,school_delegation_id,granted_by,decision_ref,invitation_accepted,valid_from,valid_until,mentor_invitation_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,kind,subject,scope.enterpriseOrgId(),scope.schoolOrgId(),scope.termId(),scope.groupId(),scope.caseId(),parent,delegation,actor(auth),decision,kind.equals("MENTOR"),utc(from),utc(until),invitation);
        security.invalidate(List.of(subject));audit.workflow(actor(auth),"CAPABILITY_GRANTED",id,scope.schoolOrgId(),Map.of("scopeKind",scope.groupId()==null?"ENTERPRISE":"GROUP","scopeId",scope.groupId()==null?scope.enterpriseOrgId():scope.groupId(),"termId",scope.termId(),"assignmentKind",kind,"validFrom",from.toString(),"validUntil",until.toString(),"decisionRef",decision));
        return new Assignment(id,kind,subject,scope,true,from,until,1,actor(auth),decision);
    }
    private void group(String group,Scope s) {if(jdbc.queryForObject("SELECT COUNT(*) FROM internship_groups g JOIN internship_cohorts c ON c.id=g.cohort_id WHERE g.id=? AND c.enterprise_org_id=? AND c.school_org_id=? AND c.term_id=?",Integer.class,group,s.enterpriseOrgId(),s.schoolOrgId(),s.termId())!=1) throw failure(404,"RESOURCE_NOT_FOUND");}
    private static boolean same(Scope a,Scope b) {return a.enterpriseOrgId().equals(b.enterpriseOrgId()) && a.schoolOrgId().equals(b.schoolOrgId()) && a.termId().equals(b.termId());}
}
