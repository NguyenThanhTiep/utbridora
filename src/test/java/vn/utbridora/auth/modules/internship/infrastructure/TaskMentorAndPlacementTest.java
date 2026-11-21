package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.modules.internship.api.CohortGroupDtos.GroupDto;
import vn.utbridora.auth.modules.internship.domain.Cohort;
import vn.utbridora.auth.modules.internship.domain.InternshipActivityPolicy;
import vn.utbridora.auth.modules.internship.domain.InternshipGroup;
import vn.utbridora.auth.modules.internship.domain.WorkspaceScope;
import vn.utbridora.auth.user.Role;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskMentorAndPlacementTest {
    private static final UUID AUTHORIZED_MENTOR_ID =
            UUID.fromString("00000000-0000-4000-8000-000000000101");
    private static final UUID UNAUTHORIZED_MENTOR_ID =
            UUID.fromString("00000000-0000-4000-8000-000000000102");
    private static final UUID ENTERPRISE_ID =
            UUID.fromString("00000000-0000-4000-8000-000000000201");
    private static final UUID SCHOOL_ID =
            UUID.fromString("00000000-0000-4000-8000-000000000202");
    private static final UUID TERM_ID =
            UUID.fromString("00000000-0000-4000-8000-000000000203");
    private static final UUID COHORT_ID =
            UUID.fromString("00000000-0000-4000-8000-000000000204");
    private static final UUID GROUP_ID =
            UUID.fromString("00000000-0000-4000-8000-000000000205");
    private static final UUID CASE_ID =
            UUID.fromString("00000000-0000-4000-8000-000000000206");
    private static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");

    @Test
    void testPlacementStatusBoundary() {
        assertFalse(InternshipActivityPolicy.hasRequiredStateAndStartTime(
                "IN_PROGRESS", "PENDING_APPROVAL", NOW.minusSeconds(1), NOW));

        assertTrue(InternshipActivityPolicy.hasRequiredStateAndStartTime(
                "IN_PROGRESS", "ACTIVE", NOW.minusSeconds(1), NOW));
    }

    @Test
    void testAuthorizedMentorAccess() {
        GroupDto groupDto = groupDto();
        assertTrue(groupDto.mentorUserIds().contains(AUTHORIZED_MENTOR_ID));

        WorkspaceScope scope = new WorkspaceScope(ENTERPRISE_ID, SCHOOL_ID, TERM_ID);
        Cohort cohort = new Cohort(COHORT_ID, scope, "C-1", "Cohort", 1);
        InternshipGroup group = new InternshipGroup(
                groupDto.groupId(), groupDto.cohortId(), groupDto.code(), groupDto.caseIds(),
                groupDto.mentorUserIds(), groupDto.version());
        Authentication actor = mentor(AUTHORIZED_MENTOR_ID);
        AuthorizationPolicy core = mock(AuthorizationPolicy.class);
        CoreWorkspaceAccess.BusinessAuthority authority =
                mock(CoreWorkspaceAccess.BusinessAuthority.class);
        CoreWorkspaceAccess access = new CoreWorkspaceAccess(core, authority);

        when(core.hasOrganization(actor, ENTERPRISE_ID.toString(), Role.ENTERPRISE,
                "internship:enterprise:task_write")).thenReturn(true);

        assertDoesNotThrow(() -> access.requireCreateTask(actor, cohort, group, CASE_ID));
        verify(authority).requireMentor(actor, scope, GROUP_ID, CASE_ID);
    }

    @Test
    void testUnauthorizedMentorAccessRejection() {
        GroupDto groupDto = groupDto();
        assertFalse(groupDto.mentorUserIds().contains(UNAUTHORIZED_MENTOR_ID));

        WorkspaceScope scope = new WorkspaceScope(ENTERPRISE_ID, SCHOOL_ID, TERM_ID);
        Cohort cohort = new Cohort(COHORT_ID, scope, "C-1", "Cohort", 1);
        InternshipGroup group = new InternshipGroup(
                groupDto.groupId(), groupDto.cohortId(), groupDto.code(), groupDto.caseIds(),
                groupDto.mentorUserIds(), groupDto.version());
        Authentication actor = mentor(UNAUTHORIZED_MENTOR_ID);
        AuthorizationPolicy core = mock(AuthorizationPolicy.class);
        CoreWorkspaceAccess.BusinessAuthority authority =
                mock(CoreWorkspaceAccess.BusinessAuthority.class);
        CoreWorkspaceAccess access = new CoreWorkspaceAccess(core, authority);

        when(core.hasOrganization(actor, ENTERPRISE_ID.toString(), Role.ENTERPRISE,
                "internship:enterprise:task_write")).thenReturn(true);
        doThrow(new SecurityException("Mentor is not assigned to this group"))
                .when(authority).requireMentor(actor, scope, GROUP_ID, CASE_ID);

        assertThrows(SecurityException.class,
                () -> access.requireCreateTask(actor, cohort, group, CASE_ID));
    }

    private static GroupDto groupDto() {
        return new GroupDto(GROUP_ID, COHORT_ID, "G-1", List.of(CASE_ID),
                List.of(AUTHORIZED_MENTOR_ID), 1L);
    }

    private static Authentication mentor(UUID mentorId) {
        return UsernamePasswordAuthenticationToken.authenticated(
                mentorId.toString(), null, List.of());
    }
}
