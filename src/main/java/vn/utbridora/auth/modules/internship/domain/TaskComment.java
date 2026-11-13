package vn.utbridora.auth.modules.internship.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import vn.utbridora.auth.modules.internship.api.SubmissionDtos.CommentDto;
import vn.utbridora.auth.modules.internship.api.SubmissionDtos.CommentInput;

/** FR-17/CP-WS2 append-only value. Reuses the existing canonical comment wire types.
 * No submission verdict, assignment transition or Weekly Report review is performed here. */
public record TaskComment(CommentDto value) {
    public TaskComment { Objects.requireNonNull(value, "comment"); }
    public static TaskComment create(UUID id, UUID assignmentId, UUID serverAuthorId, CommentInput input, Instant serverNow) {
        Objects.requireNonNull(input, "input");
        return new TaskComment(new CommentDto(id, assignmentId, serverAuthorId, input.text(), serverNow));
    }
}
