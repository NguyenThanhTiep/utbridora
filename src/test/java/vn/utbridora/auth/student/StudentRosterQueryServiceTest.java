package vn.utbridora.auth.student;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import vn.utbridora.auth.student.dto.StudentRosterDto;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StudentRosterQueryServiceTest {
    final SchoolRosterVisibilityAdapter visibility=mock(SchoolRosterVisibilityAdapter.class);
    final StudentSchoolAccessGuard guard=mock(StudentSchoolAccessGuard.class);
    final StudentRosterQueryService service=new StudentRosterQueryService(visibility,guard);
    @Test void preservesAdapterMaskWithoutFillingFieldsFromUnmaskedRoster() {
        var masked=new StudentRosterDto("r","school",null,"user",null,null,"IT","CURRENT",null);
        when(visibility.read("school")).thenReturn(List.of(masked));
        assertEquals(List.of(masked),service.findBySchool("school"));verify(guard).requireSchoolStaff("school");
    }
    @Test void deniedMembershipNeverCallsAdapter() {
        doThrow(new AccessDeniedException("denied")).when(guard).requireSchoolStaff("foreign");
        assertThrows(AccessDeniedException.class,()->service.findBySchool("foreign"));verifyNoInteractions(visibility);
    }
}
