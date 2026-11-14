package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.contracts.placement.PlacementReadPort;
import vn.utbridora.auth.modules.internship.domain.InternshipActivityPolicy;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.operations.WorkflowValues.*;
import static vn.utbridora.auth.modules.internship.infrastructure.WeeklyReportRuntimeChecks.workspace;

@Component
public class GradeAccess {
    public static final String PUBLISH="t3:grades:publish", SCHOOL="t3:result_policy:manage", HR="t3:grade_visibility:manage";
    private final AuthorizationPolicy core; private final SchoolDelegationAccess delegations;
    private final WorkspaceAuthority authority; private final CaseRuntime cases;
    private final PlacementReadPort placements; private final JdbcTemplate jdbc;
    public GradeAccess(AuthorizationPolicy core,SchoolDelegationAccess delegations,WorkspaceAuthority authority,CaseRuntime cases,PlacementReadPort placements,JdbcTemplate jdbc){
        this.core=core;this.delegations=delegations;this.authority=authority;this.cases=cases;this.placements=placements;this.jdbc=jdbc;
    }
    public CaseRuntime.CaseRef caseRef(UUID id){return workspace(()->cases.read(id));}
    public boolean schoolReader(Authentication auth,UUID school){return core.hasOrganization(auth,school.toString(),Role.SCHOOL_STAFF,PUBLISH);}
    public void school(Authentication auth,UUID school,UUID term,String permission){
        actor(auth);
        if(!core.holdOrganization(auth,school.toString(),Role.SCHOOL_STAFF,permission)) throw failure(404,"RESOURCE_NOT_FOUND");
        delegations.require(auth,school.toString(),term.toString(),permission);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM terms WHERE id=? AND school_org_id=?",Long.class,term.toString(),school.toString())!=1) throw failure(404,"RESOURCE_NOT_FOUND");
    }
    public CaseRuntime.CaseRef schoolCase(Authentication auth,UUID id){var c=caseRef(id);school(auth,c.schoolOrgId(),c.termId(),PUBLISH);return c;}
    public CaseRuntime.CaseRef student(Authentication auth,UUID id){
        var c=caseRef(id);
        if(!actor(auth).equals(c.studentUserId().toString()) || !core.holdOrganization(auth,c.schoolOrgId().toString(),Role.STUDENT,"t3:workspace:read")) throw failure(404,"RESOURCE_NOT_FOUND");
        return c;
    }
    public void hr(Authentication auth,UUID enterprise,UUID term,UUID caseId){
        if(!core.holdOrganization(auth,enterprise.toString(),Role.ENTERPRISE,HR)) throw failure(404,"RESOURCE_NOT_FOUND");
        var a=workspace(()->authority.selectedGradeHr(auth));
        if(!"HR".equals(a.kind())) throw failure(403,"ACCESS_DENIED");
        if(!enterprise.toString().equals(a.scope().enterpriseOrgId()) || !term.toString().equals(a.scope().termId())) throw failure(404,"RESOURCE_NOT_FOUND");
        if(caseId==null){if(a.scope().groupId()!=null || a.scope().caseId()!=null) throw failure(403,"ACCESS_DENIED");return;}
        var c=caseRef(caseId);
        if(!c.enterpriseOrgId().equals(enterprise) || !c.termId().equals(term) || !c.schoolOrgId().toString().equals(a.scope().schoolOrgId())
                || a.scope().caseId()!=null && !caseId.toString().equals(a.scope().caseId())) throw failure(404,"RESOURCE_NOT_FOUND");
        if(a.scope().groupId()!=null && jdbc.queryForList("SELECT case_id FROM internship_cohort_cases WHERE case_id=? AND group_id=? FOR SHARE",String.class,caseId.toString(),a.scope().groupId()).isEmpty()) throw failure(404,"RESOURCE_NOT_FOUND");
    }
    /** Term row serializes missing-policy creation and prevents fractured triple-gate reads. */
    public void holdTerm(UUID term,boolean write){
        if(jdbc.queryForList("SELECT id FROM terms WHERE id=?"+(write?" FOR UPDATE":" FOR SHARE"),String.class,term.toString()).isEmpty()) throw failure(404,"RESOURCE_NOT_FOUND");
    }
    public void holdSchoolMutation(Authentication auth,CaseRuntime.CaseRef c){
        var p=placements.readCurrentForGuard(c.placementId().toString()).orElseThrow(()->failure(503,"DEPENDENCY_UNAVAILABLE"));
        var locked=workspace(()->cases.holdForReport(c.caseId(),c.version()));
        if(!locked.equals(c)) throw failure(409,"VERSION_CONFLICT");
        var now=Instant.now();
        if(!InternshipActivityPolicy.hasRequiredStateAndStartTime(c.state(),p.state(),p.effectiveAt(),now) || p.approvedAt()==null || p.approvedAt().isAfter(now)) throw failure(409,"STATE_CONFLICT");
        school(auth,c.schoolOrgId(),c.termId(),PUBLISH);
    }
}
