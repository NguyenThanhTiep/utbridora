package vn.utbridora.auth.authorization;

import java.util.List;
import org.springframework.stereotype.Service;
import vn.utbridora.auth.organization.*;
import vn.utbridora.auth.user.Role;

/** UI hint only. Never treat this endpoint's response as an authorization decision. */
@Service
public class EffectivePermissionService {
    public record PermissionGrant(Role role,String organizationId,List<String> permissions) {}
    private final MembershipRepository memberships;
    private final PermissionCatalog catalog;
    private final OrganizationRepository organizations;
    public EffectivePermissionService(MembershipRepository memberships,PermissionCatalog catalog,
                                      OrganizationRepository organizations){
        this.memberships=memberships;this.catalog=catalog;this.organizations=organizations;
    }
    public List<PermissionGrant> forUser(String userId) {
        return memberships.findByUserIdAndActiveTrue(userId).stream()
          .filter(m->m.role==Role.ADMIN && m.organizationId==null || m.role!=Role.ADMIN && validOrganization(m))
          .map(m->new PermissionGrant(m.role,m.organizationId,catalog.permissions(m.role)))
          .toList();
    }
    private boolean validOrganization(Membership member){
        if(member.organizationId==null)return false;
        return organizations.findById(member.organizationId)
          .map(o->o.status==Organization.Status.VERIFIED
            && (member.role==Role.ENTERPRISE && o.type==Organization.Type.ENTERPRISE
            || (member.role==Role.SCHOOL_STAFF || member.role==Role.STUDENT) && o.type==Organization.Type.UNIVERSITY))
          .orElse(false);
    }
}
