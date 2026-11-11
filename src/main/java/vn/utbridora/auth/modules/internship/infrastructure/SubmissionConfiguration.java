package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.modules.internship.application.SubmissionService;
import vn.utbridora.auth.operations.OperationLedger;

/** Real adapters only. Missing V28/V29 fails closed per request, never creates schema/providers.
 * Mandatory Core audit support is not fabricated; no File Service substitute is installed.
 */
@Configuration(proxyBeanMethods = false)
public class SubmissionConfiguration {
    @Bean Clock submissionClock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
    @Bean JdbcSubmissionSession jdbcSubmissionSession(JdbcTemplate jdbc, OperationLedger ledger, AuditService audit) {
        return new JdbcSubmissionSession(jdbc, ledger, audit);
    }

    @Bean SubmissionService submissionService(PlatformTransactionManager manager, JdbcSubmissionSession session,
            AuthorizationPolicy core, WorkspaceAuthority authority, CaseRuntime cases,
            @Qualifier("submissionClock") Clock clock) {
        var work = new TransactionalSubmissionUnitOfWork(manager, () -> {
            session.requireBaseline();
            return session;
        });
        return new SubmissionService(work, new CoreSubmissionAccess(core, new CoreWorkspaceAccess(core, authority), authority), cases,
                clock, UUID::randomUUID);
    }
}
