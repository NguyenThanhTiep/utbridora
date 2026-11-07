package vn.utbridora.auth.modules.internship.api;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException;
import vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException.Kind;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceErrorMappingTest {
    static Stream<Arguments> failures() {
        return Stream.of(Arguments.of(Kind.UNAUTHENTICATED, 401, "SESSION_EXPIRED"),
            Arguments.of(Kind.DENIED, 403, "ACCESS_DENIED"),
            Arguments.of(Kind.UNAVAILABLE, 503, "DEPENDENCY_UNAVAILABLE"),
            Arguments.of(Kind.NOT_FOUND, 404, "RESOURCE_NOT_FOUND"),
            Arguments.of(Kind.SCOPE_MISMATCH, 404, "RESOURCE_NOT_FOUND"),
            Arguments.of(Kind.DUPLICATE_COHORT, 409, "DUPLICATE_COHORT"),
            Arguments.of(Kind.GROUP_MEMBERSHIP_CONFLICT, 409, "GROUP_MEMBERSHIP_CONFLICT"),
            Arguments.of(Kind.VERSION_CONFLICT, 409, "VERSION_CONFLICT"),
            Arguments.of(Kind.STATE_CONFLICT, 409, "STATE_CONFLICT"),
            Arguments.of(Kind.INVALID_OPERATION, 422, "BUSINESS_VALIDATION"));
    }
    @ParameterizedTest @MethodSource("failures")
    void mapsContractCodeAndKeepsEnvelopeSafe(Kind kind, int status, String code) {
        UUID request = UUID.randomUUID();
        var mapped = WorkspaceErrorMapping.map(new WorkspaceRuleException(kind), request);
        assertEquals(status, mapped.status());
        assertEquals(code, mapped.body().errorCode());
        assertEquals(code, mapped.body().error());
        assertEquals(request, mapped.body().requestId());
        assertTrue(mapped.body().fieldErrors().isEmpty());
        assertNull(mapped.body().retryAfterSeconds());
        assertFalse(mapped.body().message().isBlank());
    }
    @Test void invalidRequestNeverCopiesExceptionOrInputIntoMessage() {
        var mapped = WorkspaceErrorMapping.invalidRequest(UUID.randomUUID());
        assertEquals(400, mapped.status());
        assertEquals("INVALID_REQUEST", mapped.body().errorCode());
        assertThrows(NullPointerException.class, () -> WorkspaceErrorMapping.invalidRequest(null));
    }
}
