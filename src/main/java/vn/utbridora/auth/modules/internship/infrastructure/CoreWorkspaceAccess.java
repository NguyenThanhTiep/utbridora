package vn.utbridora.auth.modules.internship.infrastructure;

import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.modules.internship.application.WorkspacePorts.Access;
import vn.utbridora.auth.modules.internship.domain.*;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException.Kind.*;

/** DEV06 adapter using existing Core API plus authoritative business checks.
 * Registered by WorkspaceConfiguration with real assignment/Term providers and scoped permissions.
 * ENTERPRISE membership, ADMIN or a client authority string never substitutes for assignment.
 */
public final class CoreWorkspaceAccess implements Access {
    private static final String MANAGE = "t3:cohorts:manage";
    private static final String READ = "t3:workspace:read";
    private static final String TASK_WRITE = "internship:enterprise:task_write";
    private final AuthorizationPolicy core;
    private final BusinessAuthority authority;
    public CoreWorkspaceAccess(AuthorizationPolicy core, BusinessAuthority authority) {
        this.core = Objects.requireNonNull(core);
        this.authority = Objects.requireNonNull(authority);
    }
    /** Consumer-side boundary only; implement against owner ports, never client claims.
     * Every check must re-read validFrom <= now < validUntil, active/revoke, parent
     * authority and scope. Read checks include old-task handover rules from CP-WS1.
     */
    public interface BusinessAuthority {
        WorkspaceScope resolveCreationScope(Authentication actor, UUID termId);
        void requireHr(Authentication actor, WorkspaceScope scope, UUID groupId);
        void requireMentor(Authentication actor, WorkspaceScope scope, UUID groupId, UUID caseId);
        boolean canReadTask(Authentication actor, WorkspaceScope scope, InternshipTask task, UUID caseId);
    }
    public static BusinessAuthority unavailableAuthority() {
        return new BusinessAuthority() {
            public WorkspaceScope resolveCreationScope(Authentication actor, UUID termId) { throw unavailable(); }
            public void requireHr(Authentication actor, WorkspaceScope scope, UUID groupId) { throw unavailable(); }
            public void requireMentor(Authentication actor, WorkspaceScope scope, UUID groupId, UUID caseId) { throw unavailable(); }
            public boolean canReadTask(Authentication actor, WorkspaceScope scope, InternshipTask task, UUID caseId) { return false; }
        };
    }
    @Override public WorkspaceScope cohortCreationScope(Authentication actor, UUID termId) {
        var scope = authority.resolveCreationScope(actor, Objects.requireNonNull(termId));
        if (scope == null) throw unavailable();
        if (!scope.termId().equals(termId)) throw new WorkspaceRuleException(SCOPE_MISMATCH);
        requireEnterprise(actor, scope, MANAGE);
        authority.requireHr(actor, scope, null);
        return scope;
    }
    @Override public void requireManageGroup(Authentication actor, Cohort cohort, InternshipGroup group) {
        requireGroup(cohort, group);
        requireEnterprise(actor, cohort.scope(), MANAGE);
        authority.requireHr(actor, cohort.scope(), group.id());
    }
    @Override public void requireCreateTask(Authentication actor, Cohort cohort, InternshipGroup group, UUID caseId) {
        requireGroup(cohort, group);
        requireEnterprise(actor, cohort.scope(), TASK_WRITE);
        authority.requireMentor(actor, cohort.scope(), group.id(), Objects.requireNonNull(caseId));
    }
    @Override public boolean canReadAssignment(Authentication actor, Cohort cohort, InternshipTask task, UUID caseId) {
        var assignment = task.value().assignments().stream().filter(a -> a.caseId().equals(caseId)).findFirst();
        if (assignment.isEmpty()) return false;
        var scope = cohort.scope();
        // Owner is read from stored Task assignment, never a caller-provided student ID.
        if (core.isSelf(actor, assignment.get().studentUserId().toString())
                && core.hasOrganization(actor, scope.schoolOrgId().toString(), Role.STUDENT, READ)) return true;
        boolean enterprise = core.hasOrganization(actor, scope.enterpriseOrgId().toString(), Role.ENTERPRISE, READ);
        boolean school = core.hasOrganization(actor, scope.schoolOrgId().toString(), Role.SCHOOL_STAFF, READ);
        return (enterprise || school) && authority.canReadTask(actor, scope, task, caseId);
    }
    private void requireEnterprise(Authentication actor, WorkspaceScope scope, String permission) {
        if (!core.hasOrganization(actor, scope.enterpriseOrgId().toString(), Role.ENTERPRISE, permission)) {
            throw new WorkspaceRuleException(DENIED);
        }
    }
    private static void requireGroup(Cohort cohort, InternshipGroup group) {
        if (!cohort.id().equals(group.cohortId())) throw new WorkspaceRuleException(SCOPE_MISMATCH);
    }
    private static WorkspaceRuleException unavailable() { return new WorkspaceRuleException(UNAVAILABLE); }
}
