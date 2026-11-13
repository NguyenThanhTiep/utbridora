package vn.utbridora.auth.modules.cooperation.application;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.operations.OperationFailure;
import vn.utbridora.auth.organization.*;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.user.Role;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PortalOrganizationServiceTest {
    final AuthorizationPolicy access=mock(AuthorizationPolicy.class);
    final EffectivePermissionService permissions=mock(EffectivePermissionService.class);
    final OrganizationRepository organizations=mock(OrganizationRepository.class);
    final PortalOrganizationService service=new PortalOrganizationService(access,permissions,organizations);
    final Authentication actor=UsernamePasswordAuthenticationToken.authenticated(new AppPrincipal("actor","user@example.test",null,1,List.of()),null,List.of());
    @BeforeEach void active() {when(access.isSelf(actor,"actor")).thenReturn(true);}
    Organization org(String id,String name,Organization.Type type,Organization.Status status) {
        var row=new Organization();row.id=id;row.name=name;row.type=type;row.status=status;return row;
    }
    @Test void rejectsAnonymousAndRevokedSessionsBeforeReadingNames() {
        assertEquals(401,assertThrows(OperationFailure.class,()->service.list(null)).status);
        when(access.isSelf(actor,"actor")).thenReturn(false);
        assertEquals(401,assertThrows(OperationFailure.class,()->service.list(actor)).status);
        verifyNoInteractions(permissions,organizations);
    }
    @Test void returnsOnlyCurrentPortalMembershipNamesAndRejectsWrongTypesOrForeignRows() {
        when(permissions.forUser("actor")).thenReturn(List.of(
            new EffectivePermissionService.PermissionGrant(Role.ENTERPRISE,"enterprise",List.of("cooperation:enterprise:create")),
            new EffectivePermissionService.PermissionGrant(Role.SCHOOL_STAFF,"school",List.of()),
            new EffectivePermissionService.PermissionGrant(Role.ENTERPRISE,"wrong",List.of("cooperation:enterprise:create"))));
        when(organizations.findAllById(any())).thenReturn(List.of(
            org("enterprise","FPT Software",Organization.Type.ENTERPRISE,Organization.Status.VERIFIED),
            org("school","Trường Đại học GTVT",Organization.Type.UNIVERSITY,Organization.Status.VERIFIED),
            org("foreign","Foreign",Organization.Type.ENTERPRISE,Organization.Status.VERIFIED),
            org("wrong","Wrong type",Organization.Type.UNIVERSITY,Organization.Status.VERIFIED)));
        var result=service.list(actor);
        assertEquals(Set.of("enterprise","school"),result.stream().map(PortalOrganizationService.OrganizationOption::id).collect(java.util.stream.Collectors.toSet()));
        assertEquals("FPT Software",result.getFirst().name());
    }
    @Test void globalAdminAndStudentRolesDoNotListOtherOrganizations() {
        when(permissions.forUser("actor")).thenReturn(List.of(new EffectivePermissionService.PermissionGrant(Role.ADMIN,null,List.of()),
            new EffectivePermissionService.PermissionGrant(Role.STUDENT,"school",List.of()),new EffectivePermissionService.PermissionGrant(Role.ENTERPRISE,"enterprise",List.of())));
        assertTrue(service.list(actor).isEmpty());verifyNoInteractions(organizations);
    }
    @Test void doesNotReturnSuspendedOrganizationNames() {
        when(permissions.forUser("actor")).thenReturn(List.of(new EffectivePermissionService.PermissionGrant(Role.SCHOOL_STAFF,"school",List.of())));
        when(organizations.findAllById(any())).thenReturn(List.of(org("school","Suspended",Organization.Type.UNIVERSITY,Organization.Status.SUSPENDED)));
        assertTrue(service.list(actor).isEmpty());
    }
}
