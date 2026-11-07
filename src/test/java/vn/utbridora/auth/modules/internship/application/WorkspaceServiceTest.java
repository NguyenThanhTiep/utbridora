package vn.utbridora.auth.modules.internship.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import vn.utbridora.auth.modules.internship.api.CohortGroupDtos.*;
import vn.utbridora.auth.modules.internship.api.TaskDtos.*;
import vn.utbridora.auth.modules.internship.domain.*;
import static vn.utbridora.auth.modules.internship.application.WorkspacePorts.*;
import static vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException.Kind.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** In-process consumer tests only. Mock Session is never registered as production persistence. */
class WorkspaceServiceTest {
    private final Session session = mock(Session.class);
    private final Access access = mock(Access.class);
    private final CaseChecks cases = mock(CaseChecks.class);
    private final UnitOfWork work = new UnitOfWork() {
        public <T> T execute(Function<Session, T> operation) { return operation.apply(session); }
    };
    private final Instant now = Instant.parse("2026-11-07T03:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final Authentication actor = UsernamePasswordAuthenticationToken.authenticated("server-user", null, List.of());
    private final UUID cohortId = UUID.randomUUID(), groupId = UUID.randomUUID(), targetId = UUID.randomUUID();
    private final UUID caseId = UUID.randomUUID(), otherCase = UUID.randomUUID(), student = UUID.randomUUID();
    private final WorkspaceScope scope = new WorkspaceScope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    private final Cohort cohort = new Cohort(cohortId, scope, "C", "Name", 1);
    private InternshipGroup group;
    private WorkspaceService service;
    @BeforeEach void setup() {
        group = new InternshipGroup(groupId, cohortId, "G", List.of(caseId, otherCase), List.of(), 3);
        when(session.group(groupId)).thenReturn(group);
        when(session.cohort(cohortId)).thenReturn(cohort);
        when(session.currentGroup(cohortId, caseId)).thenReturn(groupId);
        when(session.currentGroup(cohortId, otherCase)).thenReturn(groupId);
        when(access.cohortCreationScope(actor, scope.termId())).thenReturn(scope);
        when(cases.requireTaskParticipant(actor, cohort, group, caseId, now)).thenReturn(student);
        when(cases.requireTaskParticipant(actor, cohort, group, otherCase, now)).thenReturn(UUID.randomUUID());
        service = new WorkspaceService(work, access, cases, clock, UUID::randomUUID);
    }
    private CreateTask taskInput() {
        return new CreateTask(groupId, "Task", "Description", now.plusSeconds(3600), TaskPriority.NORMAL,
            List.of(caseId, otherCase), true);
    }
    private void fails(WorkspaceRuleException.Kind kind, org.junit.jupiter.api.function.Executable action) {
        assertEquals(kind, assertThrows(WorkspaceRuleException.class, action).kind());
    }
    private void noMembershipWrite() {
        verify(session, never()).addMember(any(), any(), anyLong(), anyLong());
        verify(session, never()).moveMember(any(), any(), anyLong(), anyLong(), anyLong(), anyString());
    }
    @Test void cohortUsesResolvedScopeAndAtomicUniqueInsert() {
        var result = service.createCohort(actor, new CreateCohort(scope.termId(), " C ", " Name "));
        assertEquals(scope.schoolOrgId(), result.schoolOrgId());
        assertEquals(scope.enterpriseOrgId(), result.enterpriseOrgId());
        assertEquals("C", result.code());
        verify(session).insertCohort(argThat(c -> c.key().equals(cohort.key())));
    }
    @Test void duplicateIsPropagatedFromAtomicStoreNotPrecheck() {
        doThrow(new WorkspaceRuleException(DUPLICATE_COHORT)).when(session).insertCohort(any());
        fails(DUPLICATE_COHORT, () -> service.createCohort(actor, new CreateCohort(scope.termId(), "C", "Name")));
    }
    @Test void existingCohortWithSameScopeAndTrimmedCodeIsRejectedBeforeInsert() {
        when(session.cohortByKey(cohort.key())).thenReturn(cohort);
        fails(DUPLICATE_COHORT, () -> service.createCohort(actor, new CreateCohort(scope.termId(), " C ", "Other name")));
        verify(session, never()).insertCohort(any());
    }
    @Test void cohortDeniedBeforeInsert() {
        when(access.cohortCreationScope(actor, scope.termId())).thenThrow(new WorkspaceRuleException(DENIED));
        fails(DENIED, () -> service.createCohort(actor, new CreateCohort(scope.termId(), "C", "Name")));
        verify(session, never()).insertCohort(any());
    }
    @Test void missingScopeFailsClosed() {
        when(access.cohortCreationScope(actor, scope.termId())).thenReturn(null);
        fails(UNAVAILABLE, () -> service.createCohort(actor, new CreateCohort(scope.termId(), "C", "Name")));
        verify(session, never()).insertCohort(any());
    }
    @Test void resolvedWrongTermFailsBeforeInsert() {
        when(access.cohortCreationScope(actor, scope.termId()))
            .thenReturn(new WorkspaceScope(scope.enterpriseOrgId(), scope.schoolOrgId(), UUID.randomUUID()));
        fails(SCOPE_MISMATCH, () -> service.createCohort(actor, new CreateCohort(scope.termId(), "C", "Name")));
        verify(session, never()).insertCohort(any());
    }
    @Test void unauthenticatedAndAnonymousActorsNeverReachAdapters() {
        var input = new CreateCohort(scope.termId(), "C", "Name");
        fails(UNAUTHENTICATED, () -> service.createCohort(null, input));
        fails(UNAUTHENTICATED, () -> service.createCohort(UsernamePasswordAuthenticationToken.unauthenticated("user", ""), input));
        var anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
            List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        fails(UNAUTHENTICATED, () -> service.createCohort(anonymous, input));
        verifyNoInteractions(access);
    }
    @Test void addChecksCaseThenCommitsExpectedVersions() {
        UUID added = UUID.randomUUID();
        var result = service.addMember(actor, groupId, new AssignGroupMember(added, 9L, 3L));
        assertTrue(result.caseIds().contains(added));
        assertEquals(4, result.version());
        var ordered = inOrder(access, cases, session);
        ordered.verify(access).requireManageGroup(actor, cohort, group);
        ordered.verify(cases).requireMembership(actor, cohort, added, 9);
        ordered.verify(session).currentGroup(cohortId, added);
        ordered.verify(session).addMember(any(), eq(added), eq(9L), eq(3L));
    }
    @Test void addDuplicateDoesNotWrite() {
        fails(GROUP_MEMBERSHIP_CONFLICT, () -> service.addMember(actor, groupId, new AssignGroupMember(caseId, 1L, 3L)));
        noMembershipWrite();
    }
    @Test void addStaleGroupDoesNotWrite() {
        fails(VERSION_CONFLICT, () -> service.addMember(actor, groupId,
            new AssignGroupMember(UUID.randomUUID(), 1L, 2L)));
        noMembershipWrite();
    }
    @Test void caseScopeOrVersionFailureStopsMembership() {
        doThrow(new WorkspaceRuleException(SCOPE_MISMATCH)).when(cases).requireMembership(actor, cohort, caseId, 1);
        fails(SCOPE_MISMATCH, () -> service.addMember(actor, groupId, new AssignGroupMember(caseId, 1L, 3L)));
        noMembershipWrite();
    }
    @Test void missingCaseProviderDeniesAddMoveAndTask() {
        var safe = new WorkspaceService(work, access, unavailableCases(), clock, UUID::randomUUID);
        fails(UNAVAILABLE, () -> safe.addMember(actor, groupId, new AssignGroupMember(caseId, 1L, 3L)));
        when(session.group(targetId)).thenReturn(new InternshipGroup(targetId, cohortId, "G2", List.of(), List.of(), 4));
        fails(UNAVAILABLE, () -> safe.moveMember(actor, groupId, move()));
        fails(UNAVAILABLE, () -> safe.createTask(actor, taskInput()));
        noMembershipWrite();
        verify(session, never()).insertTask(any());
    }
    private MoveCase move() { return new MoveCase(caseId, targetId, 9L, 3L, 4L, "REASSIGN"); }
    @Test void moveAuthorizesBothGroupsAndPreservesReasonAndVersions() {
        var target = new InternshipGroup(targetId, cohortId, "G2", List.of(), List.of(), 4);
        when(session.group(targetId)).thenReturn(target);
        var transfer = service.moveMember(actor, groupId, move());
        assertFalse(transfer.source().caseIds().contains(caseId));
        assertTrue(transfer.target().caseIds().contains(caseId));
        verify(access).requireManageGroup(actor, cohort, group);
        verify(access).requireManageGroup(actor, cohort, target);
        verify(session).moveMember(transfer, caseId, 9, 3, 4, "REASSIGN");
    }
    @Test void targetPermissionFailureDoesNotWrite() {
        var target = new InternshipGroup(targetId, cohortId, "G2", List.of(), List.of(), 4);
        when(session.group(targetId)).thenReturn(target);
        doThrow(new WorkspaceRuleException(DENIED)).when(access).requireManageGroup(actor, cohort, target);
        fails(DENIED, () -> service.moveMember(actor, groupId, move()));
        verifyNoInteractions(cases);
        noMembershipWrite();
    }
    @Test void crossTenantMoveRejectedEvenIfAccessAdapterAllowsBoth() {
        UUID foreignId = UUID.randomUUID();
        var foreign = new Cohort(foreignId, new WorkspaceScope(UUID.randomUUID(), scope.schoolOrgId(), scope.termId()), "C", "N", 1);
        when(session.cohort(foreignId)).thenReturn(foreign);
        when(session.group(targetId)).thenReturn(new InternshipGroup(targetId, foreignId, "G2", List.of(), List.of(), 4));
        fails(SCOPE_MISMATCH, () -> service.moveMember(actor, groupId, move()));
        noMembershipWrite();
    }
    @Test void storeConcurrencyFailurePropagatesWithoutSuccess() {
        doThrow(new WorkspaceRuleException(VERSION_CONFLICT)).when(session).addMember(any(), any(), anyLong(), anyLong());
        fails(VERSION_CONFLICT, () -> service.addMember(actor, groupId,
            new AssignGroupMember(UUID.randomUUID(), 1L, 3L)));
    }
    @Test void missingGroupIsNotFound() {
        fails(NOT_FOUND, () -> service.addMember(actor, UUID.randomUUID(), new AssignGroupMember(caseId, 1L, 1L)));
        verifyNoInteractions(access, cases);
    }
    @Test void taskChecksEveryCaseAndUsesAuthoritativeStudentIdentity() {
        var result = service.createTask(actor, taskInput());
        assertEquals(2, result.assignments().size());
        assertEquals(student, result.assignments().getFirst().studentUserId());
        assertEquals(AssignmentState.NEW, result.assignments().getFirst().state());
        verify(access).requireCreateTask(actor, cohort, group, caseId);
        verify(access).requireCreateTask(actor, cohort, group, otherCase);
        verify(cases).requireTaskParticipant(actor, cohort, group, caseId, now);
        verify(cases).requireTaskParticipant(actor, cohort, group, otherCase, now);
        verify(session).insertTask(argThat(t -> t.value().equals(result)));
    }
    @Test void oneDeniedCasePreventsEntireTaskWrite() {
        doThrow(new WorkspaceRuleException(DENIED)).when(access).requireCreateTask(actor, cohort, group, otherCase);
        fails(DENIED, () -> service.createTask(actor, taskInput()));
        verify(session, never()).insertTask(any());
    }
    @Test void inactivePlacementOrCaseRejectionPreventsEntireTaskWrite() {
        when(cases.requireTaskParticipant(actor, cohort, group, otherCase, now))
            .thenThrow(new WorkspaceRuleException(INVALID_OPERATION));
        fails(INVALID_OPERATION, () -> service.createTask(actor, taskInput()));
        verify(session, never()).insertTask(any());
    }
    @Test void missingAuthoritativeStudentFailsClosed() {
        when(cases.requireTaskParticipant(actor, cohort, group, caseId, now)).thenReturn(null);
        fails(UNAVAILABLE, () -> service.createTask(actor, taskInput()));
        verify(session, never()).insertTask(any());
    }
    @Test void movedMembershipCannotCreateTaskAgainstOldGroup() {
        when(session.currentGroup(cohortId, caseId)).thenReturn(targetId);
        fails(GROUP_MEMBERSHIP_CONFLICT, () -> service.createTask(actor, taskInput()));
        verifyNoInteractions(cases);
        verify(session, never()).insertTask(any());
    }
    @Test void caseAbsentFromGroupSnapshotRejected() {
        var input = new CreateTask(groupId, "T", "D", now, TaskPriority.LOW, List.of(UUID.randomUUID()), false);
        fails(GROUP_MEMBERSHIP_CONFLICT, () -> service.createTask(actor, input));
        verify(session, never()).insertTask(any());
    }
    @Test void taskReadFiltersOtherStudentsWithoutMutatingStoredSnapshot() {
        var task = new InternshipTask(service.createTask(actor, taskInput()));
        when(session.task(task.value().taskId())).thenReturn(task);
        when(access.canReadAssignment(actor, cohort, task, caseId)).thenReturn(true);
        var result = service.readTask(actor, task.value().taskId());
        assertEquals(List.of(caseId), result.assignments().stream().map(AssignmentDto::caseId).toList());
        assertEquals(2, task.value().assignments().size());
    }
    @Test void taskReadDeniesWhenNoAssignmentVisible() {
        var task = new InternshipTask(service.createTask(actor, taskInput()));
        when(session.task(task.value().taskId())).thenReturn(task);
        fails(DENIED, () -> service.readTask(actor, task.value().taskId()));
    }
    @Test void nullDependenciesCannotEnableService() {
        assertThrows(NullPointerException.class, () -> new WorkspaceService(work, access, null, clock, UUID::randomUUID));
    }
    @Test void wrongResourceReturnedByAdapterDoesNotAuthorizeOrWrite() {
        when(session.group(groupId)).thenReturn(new InternshipGroup(UUID.randomUUID(), cohortId, "G", List.of(), List.of(), 3));
        fails(UNAVAILABLE, () -> service.addMember(actor, groupId, new AssignGroupMember(caseId, 1L, 3L)));
        verifyNoInteractions(access, cases);
        noMembershipWrite();
    }
    @Test void wrongCohortReturnedByAdapterDoesNotReachGuard() {
        when(session.cohort(cohortId)).thenReturn(new Cohort(UUID.randomUUID(), scope, "C", "Name", 1));
        fails(UNAVAILABLE, () -> service.createTask(actor, taskInput()));
        verifyNoInteractions(access, cases);
        verify(session, never()).insertTask(any());
    }
    @Test void taskReadRejectsAdapterReturningDifferentTaskId() {
        var task = new InternshipTask(service.createTask(actor, taskInput()));
        UUID requested = UUID.randomUUID();
        when(session.task(requested)).thenReturn(task);
        clearInvocations(access);
        fails(UNAVAILABLE, () -> service.readTask(actor, requested));
        verifyNoInteractions(access);
    }
    @Test void staleCaseVersionStopsBeforeMembershipWrite() {
        doThrow(new WorkspaceRuleException(VERSION_CONFLICT)).when(cases).requireMembership(actor, cohort, caseId, 9);
        when(session.group(targetId)).thenReturn(new InternshipGroup(targetId, cohortId, "G2", List.of(), List.of(), 4));
        fails(VERSION_CONFLICT, () -> service.moveMember(actor, groupId, move()));
        noMembershipWrite();
        assertEquals(List.of(caseId, otherCase), group.caseIds());
    }
    @Test void membershipIndexFromDifferentCohortCannotPermitMove() {
        UUID otherCohort = UUID.randomUUID();
        when(session.cohort(otherCohort)).thenReturn(new Cohort(otherCohort, scope, "C2", "N", 1));
        when(session.group(targetId)).thenReturn(new InternshipGroup(targetId, otherCohort, "G2", List.of(), List.of(), 4));
        fails(SCOPE_MISMATCH, () -> service.moveMember(actor, groupId, move()));
        noMembershipWrite();
    }
    @Test void taskProviderStateConflictOrTemporaryFailureNeverWritesPartialTask() {
        for (var kind : List.of(STATE_CONFLICT, VERSION_CONFLICT, UNAVAILABLE, SCOPE_MISMATCH, DENIED)) {
            doThrow(new WorkspaceRuleException(kind)).when(cases).requireTaskParticipant(actor, cohort, group, otherCase, now);
            fails(kind, () -> service.createTask(actor, taskInput()));
            assertEquals(List.of(caseId, otherCase), group.caseIds());
        }
        verify(session, never()).insertTask(any());
    }
    @Test void nullInputRejectedBeforeAnyMutation() {
        assertThrows(NullPointerException.class, () -> service.createCohort(actor, null));
        assertThrows(NullPointerException.class, () -> service.addMember(actor, groupId, null));
        assertThrows(NullPointerException.class, () -> service.moveMember(actor, groupId, null));
        assertThrows(NullPointerException.class, () -> service.createTask(actor, null));
        verifyNoInteractions(access, cases);
        noMembershipWrite();
    }
}
