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
import vn.utbridora.auth.modules.cooperation.domain.EnterpriseVerification;
import vn.utbridora.auth.modules.cooperation.domain.VerificationState;

public interface VerificationRepository extends JpaRepository<EnterpriseVerification, String> {

    Page<EnterpriseVerification> findByEnterpriseOrgId(String enterpriseOrgId, Pageable pageable);

    Page<EnterpriseVerification> findBySchoolOrgId(String schoolOrgId, Pageable pageable);

    Page<EnterpriseVerification> findBySchoolOrgIdAndState(String schoolOrgId, VerificationState state, Pageable pageable);

    /** Check whether an open (non-terminal) attempt already exists for this enterprise+school pair. */
    boolean existsByEnterpriseOrgIdAndSchoolOrgIdAndStateIn(
            String enterpriseOrgId, String schoolOrgId, List<VerificationState> openStates);

    /** Find the current open attempt (should be at most one). */
    Optional<EnterpriseVerification> findByEnterpriseOrgIdAndSchoolOrgIdAndStateIn(
            String enterpriseOrgId, String schoolOrgId, List<VerificationState> openStates);

    /** Find the approved record (used for guard checks). */
    Optional<EnterpriseVerification> findByEnterpriseOrgIdAndSchoolOrgIdAndState(
            String enterpriseOrgId, String schoolOrgId, VerificationState state);

    /** Pessimistic write lock for state transitions. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from EnterpriseVerification v where v.id = :id")
    Optional<EnterpriseVerification> findByIdForUpdate(@Param("id") String id);
}
