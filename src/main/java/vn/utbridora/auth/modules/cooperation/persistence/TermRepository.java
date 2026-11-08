package vn.utbridora.auth.modules.cooperation.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.utbridora.auth.modules.cooperation.domain.Term;
import vn.utbridora.auth.modules.cooperation.domain.TermStatus;

public interface TermRepository extends JpaRepository<Term, String> {

    Page<Term> findBySchoolOrgId(String schoolOrgId, Pageable pageable);

    Page<Term> findBySchoolOrgIdAndStatus(String schoolOrgId, TermStatus status, Pageable pageable);

    boolean existsBySchoolOrgIdAndTermCode(String schoolOrgId, String termCode);

    Optional<Term> findBySchoolOrgIdAndTermCode(String schoolOrgId, String termCode);

    List<Term> findBySchoolOrgIdAndStatus(String schoolOrgId, TermStatus status);

    /** Pessimistic write lock for status transitions. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Term t where t.id = :id")
    Optional<Term> findByIdForUpdate(@Param("id") String id);
}
