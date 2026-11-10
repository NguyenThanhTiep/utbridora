package vn.utbridora.auth.modules.internship.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.utbridora.auth.modules.internship.domain.SubmissionPolicy;
import static vn.utbridora.auth.modules.internship.api.CohortGroupDtos.*;

/** CP-WS2 v1.0 values only; construction does not grant permission or persist a submission. */
public final class SubmissionDtos {
    private SubmissionDtos() {}

    public enum ReviewVerdict { ACCEPT, REQUEST_REVISION }

    public record SubmissionInput(Long expectedAssignmentVersion, List<UUID> fileIds, String url, String note) {
        public SubmissionInput {
            expectedAssignmentVersion = version(expectedAssignmentVersion, "expectedAssignmentVersion");
            required(fileIds, "fileIds");
            SubmissionPolicy.requireArtifact(fileIds, url);
            fileIds = List.copyOf(fileIds);
            note = optionalNote(note);
        }
    }

    public record SubmissionDto(UUID submissionId, UUID assignmentId, Integer versionNo, List<UUID> fileIds,
                                String url, String note, Instant submittedAt, Boolean lateFlag) {
        public SubmissionDto {
            submissionId = required(submissionId, "submissionId");
            assignmentId = required(assignmentId, "assignmentId");
            versionNo = positive(versionNo, "versionNo");
            required(fileIds, "fileIds");
            SubmissionPolicy.requireArtifact(fileIds, url);
            fileIds = List.copyOf(fileIds);
            note = optionalNote(note);
            submittedAt = required(submittedAt, "submittedAt");
            lateFlag = required(lateFlag, "lateFlag");
        }
    }

    public record ReviewInput(Long expectedAssignmentVersion, ReviewVerdict verdict, String comment) {
        public ReviewInput {
            expectedAssignmentVersion = version(expectedAssignmentVersion, "expectedAssignmentVersion");
            verdict = required(verdict, "verdict");
            comment = text(comment, 2_000, "comment");
        }
    }

    public record ReviewDto(UUID reviewId, UUID submissionId, Integer reviewedVersionNo, ReviewVerdict verdict,
                            String comment, UUID reviewerUserId, Instant createdAt, Long assignmentVersion) {
        public ReviewDto {
            reviewId = required(reviewId, "reviewId");
            submissionId = required(submissionId, "submissionId");
            reviewedVersionNo = positive(reviewedVersionNo, "reviewedVersionNo");
            verdict = required(verdict, "verdict");
            comment = text(comment, 2_000, "comment");
            reviewerUserId = required(reviewerUserId, "reviewerUserId");
            createdAt = required(createdAt, "createdAt");
            assignmentVersion = version(assignmentVersion, "assignmentVersion");
        }
    }

    public record CommentInput(String text) {
        public CommentInput { text = CohortGroupDtos.text(text, 2_000, "text"); }
    }

    public record CommentDto(UUID commentId, UUID assignmentId, UUID authorUserId, String text, Instant createdAt) {
        public CommentDto {
            commentId = required(commentId, "commentId");
            assignmentId = required(assignmentId, "assignmentId");
            authorUserId = required(authorUserId, "authorUserId");
            text = CohortGroupDtos.text(text, 2_000, "text");
            createdAt = required(createdAt, "createdAt");
        }
    }

    private static Integer positive(Integer value, String field) {
        required(value, field);
        if (value < 1) throw new IllegalArgumentException(field + " must be positive");
        return value;
    }

    private static String optionalNote(String value) {
        if (value == null) return null;
        String normalized = value.replaceAll("(?U)^\\s+|\\s+$", "");
        if (normalized.codePointCount(0, normalized.length()) > 2_000) {
            throw new IllegalArgumentException("note exceeds contract Text range");
        }
        return normalized;
    }
}
