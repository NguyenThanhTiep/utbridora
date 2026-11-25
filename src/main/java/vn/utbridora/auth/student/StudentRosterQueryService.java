package vn.utbridora.auth.student;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.student.dto.StudentRosterDto;

@Service
public class StudentRosterQueryService {
    private final SchoolRosterVisibilityAdapter visibility;
    private final StudentSchoolAccessGuard guard;
    public StudentRosterQueryService(SchoolRosterVisibilityAdapter visibility,StudentSchoolAccessGuard guard) {
        this.visibility=visibility;this.guard=guard;
    }
    // Current authority and rows are held FOR SHARE through projection; MySQL needs a normal transaction.
    @Transactional
    public List<StudentRosterDto> findBySchool(String schoolOrgId) {
        guard.requireSchoolStaff(schoolOrgId);
        return visibility.read(schoolOrgId);
    }
}
