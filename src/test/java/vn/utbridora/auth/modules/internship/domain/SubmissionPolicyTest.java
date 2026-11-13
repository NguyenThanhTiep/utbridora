package vn.utbridora.auth.modules.internship.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.utbridora.auth.operations.OperationFailure;
import static vn.utbridora.auth.modules.internship.api.SubmissionDtos.ReviewVerdict.*;
import static vn.utbridora.auth.modules.internship.api.TaskDtos.AssignmentState;
import static org.junit.jupiter.api.Assertions.*;

class SubmissionPolicyTest {
    private static final UUID CURRENT = new UUID(0, 71);
    private static final Instant DUE = Instant.parse("2026-11-10T03:00:00Z");

    @Test void artifactXorPreservesBothDeclaredBranchesWithoutClaimingFileReadiness() {
        assertDoesNotThrow(() -> SubmissionPolicy.requireArtifact(List.of(), "https://example.test/submission"));
        assertDoesNotThrow(() -> SubmissionPolicy.requireArtifact(List.of(CURRENT), null));
        var twenty = IntStream.range(0, 20).mapToObj(i -> new UUID(0, i)).toList();
        assertDoesNotThrow(() -> SubmissionPolicy.requireArtifact(twenty, null));
        artifactError(() -> SubmissionPolicy.requireArtifact(List.of(), null));
        artifactError(() -> SubmissionPolicy.requireArtifact(List.of(CURRENT), "https://example.test/v1"));
        artifactError(() -> SubmissionPolicy.requireArtifact(List.of(CURRENT, CURRENT), null));
        artifactError(() -> SubmissionPolicy.requireArtifact(IntStream.range(0, 21).mapToObj(i -> new UUID(0, i)).toList(), null));
        assertThrows(IllegalArgumentException.class, () -> SubmissionPolicy.requireArtifact(null, null));
        assertThrows(IllegalArgumentException.class, () -> SubmissionPolicy.requireArtifact(Arrays.asList(CURRENT, null), null));
    }

    @ParameterizedTest @ValueSource(strings = {
        "https://example.test/a?x=1#anchor", "HTTPS://example.test/a", "https://localhost/a",
        "https://127.0.0.1/a", "https://[::1]:8443/a", "https://example.test/a%20b"
    })
    void acceptsAbsoluteHttpsWithoutAddingNetworkOrHostnamePolicy(String url) {
        assertDoesNotThrow(() -> SubmissionPolicy.requireArtifact(List.of(), url));
        // Even unreachable URLs are syntax-only; there is no HTTP client or DNS lookup.
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {
        " ", "http://example.test/a", "ftp://example.test/a", "//example.test/a", "/a",
        "https:example.test", "https:///a", "https://", "https://user:secret@example.test/a",
        "https://user@example.test", "https://@example.test", "https://example.test/a b",
        " https://example.test", "https://example.test\n", "https://example.test/%zz"
    })
    void rejectsInvalidUrlOrEmptyArtifactWithoutLeakingTheInput(String url) {
        if (url == null) artifactError(() -> SubmissionPolicy.requireArtifact(List.of(), null));
        else {
            var error = assertThrows(IllegalArgumentException.class, () -> SubmissionPolicy.requireArtifact(List.of(), url));
            assertEquals("url must be an absolute HTTPS URL without userinfo", error.getMessage());
        }
    }

    @ParameterizedTest @EnumSource(AssignmentState.class)
    void onlyInProgressCanSubmit(AssignmentState state) {
        if (state == AssignmentState.IN_PROGRESS) assertDoesNotThrow(() -> SubmissionPolicy.requireSubmission(state, 2L, 2L));
        else failure(409, "STATE_CONFLICT", () -> SubmissionPolicy.requireSubmission(state, 2L, 2L));
    }

    @ParameterizedTest @EnumSource(AssignmentState.class)
    void onlySubmittedCurrentVersionCanBeReviewed(AssignmentState state) {
        if (state == AssignmentState.SUBMITTED) assertDoesNotThrow(() -> SubmissionPolicy.requireReview(state, 3L, 3L, CURRENT, CURRENT));
        else failure(409, "STATE_CONFLICT", () -> SubmissionPolicy.requireReview(state, 3L, 3L, CURRENT, CURRENT));
    }

