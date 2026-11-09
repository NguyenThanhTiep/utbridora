package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.modules.internship.application.WorkspaceService;

@Configuration(proxyBeanMethods=false)
public class WorkspaceConfiguration {
    @Bean WorkspaceService workspaceService(PlatformTransactionManager manager,JdbcWorkspaceSession session,
        AuthorizationPolicy core,WorkspaceAuthority authority,CaseRuntime cases) {
        return new WorkspaceService(new TransactionalWorkspaceUnitOfWork(manager,()->session),
            new CoreWorkspaceAccess(core,authority),cases,Clock.systemUTC(),UUID::randomUUID);
    }
}
