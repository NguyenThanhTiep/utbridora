
package vn.utbridora.auth.student.roster;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StudentRosterRepository
        extends JpaRepository<StudentRoster, String> {

    Optional<StudentRoster> findBySchoolOrgIdAndStudentCode(
            String schoolOrgId,
            String studentCode
    );

    boolean existsBySchoolOrgIdAndStudentCode(
            String schoolOrgId,
            String studentCode
    );

    Optional<StudentRoster> findByIdAndSchoolOrgId(
            String id,
            String schoolOrgId
    );

    List<StudentRoster> findBySchoolOrgId(
            String schoolOrgId
    );
}
