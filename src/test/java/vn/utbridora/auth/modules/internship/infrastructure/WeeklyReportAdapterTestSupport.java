package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import vn.utbridora.auth.modules.internship.application.WeeklyReportPorts.CaseScope;
import vn.utbridora.auth.security.AppPrincipal;
import static org.junit.jupiter.api.Assertions.*;

/** Spring callback manager for unit tests only; no database or production lock evidence. */
abstract class WeeklyReportAdapterTestSupport {
    final Manager manager = new Manager();
    final UUID caseId = UUID.randomUUID(), placement = UUID.randomUUID(), student = UUID.randomUUID(),
            school = UUID.randomUUID(), enterprise = UUID.randomUUID(), term = UUID.randomUUID(), mentor = UUID.randomUUID();
    final Instant now = Instant.parse("2026-11-10T00:00:00Z");
    final CaseScope scope = new CaseScope(caseId, placement, student, school, enterprise, term,
            "IN_PROGRESS", "ACTIVE", now.minusSeconds(10), now.minusSeconds(20),
            LocalDate.parse("2026-11-04"), LocalDate.parse("2026-12-31"), 3, 8);
    Authentication auth(UUID user) {
        var principal = new AppPrincipal(user.toString(), "synthetic@example.test", null, 4, List.of());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
    }
    CaseRuntime.CaseRef ref(long version) {
        return new CaseRuntime.CaseRef(caseId, placement, student, school, enterprise, term, "IN_PROGRESS", version);
    }
    <T> T inTransaction(Supplier<T> call) {
        return new TransactionTemplate(manager).execute(status -> call.get());
    }
    <T> T inReadOnlyTransaction(Supplier<T> call) {
        var template = new TransactionTemplate(manager); template.setReadOnly(true);
        return template.execute(status -> call.get());
    }
    void code(int status, String expected, org.junit.jupiter.api.function.Executable call) {
        var error = assertThrows(vn.utbridora.auth.operations.OperationFailure.class, call);
        assertEquals(status, error.status); assertEquals(expected, error.code);
    }
    static final class Tx implements SmartTransactionObject {
        boolean active, rollbackOnly;
        public boolean isRollbackOnly() { return rollbackOnly; }
    }
    static final class Manager extends AbstractPlatformTransactionManager {
        private final ThreadLocal<Tx> current = new ThreadLocal<>();
        int begins, commits, rollbacks;
        boolean failCommit;
        protected Object doGetTransaction() { return current.get() == null ? new Tx() : current.get(); }
        protected boolean isExistingTransaction(Object transaction) { return ((Tx) transaction).active; }
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            ((Tx) transaction).active = true; current.set((Tx) transaction); begins++;
        }
        protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) throw new TransactionSystemException("Synthetic commit failure");
            commits++;
        }
        protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
        protected void doSetRollbackOnly(DefaultTransactionStatus status) { ((Tx) status.getTransaction()).rollbackOnly = true; }
        protected void doCleanupAfterCompletion(Object transaction) { ((Tx) transaction).active = false; current.remove(); }
    }
}
