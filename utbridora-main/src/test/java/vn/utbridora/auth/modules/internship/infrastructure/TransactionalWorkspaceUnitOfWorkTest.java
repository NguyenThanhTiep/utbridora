package vn.utbridora.auth.modules.internship.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.utbridora.auth.modules.internship.application.WorkspacePorts.Session;
import vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Spring transaction callbacks with an in-process test manager, NOT database evidence. */
class TransactionalWorkspaceUnitOfWorkTest {
    static final class Manager extends AbstractPlatformTransactionManager {
        int begins, commits, rollbacks;
        boolean failCommit;
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object transaction, TransactionDefinition definition) { begins++; }
        protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) throw new TransactionSystemException("test commit failure");
            commits++;
        }
        protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
    @Test void sessionIsObtainedInsideTransactionAndResultReturnedAfterCommit() {
        var manager = new Manager();
        var session = mock(Session.class);
        var work = new TransactionalWorkspaceUnitOfWork(manager, () -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return session;
        });
        assertEquals("result", work.execute(s -> { assertSame(session, s); return "result"; }));
        assertEquals(1, manager.begins);
        assertEquals(1, manager.commits);
        assertEquals(0, manager.rollbacks);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }
    @Test void domainFailureRollsBackWithoutCommit() {
        var manager = new Manager();
        var work = new TransactionalWorkspaceUnitOfWork(manager, () -> mock(Session.class));
        assertThrows(WorkspaceRuleException.class, () -> work.execute(s -> {
            throw new WorkspaceRuleException(WorkspaceRuleException.Kind.VERSION_CONFLICT);
        }));
        assertEquals(1, manager.rollbacks);
        assertEquals(0, manager.commits);
    }
    @Test void missingSessionFailsClosedAndRollsBack() {
        var manager = new Manager();
        var work = new TransactionalWorkspaceUnitOfWork(manager, () -> null);
        var error = assertThrows(WorkspaceRuleException.class, () -> work.execute(s -> "must not succeed"));
        assertEquals(WorkspaceRuleException.Kind.UNAVAILABLE, error.kind());
        assertEquals(1, manager.rollbacks);
        assertEquals(0, manager.commits);
    }
    @Test void commitFailureNeverReturnsSuccess() {
        var manager = new Manager();
        manager.failCommit = true;
        var work = new TransactionalWorkspaceUnitOfWork(manager, () -> mock(Session.class));
        assertThrows(TransactionSystemException.class, () -> work.execute(s -> "must not return"));
        assertEquals(0, manager.commits);
    }
}
