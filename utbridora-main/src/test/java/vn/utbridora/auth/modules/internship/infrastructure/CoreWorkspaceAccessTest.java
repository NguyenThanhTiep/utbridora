package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.modules.internship.api.TaskDtos.*;
import vn.utbridora.auth.modules.internship.domain.*;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.modules.internship.infrastructure.CoreWorkspaceAccess.*;
import static vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException.Kind.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests adapter composition. Mock business authority is not a runtime assignment provider. */
class CoreWorkspaceAccessTest {
    private final AuthorizationPolicy core = mock(AuthorizationPolicy.class);
    private final BusinessAuthority authority = mock(BusinessAuthority.class);
    private final CoreWorkspaceAccess access = new CoreWorkspaceAccess(core, authority);
    private final Authentication actor = UsernamePasswordAuthenticationToken.authenticated("user", null, List.of());
    private final WorkspaceScope scope = new WorkspaceScope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    private final Cohort cohort = new Cohort(UUID.randomUUID(), scope, "C", "Name", 1);
    private final UUID caseId = UUID.randomUUID(), student = UUID.randomUUID();
    private final InternshipGroup group = new InternshipGroup(UUID.randomUUID(), cohort.id(), "G", List.of(caseId), List.of(), 1);
    private InternshipTask task() {
        return new InternshipTask(new TaskDto(UUID.randomUUID(), group.id(), "T", "D", Instant.EPOCH,
            TaskPriority.NORMAL, true, 1L, List.of(new AssignmentDto(UUID.randomUUID(), caseId, student,
                AssignmentState.NEW, 1L, null))));
    }
    private void coreAllows(String permission) {
        when(core.hasOrganization(actor, scope.enterpriseOrgId().toString(), Role.ENTERPRISE, permission)).thenReturn(true);
    }
    @Test void creationCombinesSelectedTermScopeCorePermissionAndHrAssignment() {
        when(authority.resolveCreationScope(actor, scope.termId())).thenReturn(scope);
        coreAllows("t3:cohorts:manage");
        assertEquals(scope, access.cohortCreationScope(actor, scope.termId()));
        verify(authority).requireHr(actor, scope, null);
    }
    @Test void missingPermissionDeniesEvenWhenActorHasBusinessAssignment() {
        assertEquals(DENIED, assertThrows(WorkspaceRuleException.class,
            () -> access.requireManageGroup(actor, cohort, group)).kind());
        verifyNoInteractions(authority);
    }
    @Test void corePermissionAloneCannotBypassMissingOrWrongHrAssignment() {
        coreAllows("t3:cohorts:manage");
        doThrow(new WorkspaceRuleException(DENIED)).when(authority).requireHr(actor, scope, group.id());
        assertThrows(WorkspaceRuleException.class, () -> access.requireManageGroup(actor, cohort, group));
    }
    @Test void taskWriteRequiresMentorForExactCaseAndGroup() {
        coreAllows("internship:enterprise:task_write");
        access.requireCreateTask(actor, cohort, group, caseId);
        verify(authority).requireMentor(actor, scope, group.id(), caseId);
    }
    @Test void wrongMentorAssignmentDeniesDespiteSeededPermission() {
        coreAllows("internship:enterprise:task_write");
        doThrow(new WorkspaceRuleException(DENIED)).when(authority).requireMentor(actor, scope, group.id(), caseId);
        assertThrows(WorkspaceRuleException.class, () -> access.requireCreateTask(actor, cohort, group, caseId));
    }
    @Test void wrongCohortBindingIsRejectedBeforeCoreOrAuthority() {
        var foreign = new InternshipGroup(group.id(), UUID.randomUUID(), "G", List.of(caseId), List.of(), 1);
        assertEquals(SCOPE_MISMATCH, assertThrows(WorkspaceRuleException.class,
            () -> access.requireManageGroup(actor, cohort, foreign)).kind());
        verifyNoInteractions(core, authority);
    }
    @Test void scopeResolverCannotSubstituteAnotherTerm() {
        when(authority.resolveCreationScope(actor, scope.termId())).thenReturn(
            new WorkspaceScope(scope.enterpriseOrgId(), scope.schoolOrgId(), UUID.randomUUID()));
        assertEquals(SCOPE_MISMATCH, assertThrows(WorkspaceRuleException.class,
            () -> access.cohortCreationScope(actor, scope.termId())).kind());
        verifyNoInteractions(core);
    }
    @Test void missingAuthorityFailsClosedInsteadOfGrantingEnterpriseRole() {
        var safe = new CoreWorkspaceAccess(core, unavailableAuthority());
        assertEquals(UNAVAILABLE, assertThrows(WorkspaceRuleException.class,
            () -> safe.cohortCreationScope(actor, scope.termId())).kind());
        coreAllows("internship:enterprise:task_write");
        assertEquals(UNAVAILABLE, assertThrows(WorkspaceRuleException.class,
            () -> safe.requireCreateTask(actor, cohort, group, caseId)).kind());
        coreAllows("t3:workspace:read");
        assertFalse(safe.canReadAssignment(actor, cohort, task(), caseId));
    }
    @Test void nullResolvedScopeIsUnavailable() {
        assertEquals(UNAVAILABLE, assertThrows(WorkspaceRuleException.class,
            () -> access.cohortCreationScope(actor, scope.termId())).kind());
    }
    @Test void studentReadsOnlyOwnStoredIdentityWithSchoolMembershipAndReadPermission() {
        when(core.isSelf(actor, student.toString())).thenReturn(true);
        when(core.hasOrganization(actor, scope.schoolOrgId().toString(), Role.STUDENT, "t3:workspace:read")).thenReturn(true);
        assertTrue(access.canReadAssignment(actor, cohort, task(), caseId));
        verifyNoInteractions(authority);
    }
    @Test void selfIdentityAloneDoesNotPermitReadWithoutMembershipAndPermission() {
        when(core.isSelf(actor, student.toString())).thenReturn(true);
        assertFalse(access.canReadAssignment(actor, cohort, task(), caseId));
    }
    @Test void enterpriseReadRequiresCurrentBusinessAuthorityIncludingOldTaskHandover() {
        coreAllows("t3:workspace:read");
        var task = task();
        assertFalse(access.canReadAssignment(actor, cohort, task, caseId));
        when(authority.canReadTask(actor, scope, task, caseId)).thenReturn(true);
        assertTrue(access.canReadAssignment(actor, cohort, task, caseId));
    }
    @Test void schoolReadRequiresDelegationCheckInBusinessAuthority() {
        when(core.hasOrganization(actor, scope.schoolOrgId().toString(), Role.SCHOOL_STAFF, "t3:workspace:read")).thenReturn(true);
        var task = task();
        when(authority.canReadTask(actor, scope, task, caseId)).thenReturn(true);
        assertTrue(access.canReadAssignment(actor, cohort, task, caseId));
    }
    @Test void unknownCaseAndGlobalAdminGrantCannotOpenTask() {
        when(core.hasGlobal(actor, "t3:workspace:read")).thenReturn(true);
        assertFalse(access.canReadAssignment(actor, cohort, task(), UUID.randomUUID()));
        assertFalse(access.canReadAssignment(actor, cohort, task(), caseId));
        verify(core, never()).hasGlobal(any(), anyString());
    }
}
