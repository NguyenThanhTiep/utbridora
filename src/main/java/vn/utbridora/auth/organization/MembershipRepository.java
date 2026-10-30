package vn.utbridora.auth.organization;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.utbridora.auth.user.Role;

public interface MembershipRepository extends JpaRepository<Membership,String> {
    List<Membership> findByUserIdAndActiveTrue(String userId);
    boolean existsByUserIdAndOrganizationIdIsNullAndRoleAndActiveTrue(String userId, Role role);
    java.util.Optional<Membership> findByUserIdAndOrganizationIdAndRole(String userId,String organizationId,Role role);
    java.util.Optional<Membership> findByUserIdAndOrganizationIdIsNullAndRole(String userId,Role role);
    @org.springframework.data.jpa.repository.Query("select count(distinct m.userId) from Membership m, UserAccount u where m.userId = u.id and m.organizationId is null and m.role = :role and m.active = true and u.status = :status")
    long countActiveGlobalRole(@org.springframework.data.repository.query.Param("role") Role role,
         @org.springframework.data.repository.query.Param("status") vn.utbridora.auth.user.AccountStatus status);
    boolean existsByUserIdAndOrganizationIdAndRoleAndActiveTrue(String userId, String organizationId, Role role);
}
