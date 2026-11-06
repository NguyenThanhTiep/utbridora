
package vn.utbridora.auth.student;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import vn.utbridora.auth.student.dto.EligibilityDto;
import vn.utbridora.auth.student.eligibility.StudentTermEligibility;
import vn.utbridora.auth.student.eligibility.StudentTermEligibilityRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentEligibilityQueryServiceTest {

    private final StudentTermEligibilityRepository repository =
            mock(StudentTermEligibilityRepository.class);

    private final StudentSchoolAccessGuard guard =
            mock(StudentSchoolAccessGuard.class);

    private final StudentEligibilityQueryService service =
            new StudentEligibilityQueryService(repository, guard);

    private StudentTermEligibility createEligibility() {
        StudentTermEligibility eligibility =
                new StudentTermEligibility();

        eligibility.id = "eligibility-1";
        eligibility.studentRosterId = "roster-1";
        eligibility.termCode = "2026-1";
        eligibility.schoolApproved = false;

        return eligibility;
    }

    @Test
    void shouldReadEligibilityForRequestedSchool() {
        StudentTermEligibility eligibility =
                createEligibility();

        when(repository.findAllForSchool("school-A"))
                .thenReturn(List.of(eligibility));

        List<EligibilityDto> result =
                service.findBySchool("school-A");

        assertEquals(1, result.size());
        assertEquals("roster-1",
                result.get(0).studentRosterId());
        assertFalse(result.get(0).schoolApproved());

        verify(guard).requireSchoolStaff("school-A");
        verify(repository).findAllForSchool("school-A");
        verify(repository, never()).findAll();
    }

    @Test
    void shouldReadSingleEligibilityWithinSchool() {
        StudentTermEligibility eligibility =
                createEligibility();

        when(repository.findForSchool(
                "school-A", "roster-1", "2026-1"
        )).thenReturn(Optional.of(eligibility));

        EligibilityDto result = service.findForSchool(
                "school-A", "roster-1", "2026-1"
        );

        assertEquals("eligibility-1", result.id());
        assertEquals("2026-1", result.termCode());
        assertFalse(result.schoolApproved());

        verify(guard).requireSchoolStaff("school-A");
        verify(repository).findForSchool(
                "school-A", "roster-1", "2026-1"
        );
    }

    @Test
    void shouldNotQueryDatabaseWhenAccessDenied() {
        doThrow(new AccessDeniedException("Access denied"))
                .when(guard).requireSchoolStaff("school-B");

        assertThrows(
                AccessDeniedException.class,
                () -> service.findBySchool("school-B")
        );

        verifyNoInteractions(repository);
    }

    @Test
    void shouldNotReadSingleEligibilityWhenAccessDenied() {
        doThrow(new AccessDeniedException("Access denied"))
                .when(guard).requireSchoolStaff("school-B");

        assertThrows(
                AccessDeniedException.class,
                () -> service.findForSchool(
                        "school-B", "roster-1", "2026-1"
                )
        );

        verifyNoInteractions(repository);
    }

    @Test
    void shouldRejectMissingEligibility() {
        when(repository.findForSchool(
                "school-A", "missing-roster", "2026-1"
        )).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.findForSchool(
                        "school-A", "missing-roster", "2026-1"
                )
        );

        verify(guard).requireSchoolStaff("school-A");
    }
}
