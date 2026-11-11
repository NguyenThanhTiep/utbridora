package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.modules.internship.application.SubmissionService;
import vn.utbridora.auth.operations.OperationLedger;

/** Real adapters only. Missing V28/V29 fails closed per request, never creates schema/providers.
 * No Review endpoint/provider or File Service substitute is installed by this configuration.
 */
@Configuration(proxyBeanMethods = false)
public class SubmissionConfiguration {
    @Bean JdbcSubmissionSession jdbcSubmissionSession(JdbcTemplate jdbc, OperationLedger ledger, AuditService audit) {
        return new JdbcSubmissionSession(jdbc, ledger, audit);
    }

    @Bean SubmissionService submissionService(PlatformTransactionManager manager, JdbcSubmissionSession session,
            AuthorizationPolicy core, WorkspaceAuthority authority, CaseRuntime cases) {
        var work = new TransactionalSubmissionUnitOfWork(manager, () -> {
            session.requireBaseline();
            return session;
        });
        return new SubmissionService(work, new CoreSubmissionAccess(core, new CoreWorkspaceAccess(core, authority)), cases,
                Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000)), UUID::randomUUID);
    }
}
