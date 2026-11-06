
package vn.utbridora.auth.student;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import vn.utbridora.auth.organization.MembershipRepository;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.user.Role;

@Component
public class StudentSchoolAccessGuard {

    private final MembershipRepository membershipRepository;

    public StudentSchoolAccessGuard(
            MembershipRepository membershipRepository
    ) {
        this.membershipRepository = membershipRepository;
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
                membershipRepository
                        .existsByUserIdAndOrganizationIdAndRoleAndActiveTrue(
                                principal.userId(),
                                schoolOrgId,
                                Role.SCHOOL_STAFF
                        );

        if (!allowed) {
            throw new AccessDeniedException(
                    "Access denied for this school"
            );
        }
    }
}
