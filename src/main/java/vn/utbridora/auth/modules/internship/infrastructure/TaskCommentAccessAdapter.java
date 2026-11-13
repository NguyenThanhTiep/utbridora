package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.modules.internship.application.TaskCommentPorts.*;
import vn.utbridora.auth.modules.internship.application.WeeklyReportPorts.CaseScope;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.modules.internship.infrastructure.WeeklyReportRuntimeChecks.*;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** FR-17 adapter; reuses original-principal validation, current authority and real Task read gate.
 * Does not seed t3:comments:write or replace it with submission_write/task_write. No bean/route. */
public final class TaskCommentAccessAdapter implements Access {
    private static final String WRITE = "t3:comments:write";
    private final WeeklyReportAccessAdapter identity;
    private final AuthorizationPolicy core;
    private final WorkspaceAuthority authority;
    private final WorkspaceRuntime runtime;
    public TaskCommentAccessAdapter(WeeklyReportAccessAdapter identity, AuthorizationPolicy core,
                                     WorkspaceAuthority authority, WorkspaceRuntime runtime) {
        this.identity = available(identity); this.core = available(core); this.authority = available(authority); this.runtime = available(runtime);
    }
    @Override public UUID currentUser(Authentication actor) { return identity.currentUser(actor); }
    @Override public void requireWrite(Authentication actor, Target target, CaseScope scope, Instant now) {
        UUID user = currentUser(actor);
        if (!target.matches(scope)) throw hidden();
        if (user.equals(scope.studentUserId())) {
            if (!core.holdOrganization(actor, scope.schoolOrgId().toString(), Role.STUDENT, WRITE)) throw hidden();
            return;
        }
        if (!core.holdOrganization(actor, scope.enterpriseOrgId().toString(), Role.ENTERPRISE, WRITE)) throw hidden();
        var selected = workspace(() -> available(authority.selected(actor)));
        if (!"MENTOR".equals(selected.kind()) || !user.toString().equals(selected.userId())
                || !selected.active() || now.isBefore(selected.validFrom()) || !now.isBefore(selected.validUntil())
                || !WorkspaceAuthority.same(selected.scope(), target.cohort().scope())
                || selected.scope().caseId() != null && !selected.scope().caseId().equals(scope.caseId().toString())) throw hidden();
        // Old Task group remains immutable; the owner decides current group/handover access.
        if (!authority.canReadTask(actor, target.cohort().scope(), target.task(), scope.caseId())) throw hidden();
    }
    @Override public void requireRead(Authentication actor, Target target, CaseScope scope, Instant now) {
        if (!target.matches(scope)) throw hidden();
        identity.requirePermission(actor, scope, "t3:workspace:read", now);
        var visible = workspace(() -> available(runtime.readTask(actor, target.task().value().taskId())));
        if (!target.task().value().taskId().equals(visible.taskId())
                || visible.assignments().stream().noneMatch(a -> a.assignmentId().equals(target.assignment().assignmentId())
                    && a.caseId().equals(scope.caseId()) && a.studentUserId().equals(scope.studentUserId()))) throw hidden();
    }
    private static vn.utbridora.auth.operations.OperationFailure hidden() { return failure(404, "RESOURCE_NOT_FOUND"); }
}
