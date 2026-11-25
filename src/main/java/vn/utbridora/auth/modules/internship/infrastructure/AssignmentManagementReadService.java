package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.*;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page;
import vn.utbridora.auth.modules.cooperation.port.OrganizationSchoolVerificationPort;
import vn.utbridora.auth.student.port.TermReferencePort;
import vn.utbridora.auth.user.Role;
import vn.utbridora.auth.modules.internship.infrastructure.WorkspaceAuthority.*;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** Read projections for the existing CP-AUTHZ1 commands. No new capability or role is granted here. */
@Service @Transactional(readOnly=true)
public class AssignmentManagementReadService {
    public record ManagementScope(String id,String mode,String schoolOrgId,String enterpriseOrgId,String termId,String termCode,Instant validFrom,Instant validUntil,String contextId) {}
    public record ManagedAssignment(Assignment assignment,String displayName,boolean effective,boolean canRevoke) {}
    public record EnterpriseOption(String enterpriseOrgId,String name,boolean canConfirm) {}
    public record CaseOption(String caseId,String groupId,String groupCode,String state,long version) {}
    public record GroupOption(String groupId,String code,String cohortCode) {}
    private record Access(String mode,Scope scope,String actor) {}
    private final JdbcTemplate jdbc;
    private final WorkspaceAuthority authority;
    private final AuthorizationPolicy core;
    private final SchoolDelegationAccess delegations;
    private final TermReferencePort terms;
    private final OrganizationSchoolVerificationPort verification;
    private final EnterpriseMemberReferencePort members;
    public AssignmentManagementReadService(JdbcTemplate jdbc,WorkspaceAuthority authority,AuthorizationPolicy core,SchoolDelegationAccess delegations,TermReferencePort terms,OrganizationSchoolVerificationPort verification,EnterpriseMemberReferencePort members) {
        this.jdbc=jdbc;this.authority=authority;this.core=core;this.delegations=delegations;this.terms=terms;this.verification=verification;this.members=members;
    }
    public Page<ManagementScope> scopes(Authentication auth,int page,int size) {
        page(page,size);String user=actor(auth);if(!core.isSelf(auth,user)) throw failure(401,"SESSION_EXPIRED");
        var rows=new ArrayList<ManagementScope>();
        for(var s:delegations.confirmationScopes(auth)) terms.findById(s.termId()).ifPresent(t->rows.add(new ManagementScope(s.delegationId(),"SCHOOL",s.schoolOrgId(),null,s.termId(),t.termCode(),s.validFrom(),s.validUntil(),null)));
        for(var a:authority.assignments(auth)) {
            if(a.scope().groupId()!=null || a.scope().caseId()!=null) continue;
            String permission=a.kind().equals("COORDINATOR")?"t3:hr:manage":a.kind().equals("HR")?"t3:mentors:manage":null;
            if(permission==null || !core.hasOrganization(auth,a.scope().enterpriseOrgId(),Role.ENTERPRISE,permission)) continue;
            terms.findById(a.scope().termId()).filter(t->t.status()==TermReferencePort.Status.ACTIVE).ifPresent(t->rows.add(new ManagementScope(a.id(),a.kind(),a.scope().schoolOrgId(),a.scope().enterpriseOrgId(),a.scope().termId(),t.termCode(),a.validFrom(),a.validUntil(),a.id())));
        }
        rows.sort(Comparator.comparing(ManagementScope::mode).thenComparing(ManagementScope::schoolOrgId).thenComparing(ManagementScope::termId).thenComparing(ManagementScope::id));
        return slice(rows,page,size);
    }
    public Page<EnterpriseOption> enterprises(Authentication auth,String school,String term,int page,int size) {
        page(page,size);school=uuid(school);term=uuid(term);requireTerm(school,term);
        delegations.require(auth,school,term,"t3:coordinator:confirm");
        var approved=verification.coordinatorEnterprises(context(auth,term),school);
        var ids=new TreeSet<String>();approved.forEach(e->ids.add(e.enterpriseOrgId()));
        // A lapsed verification must block new confirmation, not hide the grantor's existing revocations.
        ids.addAll(jdbc.queryForList("SELECT DISTINCT enterprise_org_id FROM internship_context_assignments WHERE kind='COORDINATOR' AND school_org_id=? AND term_id=? AND granted_by=?",String.class,school,term,actor(auth)));
        var selected=slice(new ArrayList<>(ids),page,size);var names=members.organizationNames(selected.content());
        var allowed=approved.stream().map(OrganizationSchoolVerificationPort.EnterpriseReference::enterpriseOrgId).collect(java.util.stream.Collectors.toSet());
        return new Page<>(selected.content().stream().map(id->new EnterpriseOption(id,names.getOrDefault(id,"Doanh nghiệp …"+id.substring(30)),allowed.contains(id))).toList(),page,size,selected.totalElements(),selected.totalPages());
    }
    private Access access(Authentication auth,String school,String enterprise,String term) {
        school=uuid(school);enterprise=uuid(enterprise);term=uuid(term);requireTerm(school,term);
        var attributes=RequestContextHolder.getRequestAttributes();
        boolean selected=attributes instanceof ServletRequestAttributes web && web.getRequest().getHeader("X-Context-Assignment-Id")!=null;
        if(selected) {
            var a=authority.selected(auth);
            if(!a.scope().schoolOrgId().equals(school) || !a.scope().enterpriseOrgId().equals(enterprise) || !a.scope().termId().equals(term)) throw failure(404,"RESOURCE_NOT_FOUND");
            String permission=a.kind().equals("COORDINATOR")?"t3:hr:manage":a.kind().equals("HR")?"t3:mentors:manage":null;
            if(permission==null || !core.hasOrganization(auth,enterprise,Role.ENTERPRISE,permission)) throw failure(404,"RESOURCE_NOT_FOUND");
            return new Access(a.kind(),a.scope(),actor(auth));
        }
        delegations.require(auth,school,term,"t3:coordinator:confirm");
        return new Access("SCHOOL",new Scope(school,enterprise,term,null,null),actor(auth));
    }
    private void requireTerm(String school,String term) {
        var t=terms.findById(term).orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
        if(!t.schoolOrgId().equals(school) || t.status()!=TermReferencePort.Status.ACTIVE) throw failure(404,"RESOURCE_NOT_FOUND");
    }
    private static OrganizationSchoolVerificationPort.ReadContext context(Authentication auth,String term) {
        return OrganizationSchoolVerificationPort.ReadContext.fromAuthenticatedWorkflow(auth,term,OrganizationSchoolVerificationPort.Purpose.CONFIRM_COORDINATOR);
    }
    public Page<EnterpriseMemberReferencePort.Member> candidates(Authentication auth,String school,String enterprise,String term,String search,int page,int size) {
        page(page,size);var a=access(auth,school,enterprise,term);
        if(a.mode().equals("HR")) throw failure(404,"RESOURCE_NOT_FOUND");
        if(a.mode().equals("SCHOOL") && (!(verification.get(context(auth,term),enterprise,school) instanceof OrganizationSchoolVerificationPort.Success ok) || !ok.value().schoolApproved())) throw failure(404,"RESOURCE_NOT_FOUND");
        return members.candidates(a.scope().enterpriseOrgId(),a.actor(),search,page,size);
    }
    public Page<ManagedAssignment> assignments(Authentication auth,String school,String enterprise,String term,boolean includeInactive,int page,int size) {
        page(page,size);var a=access(auth,school,enterprise,term);String kind=childKind(a);
        String where=" WHERE school_org_id=? AND enterprise_org_id=? AND term_id=? AND granted_by=? AND kind=?"+(includeInactive?"":" AND active=true");
        Object[] args={school,enterprise,term,a.actor(),kind};
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM internship_context_assignments"+where,Long.class,args);
        var params=new ArrayList<Object>(List.of(args));params.add(size);params.add((long)page*size);
        var rows=jdbc.query("SELECT * FROM internship_context_assignments"+where+" ORDER BY valid_from DESC,id LIMIT ? OFFSET ?",AssignmentManagementReadService::assignment,params.toArray());
        var names=members.names(enterprise,rows.stream().map(Assignment::userId).toList());
        return new Page<>(rows.stream().map(r->view(a,r,names)).toList(),page,size,total,(int)((total+size-1)/size));
    }
    public ManagedAssignment detail(Authentication auth,String school,String enterprise,String term,String id) {
        uuid(id);var a=access(auth,school,enterprise,term);
        var row=jdbc.query("SELECT * FROM internship_context_assignments WHERE id=? AND school_org_id=? AND enterprise_org_id=? AND term_id=? AND granted_by=? AND kind=?",AssignmentManagementReadService::assignment,id,school,enterprise,term,a.actor(),childKind(a))
            .stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
        return view(a,row,members.names(enterprise,List.of(row.userId())));
    }
    private ManagedAssignment view(Access access,Assignment a,Map<String,String> names) {
        return new ManagedAssignment(a,names.getOrDefault(a.userId(),"Tài khoản …"+a.userId().substring(30)),authority.effectiveAssignment(a.id()),a.active() && access.actor().equals(a.grantedBy()));
    }
    private static String childKind(Access a) {return switch(a.mode()){case "SCHOOL"->"COORDINATOR";case "COORDINATOR"->"HR";default->"MENTOR";};}
    private Access hr(Authentication auth,String school,String enterprise,String term) {
        var a=access(auth,school,enterprise,term);if(!a.mode().equals("HR")) throw failure(404,"RESOURCE_NOT_FOUND");return a;
    }
    public Page<GroupOption> groups(Authentication auth,String school,String enterprise,String term,int page,int size) {
        page(page,size);var a=access(auth,school,enterprise,term);if(a.mode().equals("SCHOOL")) throw failure(404,"RESOURCE_NOT_FOUND");
        String from=" FROM internship_groups g JOIN internship_cohorts c ON c.id=g.cohort_id WHERE c.school_org_id=? AND c.enterprise_org_id=? AND c.term_id=?";
        long total=jdbc.queryForObject("SELECT COUNT(*)"+from,Long.class,school,enterprise,term);
        var rows=jdbc.query("SELECT g.id,g.code,c.code"+from+" ORDER BY c.code,g.code,g.id LIMIT ? OFFSET ?",(r,n)->new GroupOption(r.getString(1),r.getString(2),r.getString(3)),school,enterprise,term,size,(long)page*size);
        return new Page<>(rows,page,size,total,(int)((total+size-1)/size));
    }
    public Page<CaseOption> cases(Authentication auth,String school,String enterprise,String term,String groupId,int page,int size) {
        page(page,size);var a=access(auth,school,enterprise,term);if(a.mode().equals("SCHOOL")) throw failure(404,"RESOURCE_NOT_FOUND");
        if(groupId!=null) uuid(groupId);
        String from=" FROM internship_cases c JOIN internship_cohort_cases cc ON cc.case_id=c.id JOIN internship_groups g ON g.id=cc.group_id WHERE c.school_org_id=? AND c.enterprise_org_id=? AND c.term_id=? AND (? IS NULL OR cc.group_id=?) AND c.state NOT IN ('COMPLETED','TERMINATED')";
        long total=jdbc.queryForObject("SELECT COUNT(*)"+from,Long.class,school,enterprise,term,groupId,groupId);
        var rows=jdbc.query("SELECT c.id,cc.group_id,g.code,c.state,c.version"+from+" ORDER BY c.id LIMIT ? OFFSET ?",(r,n)->new CaseOption(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getLong(5)),school,enterprise,term,groupId,groupId,size,(long)page*size);
        return new Page<>(rows,page,size,total,(int)((total+size-1)/size));
    }
    public Page<EnterpriseMemberReferencePort.Member> handoverCandidates(Authentication auth,String school,String enterprise,String term,String caseId,int page,int size) {
        page(page,size);uuid(caseId);hr(auth,school,enterprise,term);
        String group=jdbc.queryForList("SELECT cc.group_id FROM internship_cohort_cases cc JOIN internship_cases c ON c.id=cc.case_id WHERE c.id=? AND c.school_org_id=? AND c.enterprise_org_id=? AND c.term_id=? AND cc.group_id IS NOT NULL AND c.state NOT IN ('COMPLETED','TERMINATED')",String.class,caseId,school,enterprise,term)
            .stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
        var ids=jdbc.queryForList("SELECT id FROM internship_context_assignments WHERE kind='MENTOR' AND school_org_id=? AND enterprise_org_id=? AND term_id=? AND group_id=? AND (case_id IS NULL OR case_id=?) ORDER BY user_id,id",String.class,school,enterprise,term,group,caseId);
        var users=new LinkedHashSet<String>();
        for(String id:ids) if(authority.effectiveAssignment(id)) users.add(jdbc.queryForObject("SELECT user_id FROM internship_context_assignments WHERE id=?",String.class,id));
        var selected=slice(new ArrayList<>(users),page,size);var names=members.names(enterprise,selected.content());
        return new Page<>(selected.content().stream().map(id->new EnterpriseMemberReferencePort.Member(id,names.getOrDefault(id,"Mentor …"+id.substring(30)))).toList(),page,size,selected.totalElements(),selected.totalPages());
    }
    private static Assignment assignment(java.sql.ResultSet r,int n) throws java.sql.SQLException {
        return new Assignment(r.getString("id"),r.getString("kind"),r.getString("user_id"),new Scope(r.getString("school_org_id"),r.getString("enterprise_org_id"),r.getString("term_id"),r.getString("group_id"),r.getString("case_id")),r.getBoolean("active"),instant(r,"valid_from"),instant(r,"valid_until"),r.getLong("version"),r.getString("granted_by"),r.getString("decision_ref"));
    }
    private static <T> Page<T> slice(List<T> rows,int page,int size) {
        int from=Math.min(page*size,rows.size());return new Page<>(List.copyOf(rows.subList(from,Math.min(from+size,rows.size()))),page,size,rows.size(),(rows.size()+size-1)/size);
    }
}
