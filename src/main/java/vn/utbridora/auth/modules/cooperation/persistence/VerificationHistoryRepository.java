package vn.utbridora.auth.modules.cooperation.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.utbridora.auth.modules.cooperation.domain.VerificationHistory;

public interface VerificationHistoryRepository extends JpaRepository<VerificationHistory, String> {
    List<VerificationHistory> findByVerificationIdOrderByChangedAtAsc(String verificationId);
}
