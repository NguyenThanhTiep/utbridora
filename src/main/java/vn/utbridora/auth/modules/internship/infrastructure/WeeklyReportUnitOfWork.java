package vn.utbridora.auth.modules.internship.infrastructure;

import java.util.function.*;
import vn.utbridora.auth.modules.internship.application.WeeklyReportPorts.*;
import vn.utbridora.auth.modules.internship.application.WorkspacePorts;
import static vn.utbridora.auth.modules.internship.infrastructure.WeeklyReportRuntimeChecks.*;

/** Type adapter over the existing TransactionalWorkspaceUnitOfWork/REQUIRED boundary.
 * The future Report Session must join that same manager/connection, never a separate transaction.
 * No report store, second transaction implementation or Spring bean is created here.
 */
public final class WeeklyReportUnitOfWork implements UnitOfWork {
    private final WorkspacePorts.UnitOfWork workspace;
    private final Supplier<Session> sessions;
    public WeeklyReportUnitOfWork(WorkspacePorts.UnitOfWork workspace, Supplier<Session> sessions) {
        this.workspace = available(workspace); this.sessions = available(sessions);
    }
    @Override public <T> T execute(Function<Session, T> operation) {
        available(operation);
        return available(workspace.execute(ignored -> {
            transaction();
            return available(operation.apply(available(sessions.get())));
        }));
    }
}