    @Test void versionMismatchAndOldSubmissionAreDifferentConflicts() {
        failure(409, "VERSION_CONFLICT", () -> SubmissionPolicy.requireSubmission(AssignmentState.IN_PROGRESS, 3L, 2L));
        failure(409, "VERSION_CONFLICT", () -> SubmissionPolicy.requireReview(AssignmentState.SUBMITTED, 3L, 2L, CURRENT, CURRENT));
        failure(409, "STALE_SUBMISSION", () -> SubmissionPolicy.requireReview(AssignmentState.SUBMITTED, 5L, 2L, new UUID(0, 70), CURRENT));
        failure(409, "STALE_SUBMISSION", () -> SubmissionPolicy.requireReview(AssignmentState.SUBMITTED, 3L, 3L, null, CURRENT));
        failure(409, "STALE_SUBMISSION", () -> SubmissionPolicy.requireReview(AssignmentState.SUBMITTED, 3L, 3L, CURRENT, null));
        assertEquals(AssignmentState.COMPLETED, SubmissionPolicy.reviewedState(ACCEPT));
        assertEquals(AssignmentState.REVISION_REQUIRED, SubmissionPolicy.reviewedState(REQUEST_REVISION));
        assertThrows(IllegalArgumentException.class, () -> SubmissionPolicy.reviewedState(null));
    }

    @ParameterizedTest @NullSource @ValueSource(longs = {-1, Long.MIN_VALUE, 9_007_199_254_740_992L, Long.MAX_VALUE})
    void malformedVersionIsRejectedOnEitherSide(Long version) {
        assertThrows(IllegalArgumentException.class, () -> SubmissionPolicy.requireSubmission(AssignmentState.IN_PROGRESS, version, 1L));
        assertThrows(IllegalArgumentException.class, () -> SubmissionPolicy.requireSubmission(AssignmentState.IN_PROGRESS, 1L, version));
        assertThrows(IllegalArgumentException.class, () -> SubmissionPolicy.requireReview(AssignmentState.SUBMITTED, version, 1L, CURRENT, CURRENT));
        assertThrows(IllegalArgumentException.class, () -> SubmissionPolicy.requireReview(AssignmentState.SUBMITTED, 1L, version, CURRENT, CURRENT));
    }

    @Test void zeroIsAValidVersionButMaxCannotBeAdvanced() {
        assertDoesNotThrow(() -> SubmissionPolicy.requireSubmission(AssignmentState.IN_PROGRESS, 0L, 0L));
        assertDoesNotThrow(() -> SubmissionPolicy.requireReview(AssignmentState.SUBMITTED, 0L, 0L, CURRENT, CURRENT));
        long max = 9_007_199_254_740_991L;
        failure(409, "VERSION_CONFLICT", () -> SubmissionPolicy.requireSubmission(AssignmentState.IN_PROGRESS, max, max));
        failure(409, "VERSION_CONFLICT", () -> SubmissionPolicy.requireReview(AssignmentState.SUBMITTED, max, max, CURRENT, CURRENT));
        failure(409, "STATE_CONFLICT", () -> SubmissionPolicy.requireSubmission(null, 1L, 1L));
        failure(409, "STATE_CONFLICT", () -> SubmissionPolicy.requireReview(null, 1L, 1L, CURRENT, CURRENT));
    }

    @Test void deadlineIsInclusiveAndUsesNanosecondBoundaries() {
        for (boolean lateAllowed : List.of(false, true)) {
            assertFalse(SubmissionPolicy.lateFlag(DUE, lateAllowed, DUE.minusNanos(1)));
            assertFalse(SubmissionPolicy.lateFlag(DUE, lateAllowed, DUE));
        }
        assertTrue(SubmissionPolicy.lateFlag(DUE, true, DUE.plusNanos(1)));
        failure(422, "SUBMISSION_LATE", () -> SubmissionPolicy.lateFlag(DUE, false, DUE.plusNanos(1)));
        assertThrows(IllegalArgumentException.class, () -> SubmissionPolicy.lateFlag(null, true, DUE));
        assertThrows(IllegalArgumentException.class, () -> SubmissionPolicy.lateFlag(DUE, null, DUE));
        assertThrows(IllegalArgumentException.class, () -> SubmissionPolicy.lateFlag(DUE, true, null));
    }

    private static void artifactError(org.junit.jupiter.api.function.Executable call) {
        failure(422, "SUBMISSION_ARTIFACT_INVALID", call);
    }

    private static void failure(int status, String code, org.junit.jupiter.api.function.Executable call) {
        var error = assertThrows(OperationFailure.class, call);
        assertEquals(status, error.status);
        assertEquals(code, error.code);
    }
}
