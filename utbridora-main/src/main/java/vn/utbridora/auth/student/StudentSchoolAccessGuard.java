
package vn.utbridora.auth.student;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.authorization.ProfileVisibilityPort;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.user.Role;

@Component
public class StudentSchoolAccessGuard {

    private final AuthorizationPolicy policy;
    private final ProfileVisibilityPort visibility;

    public StudentSchoolAccessGuard(
            AuthorizationPolicy policy, ProfileVisibilityPort visibility
    ) {
        this.policy=policy;this.visibility=visibility;
    }

    public void requireSchoolStaff(String schoolOrgId) {
        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                        instanceof AppPrincipal principal)) {
            throw new AccessDeniedException(
                    "Authentication required"
            );
        }

        if (schoolOrgId == null || schoolOrgId.isBlank()) {
            throw new AccessDeniedException(
                    "School organization is required"
            );
        }

        boolean allowed =
                policy.hasOrganization(
                                authentication,
                                schoolOrgId,
                                Role.SCHOOL_STAFF,"student_profile:school:read"
                        );

        if (!allowed) {
            throw new AccessDeniedException(
                    "Access denied for this school"
            );
        }
    }

    /** Membership alone does not grant access to roster PII. Missing delegation fails closed. */
    public boolean canReadUser(String userId,String schoolOrgId) {
        return userId!=null && visibility.delegatedToSchool(userId,schoolOrgId);
    }
}
