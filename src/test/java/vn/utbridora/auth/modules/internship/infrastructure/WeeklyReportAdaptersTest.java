package vn.utbridora.auth.modules.internship.infrastructure;

import java.util.*;
import org.junit.jupiter.api.*;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.contracts.placement.PlacementReadPort;
import vn.utbridora.auth.modules.internship.domain.*;
import vn.utbridora.auth.user.Role;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeeklyReportAdaptersTest extends WeeklyReportAdapterTestSupport {
    private final CaseRuntime cases = mock(CaseRuntime.class);
    private final PlacementReadPort placements = mock(PlacementReadPort.class);
    private final WeeklyReportCaseAdapter.CaseFence fence = mock(WeeklyReportCaseAdapter.CaseFence.class);
    private final AuthorizationPolicy core = mock(AuthorizationPolicy.class);
    private final WorkspaceAuthority authority = mock(WorkspaceAuthority.class);
    private final WorkspaceRuntime workspace = mock(WorkspaceRuntime.class);
    private final WeeklyReportCaseAdapter caseAdapter = new WeeklyReportCaseAdapter(cases, placements, fence);
    private final WeeklyReportAccessAdapter access = new WeeklyReportAccessAdapter(core, authority, workspace);

    private PlacementReadPort.PlacementView source(String user, String state) {
        return new PlacementReadPort.PlacementView(placement.toString(), user, school.toString(), enterprise.toString(), term.toString(),
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(), state, 8,
                scope.effectiveAt(), scope.startDate(), scope.endDate(), scope.approvedAt(), null, null, "SYNTHETIC");
    }
    @BeforeEach void setup() {
        when(cases.read(caseId)).thenReturn(ref(3));
        when(placements.readCurrentForGuard(placement.toString())).thenReturn(Optional.of(source(student.toString(), "ACTIVE")));
        when(fence.hold(caseId, 3)).thenReturn(ref(3));
    }
    @Test void caseAdapterUsesBothOwnersAndKeepsTheirVersions() {
        var result = inTransaction(() -> caseAdapter.current(caseId));
        assertEquals(scope, result); verify(cases, times(2)).read(caseId);
        verify(placements).readCurrentForGuard(placement.toString()); verifyNoInteractions(fence);
    }
    @Test void sourceCancellationIsReturnedImmediatelyEvenBeforeCaseProjectionChanges() {
        when(placements.readCurrentForGuard(placement.toString())).thenReturn(Optional.of(source(student.toString(), "CANCELLED")));
        assertEquals("CANCELLED", inTransaction(() -> caseAdapter.current(caseId)).placementState());
    }
    @Test void missingSourceAndCrossOwnerScopeMismatchNeverBecomeActiveFixtures() {
        when(placements.readCurrentForGuard(placement.toString())).thenReturn(Optional.empty());
        code(503, "DEPENDENCY_UNAVAILABLE", () -> inTransaction(() -> caseAdapter.current(caseId)));
        when(placements.readCurrentForGuard(placement.toString())).thenReturn(Optional.of(source(UUID.randomUUID().toString(), "ACTIVE")));
        code(404, "RESOURCE_NOT_FOUND", () -> inTransaction(() -> caseAdapter.current(caseId)));
    }
    @Test void observedCaseVersionDriftAndFenceVersionConflictFailClosed() {
        when(cases.read(caseId)).thenReturn(ref(3), ref(4));
        code(409, "VERSION_CONFLICT", () -> inTransaction(() -> caseAdapter.current(caseId)));
        when(fence.hold(caseId, 3)).thenReturn(ref(4));
        code(409, "VERSION_CONFLICT", () -> inTransaction(() -> { caseAdapter.holdForMutation(scope); return true; }));
    }
    @Test void readonlyAdapterWithoutOwnerFenceNeverAuthorizesMutation() {
        var readonly = new WeeklyReportCaseAdapter(cases, placements, null);
        assertNotNull(inTransaction(() -> readonly.current(caseId)));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> inTransaction(() -> { readonly.holdForMutation(scope); return true; }));
    }
    @Test void fenceReceivesOriginalVersionAndCannotSubstituteAnotherCase() {
        inTransaction(() -> { caseAdapter.holdForMutation(scope); return true; }); verify(fence).hold(caseId, 3);
        when(fence.hold(caseId, 3)).thenReturn(new CaseRuntime.CaseRef(UUID.randomUUID(), placement, student, school, enterprise, term, "IN_PROGRESS", 3));
        code(404, "RESOURCE_NOT_FOUND", () -> inTransaction(() -> { caseAdapter.holdForMutation(scope); return true; }));
    }
    @Test void adapterCannotCallTransactionMandatoryOwnersOutsideATransaction() {
        code(503, "DEPENDENCY_UNAVAILABLE", () -> caseAdapter.current(caseId)); verifyNoInteractions(cases, placements);
    }
    @Test void readOnlyCaseSnapshotDoesNotPermitAcquiringAMutationFence() {
        assertNotNull(inReadOnlyTransaction(() -> caseAdapter.current(caseId)));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> inReadOnlyTransaction(() -> { caseAdapter.holdForMutation(scope); return true; }));
        verifyNoInteractions(fence);
    }
    @Test void originalPrincipalSecurityVersionIsRevalidatedAndClaimedNamesAreNotTrusted() {
        var actor = auth(student); when(core.isSelf(actor, student.toString())).thenReturn(true);
        assertEquals(student, inTransaction(() -> access.currentUser(actor)));
        when(core.isSelf(actor, student.toString())).thenReturn(false);
        code(401, "SESSION_EXPIRED", () -> inTransaction(() -> access.currentUser(actor)));
        var forged = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(student.toString(), null, List.of());
        code(401, "SESSION_EXPIRED", () -> inTransaction(() -> access.currentUser(forged)));
    }
    @Test void studentWritePinsExactSchoolRoleAndPermissionAndRejectsForeignOwner() {
        var actor = auth(student); when(core.isSelf(actor, student.toString())).thenReturn(true);
        when(core.holdOrganization(actor, school.toString(), Role.STUDENT, "internship:student:submission_write")).thenReturn(true);
        inTransaction(() -> { access.requirePermission(actor, scope, "internship:student:submission_write", now); return true; });
        verify(core).holdOrganization(actor, school.toString(), Role.STUDENT, "internship:student:submission_write");
        var other = auth(mentor); when(core.isSelf(other, mentor.toString())).thenReturn(true);
        code(404, "RESOURCE_NOT_FOUND", () -> inTransaction(() -> { access.requirePermission(other, scope, "internship:student:submission_write", now); return true; }));
    }
    @Test void readCeilingCannotGrantBusinessReadOrAdminBypass() {
        var actor = auth(mentor); when(core.isSelf(actor, mentor.toString())).thenReturn(true);
        when(core.holdOrganization(actor, enterprise.toString(), Role.ENTERPRISE, "t3:workspace:read")).thenReturn(true);
        inTransaction(() -> { access.requirePermission(actor, scope, "t3:workspace:read", now); return true; });
        when(workspace.caseRef(actor, caseId)).thenThrow(new WorkspaceRuleException(WorkspaceRuleException.Kind.NOT_FOUND));
        code(404, "RESOURCE_NOT_FOUND", () -> inTransaction(() -> access.canRead(actor, scope, now)));
        code(404, "RESOURCE_NOT_FOUND", () -> inTransaction(() -> { access.requirePermission(actor, scope, "admin:any", now); return true; }));
    }
    @Test void ownerWithOnlySchoolStaffPermissionCannotSkipDelegationThroughSelfShortcut() {
        var actor = auth(student); when(core.isSelf(actor, student.toString())).thenReturn(true);
        when(core.holdOrganization(actor, school.toString(), Role.SCHOOL_STAFF, "t3:workspace:read")).thenReturn(true);
        code(404, "RESOURCE_NOT_FOUND", () -> inTransaction(() -> { access.requirePermission(actor, scope, "t3:workspace:read", now); return true; }));
    }
    @Test void mentorGateDelegatesCurrentGroupCaseAndParentChainToExistingAuthority() {
        var actor = auth(mentor); var group = UUID.randomUUID();
        when(core.isSelf(actor, mentor.toString())).thenReturn(true);
        var assignmentScope = new WorkspaceAuthority.Scope(school.toString(), enterprise.toString(), term.toString(), group.toString(), caseId.toString());
        when(authority.selected(actor)).thenReturn(new WorkspaceAuthority.Assignment(UUID.randomUUID().toString(), "MENTOR", mentor.toString(),
                assignmentScope, true, now.minusSeconds(1), now.plusSeconds(1), 2, UUID.randomUUID().toString(), "SYNTHETIC"));
        assertTrue(inTransaction(() -> access.isCurrentMentor(actor, scope, now)));
        verify(authority).requireMentor(actor, new WorkspaceScope(enterprise, school, term), group, caseId);
        doThrow(new WorkspaceRuleException(WorkspaceRuleException.Kind.UNAVAILABLE)).when(authority)
                .requireMentor(actor, new WorkspaceScope(enterprise, school, term), group, caseId);
        code(503, "DEPENDENCY_UNAVAILABLE", () -> inTransaction(() -> access.isCurrentMentor(actor, scope, now)));
    }
    @Test void hrExpiredOrDifferentCaseAssignmentsDoNotBecomeReportReviewers() {
        var actor = auth(mentor); when(core.isSelf(actor, mentor.toString())).thenReturn(true);
        for (String kind : List.of("HR", "COORDINATOR")) {
            when(authority.selected(actor)).thenReturn(new WorkspaceAuthority.Assignment(UUID.randomUUID().toString(), kind, mentor.toString(),
                    new WorkspaceAuthority.Scope(school.toString(), enterprise.toString(), term.toString(), null, null),
                    true, now.minusSeconds(1), now.plusSeconds(1), 1, UUID.randomUUID().toString(), "SYNTHETIC"));
            assertFalse(inTransaction(() -> access.isCurrentMentor(actor, scope, now)));
        }
        when(authority.selected(actor)).thenReturn(new WorkspaceAuthority.Assignment(UUID.randomUUID().toString(), "MENTOR", mentor.toString(),
                new WorkspaceAuthority.Scope(school.toString(), enterprise.toString(), term.toString(), UUID.randomUUID().toString(), null),
                true, now.minusSeconds(1), now, 1, UUID.randomUUID().toString(), "SYNTHETIC"));
        assertFalse(inTransaction(() -> access.isCurrentMentor(actor, scope, now)));
    }
    @Test void readUsesExistingCaseGateAndVerifiesReturnedScopeAndVersion() {
        var actor = auth(mentor); when(core.isSelf(actor, mentor.toString())).thenReturn(true);
        when(workspace.caseRef(actor, caseId)).thenReturn(ref(3));
        assertTrue(inTransaction(() -> access.canRead(actor, scope, now)));
        when(workspace.caseRef(actor, caseId)).thenReturn(ref(4));
        code(409, "VERSION_CONFLICT", () -> inTransaction(() -> access.canRead(actor, scope, now)));
        when(workspace.caseRef(actor, caseId)).thenReturn(new CaseRuntime.CaseRef(caseId, placement, UUID.randomUUID(), school, enterprise, term, "IN_PROGRESS", 3));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> inTransaction(() -> access.canRead(actor, scope, now)));
    }
}
