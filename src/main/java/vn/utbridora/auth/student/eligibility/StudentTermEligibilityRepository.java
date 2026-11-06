
package vn.utbridora.auth.student.eligibility;

import vn.utbridora.auth.student.roster.StudentRoster;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentTermEligibilityRepository
        extends JpaRepository<StudentTermEligibility, String> {

    Optional<StudentTermEligibility> findByStudentRosterIdAndTermCode(
            String studentRosterId,
            String termCode
    );

    boolean existsByStudentRosterIdAndTermCode(
            String studentRosterId,
            String termCode
    );

    @Query("""
        SELECT e
        FROM StudentTermEligibility e, StudentRoster r
        WHERE e.studentRosterId = r.id
          AND r.schoolOrgId = :schoolOrgId
          AND e.studentRosterId = :studentRosterId
          AND e.termCode = :termCode
        """)
    Optional<StudentTermEligibility> findForSchool(
            @Param("schoolOrgId") String schoolOrgId,
            @Param("studentRosterId") String studentRosterId,
            @Param("termCode") String termCode
    );

    @Query("""
        SELECT e
        FROM StudentTermEligibility e, StudentRoster r
        WHERE e.studentRosterId = r.id
          AND r.schoolOrgId = :schoolOrgId
        """)
    List<StudentTermEligibility> findAllForSchool(
            @Param("schoolOrgId") String schoolOrgId
    );
}
