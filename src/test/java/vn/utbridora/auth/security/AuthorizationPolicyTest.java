package vn.utbridora.auth.security;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.organization.*;
import vn.utbridora.auth.user.Role;
import vn.utbridora.auth.user.*;

class AuthorizationPolicyTest {
    private final MembershipRepository memberships=mock(MembershipRepository.class);
    private final OrganizationRepository organizations=mock(OrganizationRepository.class);
    private final PermissionCatalog permissions=mock(PermissionCatalog.class);
    private final ProfileVisibilityPort visibility=mock(ProfileVisibilityPort.class);
    private final UserRepository users=mock(UserRepository.class);
    private final AuthorizationPolicy policy=new AuthorizationPolicy(memberships,organizations,permissions,visibility,users);
    private Authentication auth(){
        UserAccount user=new UserAccount();user.id="user-A";user.status=AccountStatus.ACTIVE;
        when(users.findById("user-A")).thenReturn(Optional.of(user));
        var principal=new AppPrincipal("user-A","a@example.test",null,0,List.of());
        return UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of());
    }
    @Test void foreignEnterpriseIsAlwaysDeniedEvenWhenPermissionExists(){
        when(permissions.granted(Role.ENTERPRISE,"student_profile:shared:read")).thenReturn(true);
        assertFalse(policy.canReadStudentProfile(auth(),"student-B","enterprise-B"));
        verify(visibility,never()).explicitlySharedWithEnterprise(anyString(),anyString());
    }
    @Test void ownerCanSeeOwnProfile(){
        assertTrue(policy.canReadStudentProfile(auth(),"user-A",null));
    }
    @Test void inactiveOrgDeniedEvenWithMatchingMembership(){
        when(memberships.existsByUserIdAndOrganizationIdAndRoleAndActiveTrue("user-A","enterprise-A",Role.ENTERPRISE)).thenReturn(true);
        when(permissions.granted(Role.ENTERPRISE,"student_profile:shared:read")).thenReturn(true);
        Organization org=new Organization();org.type=Organization.Type.ENTERPRISE;org.status=Organization.Status.SUSPENDED;
        when(organizations.findById("enterprise-A")).thenReturn(Optional.of(org));
        assertFalse(policy.canReadStudentProfile(auth(),"student-B","enterprise-A"));
    }
    @Test void explicitShareStillRequiresScopeAndPermission(){
        when(memberships.existsByUserIdAndOrganizationIdAndRoleAndActiveTrue("user-A","enterprise-A",Role.ENTERPRISE)).thenReturn(true);
        when(permissions.granted(Role.ENTERPRISE,"student_profile:shared:read")).thenReturn(true);
        Organization org=new Organization();org.type=Organization.Type.ENTERPRISE;org.status=Organization.Status.VERIFIED;
        when(organizations.findById("enterprise-A")).thenReturn(Optional.of(org));
        when(visibility.explicitlySharedWithEnterprise("student-B","enterprise-A")).thenReturn(true);
        assertTrue(policy.canReadStudentProfile(auth(),"student-B","enterprise-A"));
    }
    @Test void defaultAdapterAlwaysDeniesOtherProfiles(){
        var adapter=new DenyAllProfileVisibility();
        assertFalse(adapter.explicitlySharedWithEnterprise("student-B","enterprise-A"));
        assertFalse(adapter.delegatedToSchool("student-B","school-A"));
    }
    @Test void staleOrSuspendedPrincipalCannotEvenUseSelfPermission(){
        Authentication authentication=auth();
        UserAccount user=new UserAccount();user.id="user-A";user.status=AccountStatus.ACTIVE;user.securityVersion=1;
        when(users.findById("user-A")).thenReturn(Optional.of(user));
        assertFalse(policy.isSelf(authentication,"user-A"));
        user.securityVersion=0;user.status=AccountStatus.SUSPENDED;
        assertFalse(policy.isSelf(authentication,"user-A"));
    }
    @Test void administratorDoesNotInheritBusinessAccess(){
        when(memberships.existsByUserIdAndOrganizationIdIsNullAndRoleAndActiveTrue("user-A",Role.ADMIN)).thenReturn(true);
        assertFalse(policy.hasGlobal(auth(),"cooperation:school:review"));
        assertFalse(policy.hasOrganization(auth(),"enterprise-A",Role.ADMIN,"student_profile:shared:read"));
    }
}
