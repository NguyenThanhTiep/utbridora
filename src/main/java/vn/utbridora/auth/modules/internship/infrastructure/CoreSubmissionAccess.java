package vn.utbridora.auth.modules.internship.infrastructure;

import java.util.Objects;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.modules.internship.application.SubmissionPorts.*;
import vn.utbridora.auth.modules.internship.application.WorkspacePorts;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** Uses current Core identity/permission locks and the existing Task viewer/handover boundary.
 * No authority is derived from a browser org, role string, or the Submission artifact itself.
 */
public final class CoreSubmissionAccess implements Access {
    private final AuthorizationPolicy core;
    private final WorkspacePorts.Access workspace;

    public CoreSubmissionAccess(AuthorizationPolicy core, WorkspacePorts.Access workspace) {
        this.core = Objects.requireNonNull(core);
        this.workspace = Objects.requireNonNull(workspace);
    }

    @Override public void requireSubmit(Authentication actor, AssignmentContext target) {
        actor(actor);
        if (!core.isSelf(actor, target.assignment().studentUserId().toString())
                || !core.holdOrganization(actor, target.cohort().scope().schoolOrgId().toString(),
                    Role.STUDENT, "internship:student:submission_write")) throw failure(404, "RESOURCE_NOT_FOUND");
    }

    @Override public void requireRead(Authentication actor, AssignmentContext target) {
        String user = actor(actor);
        if (!core.isSelf(actor, user)) throw failure(404, "RESOURCE_NOT_FOUND");
        var scope = target.cohort().scope();
        if (user.equals(target.assignment().studentUserId().toString())) {
            if (!core.holdOrganization(actor, scope.schoolOrgId().toString(), Role.STUDENT, "t3:workspace:read")) {
                throw failure(404, "RESOURCE_NOT_FOUND");
            }
            return;
        }
        boolean scoped = core.holdOrganization(actor, scope.enterpriseOrgId().toString(), Role.ENTERPRISE, "t3:workspace:read")
                || core.holdOrganization(actor, scope.schoolOrgId().toString(), Role.SCHOOL_STAFF, "t3:workspace:read");
        if (!scoped || !workspace.canReadAssignment(actor, target.cohort(), target.task(), target.assignment().caseId())) {
            throw failure(404, "RESOURCE_NOT_FOUND");
        }
    }

    @Override public void requireReview(Authentication actor, AssignmentContext target) {
        // M3A has a port contract only. No runtime Review grant until the owner supplies audit support.
        throw failure(503, "DEPENDENCY_UNAVAILABLE");
    }
}
