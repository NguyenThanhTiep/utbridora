package vn.utbridora.auth.student;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.utbridora.auth.student.dto.StudentRosterDto;

class StudentRosterControllerTest {
    @Test
    void filtersByTermCodeWhenProvidedWithoutChangingQueryGuard() {
        StudentRosterQueryService service = mock(StudentRosterQueryService.class);
        StudentRosterController controller = new StudentRosterController(service);
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        StudentRosterDto hk1 = new StudentRosterDto("r1", "school-1", "22520001", "u1", "Nguyen A", "a@demo.test", "SE", "HK1-2026", now);
        StudentRosterDto hk2 = new StudentRosterDto("r2", "school-1", "22520002", "u2", "Tran B", "b@demo.test", "CS", "HK2-2026", now);
        when(service.findBySchool("school-1")).thenReturn(List.of(hk1, hk2));

        assertEquals(List.of(hk1, hk2), controller.list("school-1", null));
        assertEquals(List.of(hk2), controller.list("school-1", " HK2-2026 "));
        verify(service, times(2)).findBySchool("school-1");
    }
}
