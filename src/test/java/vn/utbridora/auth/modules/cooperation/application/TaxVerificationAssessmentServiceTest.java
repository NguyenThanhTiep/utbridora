package vn.utbridora.auth.modules.cooperation.application;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.modules.cooperation.api.CooperationDtos.*;
import vn.utbridora.auth.modules.cooperation.domain.VerificationState;
import vn.utbridora.auth.modules.cooperation.integration.*;
import vn.utbridora.auth.modules.cooperation.port.TaxAuthorityLookupPort;
import vn.utbridora.auth.modules.cooperation.port.TaxAuthorityLookupPort.*;
import vn.utbridora.auth.operations.OperationFailure;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.user.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static vn.utbridora.auth.modules.cooperation.api.TaxAssessmentView.Verdict.*;

class TaxVerificationAssessmentServiceTest {
    final String org = UUID.randomUUID().toString(), school = UUID.randomUUID().toString(), id = UUID.randomUUID().toString();
    final UserRepository users = mock(UserRepository.class);
    final AuthorizationPolicy access = mock(AuthorizationPolicy.class);
    final SchoolDelegationAccess delegations = mock(SchoolDelegationAccess.class);
    final VerificationService records = mock(VerificationService.class);
    final TaxAuthorityLookupPort lookup = mock(TaxAuthorityLookupPort.class);
    final Authentication auth = UsernamePasswordAuthenticationToken.authenticated(new AppPrincipal("actor", "user@example.test", null, 1, List.of()), null, List.of());
    final TaxVerificationProperties properties = new TaxVerificationProperties("mock", false, null, 86400, 30);
    final TaxVerificationAssessmentService service = new TaxVerificationAssessmentService(users, access, delegations, records, lookup, properties);
    @BeforeEach void active() {
        var user = new UserAccount(); user.id = "actor"; user.status = AccountStatus.ACTIVE; user.securityVersion = 1;
        when(users.findById("actor")).thenReturn(Optional.of(user));
        when(access.hasOrganization(auth, org, Role.ENTERPRISE, "enterprise_verification:enterprise:manage")).thenReturn(true);
    }
    VerificationView view(String code, String name, String address, VerificationState state) {
        return new VerificationView(id, org, school, state, 1, 4, null,
            new VerificationInput(school, null, code, name, address, "Contact", "c@example.test", "0901234567", List.of()), Instant.now(), null, null, List.of());
    }
    @Test void allFourVerdictsWithIndependentNameAndAddressComparison() {
        Found found = (Found)new MockTaxAuthorityAdapter().lookup("0101248141");
        when(lookup.lookup(found.taxCode())).thenReturn(found);
        when(records.detail(auth, school, id, true)).thenReturn(view(found.taxCode(), found.officialName(), found.officialAddress(), VerificationState.SUBMITTED));
        assertEquals(MATCHED_ACTIVE, service.assessForSchool(auth, school, id).verdict());
        when(records.detail(auth, school, id, true)).thenReturn(view(found.taxCode(), "CÔNG TY KHÁC", "ĐỊA CHỈ KHÁC", VerificationState.SUBMITTED));
        var mismatch = service.assessForSchool(auth, school, id);
        assertEquals(INFO_MISMATCH, mismatch.verdict()); assertEquals(List.of("registeredName", "address"), mismatch.mismatchedFields());
        assertEquals(INACTIVE_OR_INVALID, service.lookupForEnterprise(auth, org, "0101248142").verdict());
        when(lookup.lookup("0300000000")).thenReturn(new MockTaxAuthorityAdapter().lookup("0300000000"));
        assertEquals(INACTIVE_OR_INVALID, service.lookupForEnterprise(auth, org, "0300000000").verdict());
        when(lookup.lookup(found.taxCode())).thenReturn(new Unavailable(found.taxCode(), "TIMEOUT", "casso", Instant.now()));
        assertEquals(UNVERIFIED_OFFLINE, service.lookupForEnterprise(auth, org, found.taxCode()).verdict());
        when(lookup.lookup(found.taxCode())).thenThrow(new IllegalStateException("private provider error"));
        assertEquals(UNVERIFIED_OFFLINE, service.lookupForEnterprise(auth, org, found.taxCode()).verdict());
    }
    @Test void syntheticAndPersonalCodesNeverCallUpstream() {
        for (String code : new String[]{"SYNTHETIC", "CT-TEST", "079123456789", "0101248141-000"}) service.lookupForEnterprise(auth, org, code);
        verifyNoInteractions(lookup);
    }
    @Test void authorizationAndPrivateDraftsAreCheckedBeforeLookup() {
        assertEquals(401, assertThrows(OperationFailure.class, () -> service.lookupForEnterprise(null, org, "0101248141")).status);
        when(access.hasOrganization(auth, org, Role.ENTERPRISE, "enterprise_verification:enterprise:manage")).thenReturn(false);
        assertEquals(403, assertThrows(OperationFailure.class, () -> service.lookupForEnterprise(auth, org, "0101248141")).status);
        when(records.detail(auth, school, id, true)).thenThrow(new OperationFailure(404, "RESOURCE_NOT_FOUND", "Private", null));
        assertEquals(404, assertThrows(OperationFailure.class, () -> service.assessForSchool(auth, school, id)).status);
        verifyNoInteractions(lookup);
    }
    @Test void schoolUsesSubmittedSnapshotAndRejectsRevokedAccessAfterLookup() {
        var found = (Found)new MockTaxAuthorityAdapter().lookup("0101248141");
        when(records.detail(auth, school, id, true)).thenReturn(view(found.taxCode(), found.officialName(), found.officialAddress(), VerificationState.NEEDS_CHANGES));
        when(lookup.lookup(found.taxCode())).thenReturn(found);
        assertEquals(MATCHED_ACTIVE, service.assessForSchool(auth, school, id).verdict());
        verify(records, times(2)).detail(auth, school, id, true);
        doThrow(new OperationFailure(404, "RESOURCE_NOT_FOUND", "Revoked", null)).when(delegations).require(auth, school, null, "enterprise_verification:school:review");
        assertEquals(404, assertThrows(OperationFailure.class, () -> service.assessForSchool(auth, school, id)).status);
    }
    @Test void staleSecurityVersionAndRateLimit() {
        var user = users.findById("actor").orElseThrow(); user.securityVersion = 2;
        assertEquals(401, assertThrows(OperationFailure.class, () -> service.lookupForEnterprise(auth, org, "0101248141")).status);
        user.securityVersion = 1;
        for (int i = 0; i < 30; i++) service.lookupForEnterprise(auth, org, "CT-TEST");
        var limit = assertThrows(OperationFailure.class, () -> service.lookupForEnterprise(auth, org, "CT-TEST"));
        assertEquals(429, limit.status); assertNotNull(limit.retryAfterSeconds);
    }
    @Test void rateLimitWindowExpiresAndRevocationDuringLookupDiscardsResult() {
        Clock clock = mock(Clock.class); Instant now = Instant.parse("2026-11-27T00:00:00Z"); when(clock.instant()).thenReturn(now);
        var limited = new TaxVerificationAssessmentService(users, access, delegations, records, lookup,
            new TaxVerificationProperties("mock", false, null, 86400, 1), clock);
        limited.lookupForEnterprise(auth, org, "SYNTHETIC");
        assertEquals(429, assertThrows(OperationFailure.class, () -> limited.lookupForEnterprise(auth, org, "SYNTHETIC")).status);
        when(clock.instant()).thenReturn(now.plusSeconds(60)); limited.lookupForEnterprise(auth, org, "SYNTHETIC");
        var user = users.findById("actor").orElseThrow();
        when(lookup.lookup("0101248141")).thenAnswer(call -> { user.securityVersion++; return new MockTaxAuthorityAdapter().lookup("0101248141"); });
        assertEquals(401, assertThrows(OperationFailure.class, () -> service.lookupForEnterprise(auth, org, "0101248141")).status);
    }
    @Test void schoolRejectsARecordChangedDuringExternalLookup() {
        var first = view("0101248141", "FPT", "Address", VerificationState.SUBMITTED);
        var changed = new VerificationView(id, org, school, VerificationState.NEEDS_CHANGES, 1, first.version()+1, null,
            first.data(), first.submittedAt(), null, null, List.of("address"));
        when(records.detail(auth, school, id, true)).thenReturn(first, changed);
        when(lookup.lookup("0101248141")).thenReturn(new MockTaxAuthorityAdapter().lookup("0101248141"));
        assertEquals(409, assertThrows(OperationFailure.class, () -> service.assessForSchool(auth, school, id)).status);
    }
}
