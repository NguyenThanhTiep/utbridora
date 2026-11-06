package vn.utbridora.auth.user;

import org.springframework.data.jpa.repository.JpaRepository;
public interface AccountStatusHistoryRepository extends JpaRepository<AccountStatusHistory,String>{
    org.springframework.data.domain.Page<AccountStatusHistory> findByUserId(String userId,org.springframework.data.domain.Pageable pageable);
}
