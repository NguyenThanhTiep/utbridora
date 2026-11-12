package vn.utbridora.auth.modules.internship.infrastructure;

import vn.utbridora.auth.audit.AuditAction;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.modules.internship.application.TaskCommentPorts.*;
import static vn.utbridora.auth.modules.internship.infrastructure.WeeklyReportRuntimeChecks.*;

/** Exact CP-WS2/E.3 TASK_COMMENTED bridge; missing T1 action/registry stays fail-closed. */
public final class TaskCommentAuditAdapter implements Audit {
    private final AuditService audit;
    public TaskCommentAuditAdapter(AuditService audit) { this.audit = available(audit); }
    @Override public void append(AuditEvent event) {
        mutationTransaction(); available(event);
        try { AuditAction.valueOf("TASK_COMMENTED"); }
        catch (IllegalArgumentException missingAction) { throw unavailable(); }
        try {
            audit.workflow(event.actorId().toString(), "TASK_COMMENTED", event.assignmentId().toString(),
                    event.schoolOrgId().toString(), event.metadata());
        } catch (IllegalArgumentException missingRegistry) { throw unavailable(); }
    }
}
