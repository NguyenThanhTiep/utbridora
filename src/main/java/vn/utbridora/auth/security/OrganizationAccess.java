package vn.utbridora.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.user.Role;

/** Compatibility facade for previously published T1 integration examples. */
@Component("organizationAccess")
public class OrganizationAccess {
    private final AuthorizationPolicy policy;
    public OrganizationAccess(AuthorizationPolicy policy){this.policy=policy;}
    public boolean enterpriseCanAccess(Authentication authentication,String organizationId){
        return policy.hasOrganization(authentication,organizationId,Role.ENTERPRISE,"cooperation:enterprise:create");
    }
}
