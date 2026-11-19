package vn.utbridora.auth.modules.internship.infrastructure;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.student.port.TermReferencePort;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** Current school delegation or exact HR/Mentor chain; raw IDs never confer authority. */
@Component
public class OperationsAccess {
    public record Scope(String school,String enterprise,String term,String group,String caseId,String student) {}
    private final AuthorizationPolicy core;private final SchoolDelegationAccess delegates;private final WorkspaceAuthority authority;
    private final CaseRuntime cases;private final TermReferencePort terms;private final JdbcTemplate jdbc;
    public OperationsAccess(AuthorizationPolicy core,SchoolDelegationAccess delegates,WorkspaceAuthority authority,CaseRuntime cases,TermReferencePort terms,JdbcTemplate jdbc) {
        this.core=core;this.delegates=delegates;this.authority=authority;this.cases=cases;this.terms=terms;this.jdbc=jdbc;
    }
    public String school(Authentication auth,UUID term,String permission) {
        actor(auth);var t=terms.findById(term.toString()).orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
        if(!core.holdOrganization(auth,t.schoolOrgId(),Role.SCHOOL_STAFF,permission)) throw failure(404,"RESOURCE_NOT_FOUND");
        delegates.require(auth,t.schoolOrgId(),term.toString(),permission);return t.schoolOrgId();
    }
    public Scope scope(Authentication auth,UUID term,String kind) {
        String user=actor(auth);var t=terms.findById(term.toString()).orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
        if(kind.equals("school")) return new Scope(school(auth,term,"t3:workspace:read"),null,term.toString(),null,null,null);
        var attributes=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        boolean selected=attributes instanceof org.springframework.web.context.request.ServletRequestAttributes web && web.getRequest().getHeader("X-Context-Assignment-Id")!=null;
        if(kind.equals("calendar") && !selected) {
            if(core.hasOrganization(auth,t.schoolOrgId(),Role.SCHOOL_STAFF,"t3:workspace:read")) try {return scope(auth,term,"school");}
            catch(vn.utbridora.auth.operations.OperationFailure denied) {if(denied.status>=500 || denied.status==401) throw denied;}
            if(core.holdOrganization(auth,t.schoolOrgId(),Role.STUDENT,"t3:workspace:read")) return new Scope(t.schoolOrgId(),null,term.toString(),null,null,user);
        }
        var a=authority.selectedGradeHr(auth);
        if(!Set.of("HR","MENTOR").contains(a.kind()) || !kind.equals("calendar") && !a.kind().equalsIgnoreCase(kind)
            || !a.scope().termId().equals(term.toString()) || !a.scope().schoolOrgId().equals(t.schoolOrgId())) throw failure(404,"RESOURCE_NOT_FOUND");
        return new Scope(t.schoolOrgId(),a.scope().enterpriseOrgId(),term.toString(),a.scope().groupId(),a.scope().caseId(),null);
    }
    public List<String> caseIds(Scope s) {
        return jdbc.queryForList("SELECT c.id FROM internship_cases c WHERE c.school_org_id=? AND c.term_id=? AND (? IS NULL OR c.enterprise_org_id=?) AND (? IS NULL OR c.student_user_id=?) AND (? IS NULL OR c.id=?) AND (? IS NULL OR EXISTS(SELECT 1 FROM internship_cohort_cases b WHERE b.case_id=c.id AND b.group_id=?)) ORDER BY c.id",String.class,
            s.school(),s.term(),s.enterprise(),s.enterprise(),s.student(),s.student(),s.caseId(),s.caseId(),s.group(),s.group());
    }
    public CaseRuntime.CaseRef readCase(Authentication auth,UUID id) {
        var c=cases.read(id);String user=actor(auth);
        if(core.hasOrganization(auth,c.schoolOrgId().toString(),Role.SCHOOL_STAFF,"t3:workspace:read")) {
            school(auth,c.termId(),"t3:workspace:read");return c;
        }
        var a=authority.selectedGradeHr(auth);
        if(!Set.of("HR","MENTOR").contains(a.kind()) || !same(c,a.scope()) || a.scope().caseId()!=null && !id.toString().equals(a.scope().caseId())
            || a.scope().groupId()!=null && jdbc.queryForObject("SELECT COUNT(*) FROM internship_cohort_cases WHERE case_id=? AND group_id=?",Integer.class,id.toString(),a.scope().groupId())!=1) throw failure(404,"RESOURCE_NOT_FOUND");
        return c;
    }
    public void schoolCase(Authentication auth,CaseRuntime.CaseRef c,String permission) {
        if(!school(auth,c.termId(),permission).equals(c.schoolOrgId().toString())) throw failure(404,"RESOURCE_NOT_FOUND");
    }
    public void hrCase(Authentication auth,CaseRuntime.CaseRef c,String permission) {
        if(!core.holdOrganization(auth,c.enterpriseOrgId().toString(),Role.ENTERPRISE,permission)) throw failure(404,"RESOURCE_NOT_FOUND");
        var a=authority.selectedGradeHr(auth);
        if(!a.kind().equals("HR") || !same(c,a.scope()) || a.scope().caseId()!=null && !a.scope().caseId().equals(c.caseId().toString())
            || a.scope().groupId()!=null && jdbc.queryForObject("SELECT COUNT(*) FROM internship_cohort_cases WHERE case_id=? AND group_id=?",Integer.class,c.caseId().toString(),a.scope().groupId())!=1) throw failure(404,"RESOURCE_NOT_FOUND");
    }
    public void coordinationWriter(Authentication auth,CaseRuntime.CaseRef c) {
        if(core.hasOrganization(auth,c.schoolOrgId().toString(),Role.SCHOOL_STAFF,"t3:coordination:manage")) schoolCase(auth,c,"t3:coordination:manage");
        else hrCase(auth,c,"t3:coordination:manage");
    }
    public boolean owner(UUID user,CaseRuntime.CaseRef c) {
        var auth=core.currentSubject(user.toString()).orElse(null);if(auth==null) return false;
        if(core.hasOrganization(auth,c.schoolOrgId().toString(),Role.SCHOOL_STAFF,"t3:workspace:read")) {
            try {schoolCase(auth,c,"t3:workspace:read");return true;} catch(vn.utbridora.auth.operations.OperationFailure denied) {if(denied.status>=500 || denied.status==401)throw denied;return false;}
        }
        return authority.assignments(auth).stream().anyMatch(a->Set.of("HR","MENTOR").contains(a.kind()) && same(c,a.scope())
            && (a.scope().caseId()==null || c.caseId().toString().equals(a.scope().caseId()))
            && (a.scope().groupId()==null || jdbc.queryForObject("SELECT COUNT(*) FROM internship_cohort_cases WHERE case_id=? AND group_id=?",Integer.class,c.caseId().toString(),a.scope().groupId())==1));
    }
    private static boolean same(CaseRuntime.CaseRef c,WorkspaceAuthority.Scope s) {return c.schoolOrgId().toString().equals(s.schoolOrgId()) && c.enterpriseOrgId().toString().equals(s.enterpriseOrgId()) && c.termId().toString().equals(s.termId());}
}
