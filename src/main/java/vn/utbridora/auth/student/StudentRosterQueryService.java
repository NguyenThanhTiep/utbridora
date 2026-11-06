
package vn.utbridora.auth.student;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.utbridora.auth.student.dto.StudentRosterDto;
import vn.utbridora.auth.student.roster.StudentRoster;
import vn.utbridora.auth.student.roster.StudentRosterRepository;

@Service
public class StudentRosterQueryService {

    private final StudentRosterRepository rosterRepository;
    private final StudentSchoolAccessGuard accessGuard;

    public StudentRosterQueryService(
            StudentRosterRepository rosterRepository,
            StudentSchoolAccessGuard accessGuard
    ) {
        this.rosterRepository = rosterRepository;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public List<StudentRosterDto> findBySchool(String schoolOrgId) {
        accessGuard.requireSchoolStaff(schoolOrgId);

        return rosterRepository.findBySchoolOrgId(schoolOrgId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private StudentRosterDto toDto(StudentRoster roster) {
        return new StudentRosterDto(
                roster.id,
                roster.schoolOrgId,
                roster.studentCode,
                roster.userId,
                roster.fullName,
                roster.email,
                roster.majorCode,
                roster.termCode,
                roster.createdAt
        );
    }
}
