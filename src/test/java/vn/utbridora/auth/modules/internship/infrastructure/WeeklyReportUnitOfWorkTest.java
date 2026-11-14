package vn.utbridora.auth.modules.internship.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.utbridora.auth.modules.internship.application.WeeklyReportPorts;
import vn.utbridora.auth.modules.internship.application.WorkspacePorts;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeeklyReportUnitOfWorkTest extends WeeklyReportAdapterTestSupport {
    private WorkspacePorts.UnitOfWork existing() {
        return new TransactionalWorkspaceUnitOfWork(manager, () -> mock(WorkspacePorts.Session.class));
    }
    @Test void reportSessionJoinsExistingRequiredTransactionAndNestedCallsDoNotCommitSeparately() {
        var session = mock(WeeklyReportPorts.Session.class);
        var work = new WeeklyReportUnitOfWork(existing(), () -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); return session;
        });
        assertEquals("done", work.execute(s -> {
            assertSame(session, s); return work.execute(inner -> "done");
        }));
        assertEquals(1, manager.begins); assertEquals(1, manager.commits); assertEquals(0, manager.rollbacks);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }
    @Test void missingReportSessionOrCallbackFailureRollsBackTheSharedBoundary() {
        var missing = new WeeklyReportUnitOfWork(existing(), () -> null);
        code(503, "DEPENDENCY_UNAVAILABLE", () -> missing.execute(s -> "never"));
        var work = new WeeklyReportUnitOfWork(existing(), () -> mock(WeeklyReportPorts.Session.class));
        assertThrows(IllegalStateException.class, () -> work.execute(s -> { throw new IllegalStateException("Synthetic audit failure"); }));
        assertEquals(2, manager.rollbacks); assertEquals(0, manager.commits);
    }
    @Test void failedCommitCannotReturnAReportSuccess() {
        manager.failCommit = true;
        var work = new WeeklyReportUnitOfWork(existing(), () -> mock(WeeklyReportPorts.Session.class));
        assertThrows(TransactionSystemException.class, () -> work.execute(s -> "must not return"));
        assertEquals(0, manager.commits);
    }
    @Test void aDelegateThatDoesNotOpenATransactionIsRejected() {
        WorkspacePorts.UnitOfWork fake = new WorkspacePorts.UnitOfWork() {
            public <T> T execute(java.util.function.Function<WorkspacePorts.Session, T> call) { return call.apply(mock(WorkspacePorts.Session.class)); }
        };
        var work = new WeeklyReportUnitOfWork(fake, () -> mock(WeeklyReportPorts.Session.class));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> work.execute(s -> "must not return"));
    }
    @Test void nullDelegateResultCannotBecomeAFalseSuccess() {
        WorkspacePorts.UnitOfWork missing = new WorkspacePorts.UnitOfWork() {
            public <T> T execute(java.util.function.Function<WorkspacePorts.Session, T> call) { return null; }
        };
        var work = new WeeklyReportUnitOfWork(missing, () -> mock(WeeklyReportPorts.Session.class));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> work.execute(s -> "must not return"));
    }
    @Test void readOnlySessionQueriesRemainAllowed() {
        var session = mock(WeeklyReportPorts.Session.class);
        var work = new WeeklyReportUnitOfWork(existing(), () -> session);
        assertEquals("read", inReadOnlyTransaction(() -> work.execute(s -> {
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly()); return "read";
        })));
    }
}
