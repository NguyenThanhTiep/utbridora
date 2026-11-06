package vn.utbridora.auth.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
public interface UserRepository extends JpaRepository<UserAccount, String>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<UserAccount> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id=:id")
    Optional<UserAccount> findByIdForUpdate(@Param("id") String id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where lower(u.email)=lower(:email)")
    Optional<UserAccount> findByEmailForUpdate(@Param("email") String email);
}
