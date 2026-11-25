package vn.utbridora.auth.modules.cooperation.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.utbridora.auth.modules.cooperation.domain.VerificationHistory;

public interface VerificationHistoryRepository extends JpaRepository<VerificationHistory, String> {
    List<VerificationHistory> findByVerificationIdOrderByChangedAtAsc(String verificationId);
    Optional<VerificationHistory> findFirstByVerificationIdAndSubmissionNoAndToStateOrderByChangedAtDescIdDesc(String verificationId,int submissionNo,String toState);
}
