package vn.utbridora.auth.modules.internship.infrastructure;

import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException;
import vn.utbridora.auth.operations.OperationFailure;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** Local adapter checks; shared Core/DEV05 error handling is unchanged. */
final class WeeklyReportRuntimeChecks {
    private WeeklyReportRuntimeChecks() {}
    static void transaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw unavailable();
    }
    static void mutationTransaction() {
        transaction();
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) throw unavailable();
    }
    static <T> T available(T value) { if (value == null) throw unavailable(); return value; }
    static OperationFailure unavailable() { return failure(503, "DEPENDENCY_UNAVAILABLE"); }
    static <T> T workspace(Supplier<T> call) {
        try { return call.get(); }
        catch (WorkspaceRuleException rejected) {
            throw switch (rejected.kind()) {
                case UNAUTHENTICATED -> failure(401, "SESSION_EXPIRED");
                case DENIED -> failure(403, "ACCESS_DENIED");
                case NOT_FOUND, SCOPE_MISMATCH -> failure(404, "RESOURCE_NOT_FOUND");
                case STATE_CONFLICT -> failure(409, "STATE_CONFLICT");
                case VERSION_CONFLICT -> failure(409, "VERSION_CONFLICT");
                default -> unavailable();
            };
        }
    }
}
