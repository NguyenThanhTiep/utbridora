package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.contracts.placement.PlacementReadPort;
import vn.utbridora.auth.modules.internship.domain.*;
import vn.utbridora.auth.student.port.TermReferencePort;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.operations.WorkflowValues.*;
import static vn.utbridora.auth.modules.internship.infrastructure.WeeklyReportRuntimeChecks.workspace;

/** Reuses current T1 identity, T3 assignments and T5 source locks. No student grade read here. */
@Component
public class EvaluationAccess {
    private final AuthorizationPolicy core;
    private final WorkspaceAuthority authority;
    private final CaseRuntime cases;
    private final PlacementReadPort placements;
    private final TermReferencePort terms;
    private final SchoolDelegationAccess delegations;
    private final JdbcTemplate jdbc;
    public EvaluationAccess(AuthorizationPolicy core, WorkspaceAuthority authority, CaseRuntime cases,
            PlacementReadPort placements, TermReferencePort terms, SchoolDelegationAccess delegations, JdbcTemplate jdbc) {
        this.core=core; this.authority=authority; this.cases=cases; this.placements=placements;
        this.terms=terms; this.delegations=delegations; this.jdbc=jdbc;
    }
    public UUID user(Authentication auth) {
        String user=actor(auth);
        if (!core.isSelf(auth,user)) throw failure(401,"SESSION_EXPIRED");
        return UUID.fromString(user);
    }
    public CaseRuntime.CaseRef mentor(Authentication auth, UUID caseId) {
        return workspace(() -> mentorScope(auth,caseId));
    }
    private CaseRuntime.CaseRef mentorScope(Authentication auth, UUID caseId) {
        user(auth);
        var c=cases.read(caseId);
        if (!core.holdOrganization(auth,c.enterpriseOrgId().toString(),Role.ENTERPRISE,"internship:enterprise:evaluate"))
            throw failure(404,"RESOURCE_NOT_FOUND");
        var selected=authority.selected(auth);
        if (!"MENTOR".equals(selected.kind()) || selected.scope().groupId()==null) throw failure(403,"ACCESS_DENIED");
        authority.requireMentor(auth,new WorkspaceScope(c.enterpriseOrgId(),c.schoolOrgId(),c.termId()),
                UUID.fromString(selected.scope().groupId()),caseId);
        return c;
    }
    public void holdMutation(Authentication auth, CaseRuntime.CaseRef before, Instant now) {
        // Authoritative Placement -> Case fence, shared with existing independent report workflow.
        var source=placements.readCurrentForGuard(before.placementId().toString()).orElseThrow(() -> failure(503,"DEPENDENCY_UNAVAILABLE"));
        var locked=workspace(() -> cases.holdForReport(before.caseId(),before.version()));
        if (!locked.equals(before)) throw failure(409,"VERSION_CONFLICT");
        if (!InternshipActivityPolicy.hasRequiredStateAndStartTime(locked.state(),source.state(),source.effectiveAt(),now)
                || source.approvedAt()==null || source.approvedAt().isAfter(now)) throw failure(409,"STATE_CONFLICT");
        mentor(auth, before.caseId());
    }
    public UUID templateSchool(Authentication auth, UUID termId) {
        UUID user=user(auth);
        var term=terms.findById(termId.toString()).orElseThrow(() -> failure(404,"RESOURCE_NOT_FOUND"));
        String school=term.schoolOrgId();
        if (core.holdOrganization(auth,school,Role.SCHOOL_STAFF,"t3:workspace:read")) {
            delegations.require(auth,school,termId.toString(),"t3:workspace:read");
        } else if (core.holdOrganization(auth,school,Role.STUDENT,"t3:workspace:read")) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM internship_cases WHERE student_user_id=? AND school_org_id=? AND term_id=?",
                    Long.class,user.toString(),school,termId.toString())==0) throw failure(404,"RESOURCE_NOT_FOUND");
        } else {
            var selected=workspace(() -> authority.selected(auth));
            if (!school.equals(selected.scope().schoolOrgId()) || !termId.toString().equals(selected.scope().termId()))
                throw failure(404,"RESOURCE_NOT_FOUND");
        }
        return UUID.fromString(school);
    }
}
