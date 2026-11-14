package vn.utbridora.auth.modules.internship.infrastructure;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.*;
import vn.utbridora.auth.modules.internship.api.SubmissionDtos.*;
import vn.utbridora.auth.modules.internship.api.TaskDtos.*;
import vn.utbridora.auth.modules.internship.application.TaskCommentPorts.*;
import vn.utbridora.auth.modules.internship.domain.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JdbcTaskCommentSessionTest extends Dev06JdbcTestSupport {
    private final UUID assignmentId = UUID.randomUUID();
    private final JdbcTaskCommentSession.AssignmentVersions versions = mock(JdbcTaskCommentSession.AssignmentVersions.class);
    private final Target target = target();
    private final CommentDto comment = new CommentDto(UUID.randomUUID(), assignmentId, student, "Discussion", now);
    private Target target() {
        var cohort = new Cohort(UUID.randomUUID(), new WorkspaceScope(enterprise, school, term), "C", "Cohort", 1);
        var group = new InternshipGroup(UUID.randomUUID(), cohort.id(), "G", List.of(caseId), List.of(mentor), 1);
        var assignment = new AssignmentDto(assignmentId, caseId, student, AssignmentState.NEW, 3L, null);
        return new Target(cohort, group, new InternshipTask(new TaskDto(UUID.randomUUID(), group.id(), "Task", "Description", now,
                TaskPriority.NORMAL, true, 1L, List.of(assignment))), assignment);
    }
    private JdbcTaskCommentSession session(boolean nanos) {
        // Synthetic layout only; no production table/column or V29 ownership is selected here.
        var layout = new JdbcTaskCommentSession.Layout("fixture_comments", "id", "assignment_id", "author_user_id", "body", "created_at", nanos ? "created_nanos" : null);
        return new JdbcTaskCommentSession(jdbc, layout, versions, schema);
    }
    @ParameterizedTest @ValueSource(longs = {3, 4})
    void appendUsesOwnerVersionPolicyWithoutWritingAssignmentOrChangingItsState(long version) {
        when(versions.afterAppend(target)).thenReturn(version);
        var receipt = rc(() -> session(false).append(target, new TaskComment(comment)));
        assertEquals(comment, receipt.comment()); assertEquals(version, receipt.assignmentVersion());
        assertEquals(1, writes.size()); assertTrue(writes.getFirst().sql().startsWith("INSERT INTO `fixture_comments`"));
        assertEquals(List.of(comment.commentId().toString(), assignmentId.toString(), student.toString(), "Discussion", Dev06Jdbc.micros(now)), writes.getFirst().parameters());
        assertTrue(writes.stream().noneMatch(s -> s.sql().contains("UPDATE") || s.sql().contains("DELETE") || s.sql().contains("internship_task_assignments")));
        verify(versions).afterAppend(target); assertEquals(AssignmentState.NEW, target.assignment().state());
    }
    @Test void ownerVersionFailureRollsBackAppendAndIsNotReplacedWithFakeSuccess() {
        when(versions.afterAppend(target)).thenThrow(vn.utbridora.auth.operations.WorkflowValues.failure(409, "VERSION_CONFLICT"));
        code(409, "VERSION_CONFLICT", () -> rc(() -> session(false).append(target, new TaskComment(comment))));
        assertEquals(1, writes.size()); assertEquals(1, manager.rollbacks); assertEquals(0, manager.commits);
    }
    @Test void regressingReceiptVersionIsRejectedAndTransactionRolledBack() {
        when(versions.afterAppend(target)).thenReturn(2L);
        code(503, "DEPENDENCY_UNAVAILABLE", () -> rc(() -> session(false).append(target, new TaskComment(comment))));
        assertEquals(1, manager.rollbacks);
    }
    @Test void missingLayoutSchemaVersionPolicyAndTransactionsFailClosed() {
        code(503, "DEPENDENCY_UNAVAILABLE", () -> new JdbcTaskCommentSession(jdbc, null, versions, schema));
        var layout = new JdbcTaskCommentSession.Layout("fixture_comments", "id", "assignment_id", "author_user_id", "body", "created_at", null);
        code(503, "DEPENDENCY_UNAVAILABLE", () -> new JdbcTaskCommentSession(jdbc, layout, null, schema));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> new JdbcTaskCommentSession(jdbc, layout, versions, null));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> session(false).append(target, new TaskComment(comment)));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> inReadOnlyTransaction(() -> session(false).append(target, new TaskComment(comment))));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> inTransaction(() -> session(false).append(target, new TaskComment(comment))));
        assertTrue(writes.isEmpty()); verifyNoInteractions(versions);
    }
    @ParameterizedTest @ValueSource(strings = {"comments;DELETE", "schema.comments", "comments`", "comments--", ""})
    void physicalLayoutCannotInjectSql(String table) {
        assertThrows(IllegalArgumentException.class, () -> new JdbcTaskCommentSession.Layout(table, "id", "assignment_id", "author", "body", "created_at", null));
    }
    @Test void ownerSchemaCheckFailureDoesNotAttemptStorage() {
        doThrow(new DataAccessResourceFailureException("Synthetic missing schema")).when(schema).run();
        code(503, "DEPENDENCY_UNAVAILABLE", () -> rc(() -> session(false).append(target, new TaskComment(comment))));
        assertTrue(writes.isEmpty()); verifyNoInteractions(versions);
    }
    @Test void foreignAssignmentCannotBeAppended() {
        var foreign = new CommentDto(comment.commentId(), UUID.randomUUID(), student, comment.text(), now);
        code(503, "DEPENDENCY_UNAVAILABLE", () -> rc(() -> session(false).append(target, new TaskComment(foreign))));
        assertTrue(writes.isEmpty()); verifyNoInteractions(versions);
    }
    @Test void cannotSilentlyTruncateNanosecondWhenOwnerLayoutHasNoRemainder() {
        var precise = new CommentDto(comment.commentId(), assignmentId, student, comment.text(), now.plusNanos(1));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> rc(() -> session(false).append(target, new TaskComment(precise))));
        assertTrue(writes.isEmpty()); verifyNoInteractions(versions);
    }
    @Test void explicitRemainderColumnPreservesExactTimeInWriteAndRead() {
        var time = now.plusNanos(123456789); var precise = new CommentDto(comment.commentId(), assignmentId, student, comment.text(), time);
        when(versions.afterAppend(target)).thenReturn(3L);
        assertEquals(precise, rc(() -> session(true).append(target, new TaskComment(precise))).comment());
        assertEquals(789, writes.getFirst().parameters().getLast());
        pageRows = List.of(row("id", precise.commentId(), "assignment_id", assignmentId, "author_user_id", student, "comment_text", precise.text(),
                "created_at", Dev06Jdbc.micros(time), "submicro_nanos", 789, "total_elements", 1L));
        var page = inTransaction(() -> session(true).comments(assignmentId, 0, 25));
        assertEquals(List.of(precise), page.content()); assertEquals(1, page.totalPages());
    }
    @Test void pageAndTotalUseSameQueryAndEmptyOutOfRangePageKeepsTotal() {
        pageRows = List.of(row("id", null, "total_elements", 60L));
        var page = inTransaction(() -> session(false).comments(assignmentId, 3, 25));
        assertTrue(page.content().isEmpty()); assertEquals(60, page.totalElements()); assertEquals(3, page.totalPages());
        assertEquals(1, reads.size()); assertEquals(List.of(assignmentId.toString(), assignmentId.toString(), 25, 75L), reads.getFirst().parameters());
        assertFalse(reads.getFirst().sql().contains(",0 ASC"));
    }
    @Test void foreignStoredCommentAndInvalidRemainderCannotLeak() {
        var stored = row("id", comment.commentId(), "assignment_id", UUID.randomUUID(), "author_user_id", student,
                "comment_text", comment.text(), "created_at", Dev06Jdbc.micros(now), "submicro_nanos", 0, "total_elements", 1L);
        pageRows = List.of(stored);
        code(503, "DEPENDENCY_UNAVAILABLE", () -> inTransaction(() -> session(true).comments(assignmentId, 0, 25)));
        stored.put("assignment_id", assignmentId); stored.put("submicro_nanos", 1000);
        code(503, "DEPENDENCY_UNAVAILABLE", () -> inTransaction(() -> session(true).comments(assignmentId, 0, 25)));
    }
    @Test void sqlDuplicateAndFailedInsertDoNotCallOwnerVersionMutation() {
        write = statement -> { throw new DuplicateKeyException("Synthetic collision"); };
        code(409, "VERSION_CONFLICT", () -> rc(() -> session(false).append(target, new TaskComment(comment))));
        write = statement -> 0;
        code(503, "DEPENDENCY_UNAVAILABLE", () -> rc(() -> session(false).append(target, new TaskComment(comment))));
        verifyNoInteractions(versions); assertEquals(2, manager.rollbacks);
    }
}
