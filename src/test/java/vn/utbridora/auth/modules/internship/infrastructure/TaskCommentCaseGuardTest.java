package vn.utbridora.auth.modules.internship.infrastructure;

import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.utbridora.auth.modules.internship.application.*;
import vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskCommentCaseGuardTest extends Dev06WorkflowFixture {
    private final WorkspacePorts.CaseChecks owner = mock(WorkspacePorts.CaseChecks.class);
    private final WeeklyReportPorts.Cases snapshots = mock(WeeklyReportPorts.Cases.class);
    private TaskCommentCaseGuard guard;
    @BeforeEach void setup() {
        when(owner.requireTaskParticipant(studentActor, target.cohort(), target.group(), caseId, now)).thenReturn(student);
        when(snapshots.current(caseId)).thenReturn(scope);
        guard = new TaskCommentCaseGuard(owner, snapshots);
    }
    private <T> T rc(Supplier<T> call) {
        var tx = new TransactionTemplate(manager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return tx.execute(status -> call.get());
    }
    private void hold() { guard.hold(studentActor, target, scope, now); }
    @Test void delegatesRealTaskTargetToOwnerThenChecksFreshSourceSnapshotWithoutReportFence() {
        rc(() -> { hold(); return true; });
        var order = inOrder(owner, snapshots);
        order.verify(owner).requireTaskParticipant(studentActor, target.cohort(), target.group(), caseId, now);
        order.verify(snapshots).current(caseId);
        verify(snapshots, never()).holdForMutation(any()); assertEquals(1, manager.commits);
    }
    @Test void rejectsMissingTransactionAndReadOnlyTransaction() {
        code(503, "DEPENDENCY_UNAVAILABLE", this::hold);
        code(503, "DEPENDENCY_UNAVAILABLE", () -> inReadOnlyTransaction(() -> { hold(); return true; }));
        verifyNoInteractions(owner, snapshots);
    }
    @ParameterizedTest @ValueSource(ints = {TransactionDefinition.ISOLATION_DEFAULT,
            TransactionDefinition.ISOLATION_REPEATABLE_READ, TransactionDefinition.ISOLATION_SERIALIZABLE})
    void incompatibleOrUnknownIsolationCannotAuthorizeMutation(int isolation) {
        var tx = new TransactionTemplate(manager); tx.setIsolationLevel(isolation);
        code(503, "DEPENDENCY_UNAVAILABLE", () -> tx.execute(status -> { hold(); return true; }));
        verifyNoInteractions(owner, snapshots);
    }
    @Test void ownerStudentCannotBeSubstituted() {
        when(owner.requireTaskParticipant(studentActor, target.cohort(), target.group(), caseId, now)).thenReturn(outsider);
        code(404, "RESOURCE_NOT_FOUND", () -> rc(() -> { hold(); return true; }));
        verifyNoInteractions(snapshots);
    }
    @Test void authoritativePlacementDriftAfterSourceLockFailsClosed() {
        placementState("CANCELLED"); when(snapshots.current(caseId)).thenReturn(currentScope);
        code(409, "VERSION_CONFLICT", () -> rc(() -> { hold(); return true; }));
        assertEquals(1, manager.rollbacks);
    }
    @Test void ownerScopeMismatchMapsToHiddenResource() {
        when(owner.requireTaskParticipant(studentActor, target.cohort(), target.group(), caseId, now))
                .thenThrow(new WorkspaceRuleException(WorkspaceRuleException.Kind.SCOPE_MISMATCH));
        code(404, "RESOURCE_NOT_FOUND", () -> rc(() -> { hold(); return true; }));
        verifyNoInteractions(snapshots);
    }
    @Test void ownerStateConflictIsPreserved() {
        when(owner.requireTaskParticipant(studentActor, target.cohort(), target.group(), caseId, now))
                .thenThrow(new WorkspaceRuleException(WorkspaceRuleException.Kind.STATE_CONFLICT));
        code(409, "STATE_CONFLICT", () -> rc(() -> { hold(); return true; }));
    }
    @Test void unavailableOwnerOrSnapshotDoesNotGrantAnything() {
        code(503, "DEPENDENCY_UNAVAILABLE", () -> new TaskCommentCaseGuard(null, snapshots));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> new TaskCommentCaseGuard(owner, null));
        when(snapshots.current(caseId)).thenReturn(null);
        code(503, "DEPENDENCY_UNAVAILABLE", () -> rc(() -> { hold(); return true; }));
    }
    @Test void wrongCaseOrStudentInSnapshotIsHidden() {
        var wrong = new WeeklyReportPorts.CaseScope(UUID.randomUUID(), placement, outsider, school, enterprise, term,
                scope.caseState(), scope.placementState(), scope.effectiveAt(), scope.approvedAt(), scope.startDate(), scope.endDate(), 3, 8);
        when(snapshots.current(caseId)).thenReturn(wrong);
        code(404, "RESOURCE_NOT_FOUND", () -> rc(() -> { hold(); return true; }));
    }
}
