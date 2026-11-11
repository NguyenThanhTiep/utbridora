package vn.utbridora.auth.modules.internship.infrastructure;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.utbridora.auth.modules.internship.application.SubmissionPorts.*;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** REQUIRED / READ_COMMITTED transaction shared by JDBC, Core authorization, ledger and mandatory audit.
 * Does not commit a separate ledger reservation or swallow a mutation/audit failure.
 * CaseRuntime's reads after source/Case locks must see the committed state, not a pre-lock
 * REPEATABLE_READ snapshot. An incompatible outer transaction cannot silently override this.
 */
public final class TransactionalSubmissionUnitOfWork implements UnitOfWork {
    private final TransactionTemplate transactions;
    private final Supplier<Session> sessions;

    public TransactionalSubmissionUnitOfWork(PlatformTransactionManager manager, Supplier<Session> sessions) {
        transactions = new TransactionTemplate(Objects.requireNonNull(manager));
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.sessions = Objects.requireNonNull(sessions);
    }

    @Override public <T> T execute(Function<Session, T> operation) {
        Objects.requireNonNull(operation);
        return transactions.execute(status -> {
            if (!Integer.valueOf(TransactionDefinition.ISOLATION_READ_COMMITTED).equals(
                    TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                    || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                throw failure(503, "DEPENDENCY_UNAVAILABLE");
            }
            var session = sessions.get();
            if (session == null) throw failure(503, "DEPENDENCY_UNAVAILABLE");
            return operation.apply(session);
        });
    }
}
