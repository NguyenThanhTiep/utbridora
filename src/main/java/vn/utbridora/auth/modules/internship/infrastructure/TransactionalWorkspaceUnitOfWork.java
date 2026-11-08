package vn.utbridora.auth.modules.internship.infrastructure;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.utbridora.auth.modules.internship.application.WorkspacePorts.*;
import vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException;

/** Spring REQUIRED transaction boundary. No Session provider or bean is fabricated here.
 * A real Session must join this SAME transaction for CAS/unique/history/audit writes.
 * If joined to an outer transaction, the outer boundary still controls the final commit.
 */
public final class TransactionalWorkspaceUnitOfWork implements UnitOfWork {
    private final TransactionTemplate transactions;
    private final Supplier<Session> sessions;
    public TransactionalWorkspaceUnitOfWork(PlatformTransactionManager manager, Supplier<Session> sessions) {
        this.transactions = new TransactionTemplate(Objects.requireNonNull(manager));
        this.sessions = Objects.requireNonNull(sessions);
    }
    @Override public <T> T execute(Function<Session, T> operation) {
        Objects.requireNonNull(operation);
        return transactions.execute(status -> {
            var session = sessions.get();
            if (session == null) throw new WorkspaceRuleException(WorkspaceRuleException.Kind.UNAVAILABLE);
            return operation.apply(session);
        });
    }
}
