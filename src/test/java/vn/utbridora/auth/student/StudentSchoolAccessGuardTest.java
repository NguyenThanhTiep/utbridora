
package vn.utbridora.auth.student;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.authorization.ProfileVisibilityPort;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.user.Role;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentSchoolAccessGuardTest {

    private final AuthorizationPolicy memberships = mock(AuthorizationPolicy.class);
    private final ProfileVisibilityPort visibility=mock(ProfileVisibilityPort.class);

    private final StudentSchoolAccessGuard guard =
            new StudentSchoolAccessGuard(memberships,visibility);

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
                .hasOrganization(
                        any(), eq("school-A"), eq(Role.SCHOOL_STAFF), eq("student_profile:school:read")
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
                .hasOrganization(
                        any(), eq("school-B"), eq(Role.SCHOOL_STAFF), eq("student_profile:school:read")
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

    @Test void missingDelegationDoesNotExposeRosterPii() {
        assertFalse(guard.canReadUser("student","school"));
        assertFalse(guard.canReadUser(null,"school"));
        when(visibility.delegatedToSchool("student","school")).thenReturn(true);
        assertTrue(guard.canReadUser("student","school"));
        assertFalse(guard.canReadUser("student","other-school"));
    }
}
