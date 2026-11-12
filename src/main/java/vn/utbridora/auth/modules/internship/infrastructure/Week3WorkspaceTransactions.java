package vn.utbridora.auth.modules.internship.infrastructure;

import java.util.function.Function;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.utbridora.auth.modules.internship.application.WorkspacePorts;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** Shares the existing manager/connection with Core, JDBC, audit and ledger. */
public final class Week3WorkspaceTransactions implements WorkspacePorts.UnitOfWork {
    private final TransactionTemplate transactions;
    private final JdbcWorkspaceSession session;
    public Week3WorkspaceTransactions(PlatformTransactionManager manager, JdbcWorkspaceSession session) {
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.session = session;
    }
    @Override public <T> T execute(Function<WorkspacePorts.Session, T> operation) {
        return transactions.execute(status -> {
            if (!Integer.valueOf(TransactionDefinition.ISOLATION_READ_COMMITTED).equals(
                    TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                    || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                throw failure(503, "DEPENDENCY_UNAVAILABLE");
            }
            return operation.apply(session);
        });
    }
}
