package vn.utbridora.auth.modules.internship.domain;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.utbridora.auth.modules.internship.api.SubmissionDtos.CommentInput;
import static org.junit.jupiter.api.Assertions.*;

class TaskCommentTest {
    @Test void independentAppendValuesPreserveOriginalAuthorTextAndTime() {
        var assignment = UUID.randomUUID(); var author = UUID.randomUUID(); var now = Instant.parse("2026-11-10T00:00:00Z");
        var first = TaskComment.create(UUID.randomUUID(), assignment, author, new CommentInput("\u00a0 First \u3000"), now);
        var second = TaskComment.create(UUID.randomUUID(), assignment, author, new CommentInput("Second"), now.plusSeconds(1));
        assertEquals(assignment, first.value().assignmentId()); assertEquals(author, first.value().authorUserId());
        assertEquals("First", first.value().text()); assertEquals(now, first.value().createdAt());
        assertNotEquals(first.value().commentId(), second.value().commentId());
        assertEquals("Second", second.value().text()); assertEquals("First", first.value().text());
    }
}
