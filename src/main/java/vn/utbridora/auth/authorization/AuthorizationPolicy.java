package vn.utbridora.auth.authorization;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import vn.utbridora.auth.organization.*;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.user.*;

/** Never authorize by role alone for organization-scoped resources. */
@Component("authorizationPolicy")
public class AuthorizationPolicy {
    private final MembershipRepository memberships;
    private final OrganizationRepository organizations;
    private final PermissionCatalog catalog;
    private final ProfileVisibilityPort profileVisibility;
    private final UserRepository users;
    public AuthorizationPolicy(MembershipRepository memberships,OrganizationRepository organizations,
                               PermissionCatalog catalog,ProfileVisibilityPort profileVisibility, UserRepository users) {
        this.memberships=memberships;this.organizations=organizations;
        this.catalog=catalog;this.profileVisibility=profileVisibility;
        this.users=users;
    }
    private String userId(Authentication auth) {
        if(auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AppPrincipal p)) return null;
        return users.findById(p.userId()).filter(u -> u.status==AccountStatus.ACTIVE
            && u.securityVersion==p.securityVersion()).map(u -> u.id).orElse(null);
    }
    public boolean isSelf(Authentication auth,String userId) {
        String actor=userId(auth); return actor!=null && actor.equals(userId);
    }
    public boolean hasGlobal(Authentication auth,String permission) {
        String actor=userId(auth);
        return actor!=null
          && memberships.existsByUserIdAndOrganizationIdIsNullAndRoleAndActiveTrue(actor,Role.ADMIN)
          && catalog.granted(Role.ADMIN,permission);
    }
    public boolean hasOrganization(Authentication auth,String organizationId,Role role,String permission) {
        String actor=userId(auth);
        if(actor==null || organizationId==null || organizationId.isBlank() || role==null || role==Role.ADMIN) return false;
        if(!memberships.existsByUserIdAndOrganizationIdAndRoleAndActiveTrue(actor,organizationId,role)
            || !catalog.granted(role,permission)) return false;
        return organizations.findById(organizationId)
           .map(o->o.status==Organization.Status.VERIFIED
              && ((role==Role.ENTERPRISE && o.type==Organization.Type.ENTERPRISE)
              || ((role==Role.SCHOOL_STAFF || role==Role.STUDENT) && o.type==Organization.Type.UNIVERSITY)))
           .orElse(false);
    }
    public boolean canReadStudentProfile(Authentication auth,String studentId,String organizationId) {
        if(studentId==null || studentId.isBlank()) return false;
        if(isSelf(auth,studentId)) return true;
        return hasOrganization(auth,organizationId,Role.ENTERPRISE,"student_profile:shared:read")
              && profileVisibility.explicitlySharedWithEnterprise(studentId,organizationId)
          || hasOrganization(auth,organizationId,Role.SCHOOL_STAFF,"student_profile:school:read")
              && profileVisibility.delegatedToSchool(studentId,organizationId);
    }
    /** Use when a legacy body carries organizationId. New bodies should omit redundant scope. */
    public boolean hasOrganizationForRequest(Authentication auth,String pathOrganizationId,String bodyOrganizationId,
                                             Role role,String permission) {
        return pathOrganizationId!=null && pathOrganizationId.equals(bodyOrganizationId)
            && hasOrganization(auth,pathOrganizationId,role,permission);
    }
}
