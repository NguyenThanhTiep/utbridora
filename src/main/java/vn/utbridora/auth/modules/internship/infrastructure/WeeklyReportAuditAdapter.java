package vn.utbridora.auth.modules.internship.infrastructure;

import vn.utbridora.auth.audit.AuditAction;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.modules.internship.application.WeeklyReportPorts.*;
import static vn.utbridora.auth.modules.internship.infrastructure.WeeklyReportRuntimeChecks.*;

/** Bridge to T1 workflow audit; missing WEEKLY_REPORT actions fail closed, never use TASK_COMMENTED
 * or a legacy action as a substitute. Enum/registry remain T1-owned and are not edited here. */
public final class WeeklyReportAuditAdapter implements Audit {
    private final AuditService audit;
    public WeeklyReportAuditAdapter(AuditService audit) { this.audit = available(audit); }
    @Override public void append(AuditEvent event) {
        mutationTransaction(); available(event);
        try { AuditAction.valueOf(event.action().name()); }
        catch (IllegalArgumentException missingAction) { throw unavailable(); }
        try {
            audit.workflow(event.actorId().toString(), event.action().name(), event.reportId().toString(),
                    event.schoolOrgId().toString(), event.metadata());
        } catch (IllegalArgumentException missingRegistry) { throw unavailable(); }
    }
}
