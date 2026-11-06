
package vn.utbridora.auth.student;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import vn.utbridora.auth.organization.MembershipRepository;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.user.Role;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentSchoolAccessGuardTest {

    private final MembershipRepository memberships =
            mock(MembershipRepository.class);

    private final StudentSchoolAccessGuard guard =
            new StudentSchoolAccessGuard(memberships);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String userId) {
        AppPrincipal principal = new AppPrincipal(
                userId,
                "staff@example.test",
                null,
                0L,
                List.of(new SimpleGrantedAuthority("ROLE_SCHOOL_STAFF"))
        );

        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        principal.getAuthorities()
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }

    @Test
    void shouldAllowActiveStaffOfSameSchool() {
        loginAs("user-1");

        when(memberships
                .existsByUserIdAndOrganizationIdAndRoleAndActiveTrue(
                        "user-1", "school-A", Role.SCHOOL_STAFF
                ))
                .thenReturn(true);

        assertDoesNotThrow(() ->
                guard.requireSchoolStaff("school-A")
        );
    }

    @Test
    void shouldDenyAccessToAnotherSchool() {
        loginAs("user-1");

        when(memberships
                .existsByUserIdAndOrganizationIdAndRoleAndActiveTrue(
                        "user-1", "school-B", Role.SCHOOL_STAFF
                ))
                .thenReturn(false);

        assertThrows(
                AccessDeniedException.class,
                () -> guard.requireSchoolStaff("school-B")
        );
    }

    @Test
    void shouldDenyInactiveMembership() {
        loginAs("user-1");

        assertThrows(
                AccessDeniedException.class,
                () -> guard.requireSchoolStaff("school-A")
        );
    }

    @Test
    void shouldDenyUnauthenticatedUser() {
        SecurityContextHolder.clearContext();

        assertThrows(
                AccessDeniedException.class,
                () -> guard.requireSchoolStaff("school-A")
        );

        verifyNoInteractions(memberships);
    }

    @Test
    void shouldDenyMissingSchoolId() {
        loginAs("user-1");

        assertThrows(
                AccessDeniedException.class,
                () -> guard.requireSchoolStaff(" ")
        );

        verifyNoInteractions(memberships);
    }
}
