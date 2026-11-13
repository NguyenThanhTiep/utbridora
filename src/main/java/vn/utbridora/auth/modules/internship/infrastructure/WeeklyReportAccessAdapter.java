package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.modules.internship.application.WeeklyReportPorts.*;
import vn.utbridora.auth.modules.internship.domain.WorkspaceScope;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.modules.internship.infrastructure.WeeklyReportRuntimeChecks.*;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** Current identity/permission and report-specific assignment composition using existing authority.
 * Original Authentication is preserved (including securityVersion and selected request context).
 * No roles, membership, delegation, assignment or fake Task objects are created. No bean/HTTP route.
 */
public final class WeeklyReportAccessAdapter implements Authority, Assignments {
    private static final String READ = "t3:workspace:read";
    private final AuthorizationPolicy core;
    private final WorkspaceAuthority authority;
    private final WorkspaceRuntime workspace;
    public WeeklyReportAccessAdapter(AuthorizationPolicy core, WorkspaceAuthority authority, WorkspaceRuntime workspace) {
        this.core = available(core); this.authority = available(authority); this.workspace = available(workspace);
    }
    @Override public UUID currentUser(Authentication actor) {
        transaction();
        String user = workspace(() -> WorkspaceAuthority.actor(actor));
        if (!core.isSelf(actor, user)) throw failure(401, "SESSION_EXPIRED");
        try { return UUID.fromString(user); }
        catch (IllegalArgumentException invalidProvider) { throw unavailable(); }
    }
    @Override public void requirePermission(Authentication actor, CaseScope scope, String permission, Instant now) {
        UUID user = currentUser(actor);
        boolean allowed = switch (permission) {
            case "internship:student:submission_write" -> user.equals(scope.studentUserId())
                    && core.holdOrganization(actor, scope.schoolOrgId().toString(), Role.STUDENT, permission);
            case "internship:enterprise:task_write" ->
                    core.holdOrganization(actor, scope.enterpriseOrgId().toString(), Role.ENTERPRISE, permission);
            case READ -> user.equals(scope.studentUserId())
                    ? core.holdOrganization(actor, scope.schoolOrgId().toString(), Role.STUDENT, permission)
                    : core.holdOrganization(actor, scope.schoolOrgId().toString(), Role.SCHOOL_STAFF, permission)
                        || core.holdOrganization(actor, scope.enterpriseOrgId().toString(), Role.ENTERPRISE, permission);
            default -> false;
        };
        if (!allowed) throw failure(404, "RESOURCE_NOT_FOUND");
    }
    @Override public boolean isCurrentMentor(Authentication actor, CaseScope scope, Instant now) {
        UUID user = currentUser(actor);
        var selected = workspace(() -> available(authority.selected(actor)));
        if (!user.toString().equals(selected.userId()) || !"MENTOR".equals(selected.kind())
                || !selected.active() || now.isBefore(selected.validFrom()) || !now.isBefore(selected.validUntil())
                || selected.scope().groupId() == null
                || !WorkspaceAuthority.same(selected.scope(), workspaceScope(scope))
                || selected.scope().caseId() != null && !selected.scope().caseId().equals(scope.caseId().toString())) return false;
        workspace(() -> {
            authority.requireMentor(actor, workspaceScope(scope), UUID.fromString(selected.scope().groupId()), scope.caseId());
            return true;
        });
        return true;
    }
    @Override public boolean canRead(Authentication actor, CaseScope scope, Instant now) {
        currentUser(actor);
        // Reuse existing self/SD/HR/Mentor Case read gate, not canReadTask with a fabricated Task.
        var ref = workspace(() -> available(workspace.caseRef(actor, scope.caseId())));
        if (!ref.caseId().equals(scope.caseId()) || !ref.placementId().equals(scope.placementId())
                || !ref.studentUserId().equals(scope.studentUserId()) || !ref.schoolOrgId().equals(scope.schoolOrgId())
                || !ref.enterpriseOrgId().equals(scope.enterpriseOrgId()) || !ref.termId().equals(scope.termId())) throw unavailable();
        if (ref.version() != scope.caseVersion()) throw failure(409, "VERSION_CONFLICT");
        return true;
    }
    private static WorkspaceScope workspaceScope(CaseScope scope) {
        return new WorkspaceScope(scope.enterpriseOrgId(), scope.schoolOrgId(), scope.termId());
    }
}
