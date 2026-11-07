package vn.utbridora.auth.modules.student.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.utbridora.auth.modules.student.domain.StudentProfile;

public interface StudentProfileRepository extends JpaRepository<StudentProfile,String> {
    Optional<StudentProfile> findByStudentUserIdAndTermId(String studentUserId,String termId);
    /** Serializes writers of one profile so the version check and the update commit together. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from StudentProfile p where p.studentUserId = :student and p.termId = :term")
    Optional<StudentProfile> lockByStudentAndTerm(@Param("student") String studentUserId,@Param("term") String termId);
}
