package vn.utbridora.auth.modules.internship.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import vn.utbridora.auth.modules.internship.api.SubmissionDtos.ReviewVerdict;
import vn.utbridora.auth.modules.internship.api.TaskDtos.AssignmentState;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/**
 * Pure CP-WS2 v1.0 prerequisites. No URL is fetched and no assignment is changed.
 * Runtime must independently check identity/ownership/current Mentor handover,
 * Case IN_PROGRESS, authoritative Placement ACTIVE/effectiveAt and atomic persistence.
 * File syntax does not establish CLEAN status, binding or permission to download.
 */
public final class SubmissionPolicy {
    private static final long MAX_VERSION = 9_007_199_254_740_991L;
    private SubmissionPolicy() {}

    public static void requireArtifact(List<UUID> fileIds, String url) {
        if (fileIds == null || fileIds.stream().anyMatch(id -> id == null)) {
            throw new IllegalArgumentException("fileIds must be an array of UUIDs");
        }
        if (fileIds.size() > 20 || new HashSet<>(fileIds).size() != fileIds.size()
                || (url == null) == fileIds.isEmpty()) {
            throw failure(422, "SUBMISSION_ARTIFACT_INVALID");
        }
        if (url != null) requireHttpsUrl(url);
    }

    private static void requireHttpsUrl(String value) {
        try {
            URI uri = new URI(value);
            if (uri.isAbsolute() && "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null && uri.getRawUserInfo() == null) return;
        } catch (URISyntaxException invalid) {
            // Do not expose a URI exception containing the supplied URL.
        }
        throw new IllegalArgumentException("url must be an absolute HTTPS URL without userinfo");
    }

    /** Current/expected versions come from stored assignment and validated request, respectively. */
    public static void requireSubmission(AssignmentState state, Long currentVersion, Long expectedVersion) {
        requireVersionMatch(currentVersion, expectedVersion);
        if (state != AssignmentState.IN_PROGRESS) throw failure(409, "STATE_CONFLICT");
    }

    /** Check current submission first so an old target is reported as STALE_SUBMISSION. */
    public static void requireReview(AssignmentState state, Long currentVersion, Long expectedVersion,
                                     UUID submissionId, UUID currentSubmissionId) {
        if (submissionId == null || currentSubmissionId == null || !submissionId.equals(currentSubmissionId)) {
            throw failure(409, "STALE_SUBMISSION");
        }
        requireVersionMatch(currentVersion, expectedVersion);
        if (state != AssignmentState.SUBMITTED) throw failure(409, "STATE_CONFLICT");
    }

    /** Both verdicts require a non-empty comment in ReviewInput under CP-WS2. */
    public static AssignmentState reviewedState(ReviewVerdict verdict) {
        if (verdict == null) throw new IllegalArgumentException("verdict is required");
        return switch (verdict) {
            case ACCEPT -> AssignmentState.COMPLETED;
            case REQUEST_REVISION -> AssignmentState.REVISION_REQUIRED;
        };
    }

    /** Inclusive dueAt boundary, using server time captured by the caller (never a browser clock). */
    public static boolean lateFlag(Instant dueAt, Boolean lateAllowed, Instant serverNow) {
        if (dueAt == null || lateAllowed == null || serverNow == null) {
            throw new IllegalArgumentException("deadline, lateAllowed and server time are required");
        }
        boolean late = serverNow.isAfter(dueAt);
        if (late && !lateAllowed) throw failure(422, "SUBMISSION_LATE");
        return late;
    }

    private static void requireVersionMatch(Long current, Long expected) {
        if (current == null || expected == null || current < 0 || expected < 0
                || current > MAX_VERSION || expected > MAX_VERSION) {
            throw new IllegalArgumentException("assignment versions must be in contract V range");
        }
        if (!current.equals(expected) || current == MAX_VERSION) throw failure(409, "VERSION_CONFLICT");
    }
}
