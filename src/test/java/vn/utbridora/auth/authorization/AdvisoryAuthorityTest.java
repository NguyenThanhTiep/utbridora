package vn.utbridora.auth.authorization;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import vn.utbridora.auth.operations.OperationFailure;
import vn.utbridora.auth.security.AppPrincipal;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

class AdvisoryAuthorityTest {
    final SchoolDelegationAccess scope=mock(SchoolDelegationAccess.class);final AuthorizationPolicy core=mock(AuthorizationPolicy.class);final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final String id="a17e2026-1121-4000-8000-000000000001";
    final AdvisoryAuthority service=new AdvisoryAuthority(scope,core,jdbc);
    org.springframework.security.core.Authentication auth;
    @BeforeEach void setup() {var p=new AppPrincipal(id,"synthetic@example.test",null,1,List.of());auth=UsernamePasswordAuthenticationToken.authenticated(p,null,List.of());when(core.holdOrganization(eq(auth),eq(id),eq(vn.utbridora.auth.user.Role.SCHOOL_STAFF),anyString())).thenReturn(true);when(scope.profileFields(auth,id,id)).thenReturn(Set.of("skills","projects"));}
    @Test void scoreRequiresBothUnderlyingFields() {when(scope.profileFields(auth,id,id)).thenReturn(Set.of("skills"));assertEquals(404,assertThrows(OperationFailure.class,()->service.require(auth,id,id,false)).status);}
    @Test void roleOrOtherPermissionDoesNotReplaceAdvisoryRead() {doThrow(new OperationFailure(404,"RESOURCE_NOT_FOUND","denied",null)).when(scope).require(auth,id,id,AdvisoryAuthority.READ);assertThrows(OperationFailure.class,()->service.require(auth,id,id,false));}
    @Test void oldSecurityVersionCannotResumeJob() {when(core.currentSubject(id)).thenReturn(Optional.of(auth));var stamp=new AdvisoryAuthority.Stamp(id,0,id,id,List.of(),List.of(),Instant.now().plusSeconds(100));assertTrue(service.current(stamp).isEmpty());verifyNoInteractions(jdbc);}
    @Test void exactParentVersionChangeRevokesEvenWhenRoleSurvives() {when(core.currentSubject(id)).thenReturn(Optional.of(auth));when(jdbc.queryForList(anyString(),eq(Long.class),eq(id))).thenReturn(List.of(2L));var stamp=new AdvisoryAuthority.Stamp(id,1,id,id,List.of(),List.of(new AdvisoryAuthority.Ancestor(id,1,Instant.now().plusSeconds(100))),Instant.now().plusSeconds(100));assertTrue(service.current(stamp).isEmpty());}
    @Test void exactGrantIsValidatedRatherThanReplaced() {when(core.currentSubject(id)).thenReturn(Optional.of(auth));var grant=new AdvisoryAuthority.Grant(AdvisoryAuthority.MANAGE,id,1);doThrow(new OperationFailure(404,"RESOURCE_NOT_FOUND","revoked",null)).when(scope).requireStamp(id,1,id,id,id,AdvisoryAuthority.MANAGE);var stamp=new AdvisoryAuthority.Stamp(id,1,id,id,List.of(grant),List.of(),Instant.now().plusSeconds(100));assertTrue(service.current(stamp).isEmpty());verify(scope,never()).capture(any(),anyString(),anyString(),anyString());}
}
