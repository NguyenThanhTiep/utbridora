
package vn.utbridora.auth.student;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import vn.utbridora.auth.student.dto.StudentRosterDto;
import vn.utbridora.auth.student.roster.StudentRoster;
import vn.utbridora.auth.student.roster.StudentRosterRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentRosterQueryServiceTest {

    private final StudentRosterRepository repository =
            mock(StudentRosterRepository.class);

    private final StudentSchoolAccessGuard guard =
            mock(StudentSchoolAccessGuard.class);

    private final StudentRosterQueryService service =
            new StudentRosterQueryService(repository, guard);

    @Test
    void shouldReturnOnlyStudentsOfRequestedSchool() {
        StudentRoster student = new StudentRoster();
        student.id = "roster-1";
        student.userId="student-1";
        student.schoolOrgId = "school-A";
        student.studentCode = "00123";
        student.fullName = "Nguyen Van A";
        student.email = "student@example.test";
        student.majorCode = "IT";
        student.termCode = "2026-1";

        when(repository.findBySchoolOrgId("school-A"))
                .thenReturn(List.of(student));
        when(guard.canReadUser("student-1","school-A")).thenReturn(true);

        List<StudentRosterDto> result =
                service.findBySchool("school-A");

        assertEquals(1, result.size());
        assertEquals("00123", result.get(0).studentCode());
        assertEquals("school-A", result.get(0).schoolOrgId());

        verify(guard).requireSchoolStaff("school-A");
        verify(repository).findBySchoolOrgId("school-A");
        verify(repository, never()).findAll();
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
}
