package vn.utbridora.auth.organization;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OrganizationRepository extends JpaRepository<Organization,String>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Organization> {}
