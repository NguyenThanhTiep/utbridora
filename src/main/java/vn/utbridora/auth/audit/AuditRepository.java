package vn.utbridora.auth.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
public interface AuditRepository extends JpaRepository<AuditLog,String>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<AuditLog> {
    Page<AuditLog> findByAction(String action,Pageable pageable);
    Page<AuditLog> findByActorId(String actorId,Pageable pageable);
    Page<AuditLog> findByActionAndActorId(String action,String actorId,Pageable pageable);
}
