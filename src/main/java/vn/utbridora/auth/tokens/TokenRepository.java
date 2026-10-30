package vn.utbridora.auth.tokens;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.utbridora.auth.tokens.OneTimeToken.Purpose;

public interface TokenRepository extends JpaRepository<OneTimeToken,String> {
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically=true)
    @Query("update OneTimeToken t set t.consumedAt=:now where t.id=:id and t.consumedAt is null and t.expiresAt>:now")
    int consumeIfUsable(@Param("id") String id,@Param("now") java.time.Instant now);
    Optional<OneTimeToken> findByTokenHashAndPurpose(String tokenHash, Purpose purpose);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from OneTimeToken t where t.tokenHash = :hash and t.purpose = :purpose")
    Optional<OneTimeToken> findForUpdate(@Param("hash") String hash,@Param("purpose") Purpose purpose);
    List<OneTimeToken> findByUserIdAndPurposeAndConsumedAtIsNull(String userId, Purpose purpose);
}
