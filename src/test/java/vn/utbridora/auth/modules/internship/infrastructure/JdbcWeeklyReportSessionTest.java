package vn.utbridora.auth.modules.internship.infrastructure;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.*;
import vn.utbridora.auth.modules.internship.domain.WeeklyReport;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JdbcWeeklyReportSessionTest extends Dev06JdbcTestSupport {
    private final UUID id = UUID.randomUUID();
    private final JdbcWeeklyReportSession session = new JdbcWeeklyReportSession(jdbc, schema);
    @Test void restoresTwoRevisionsFeedbackDuplicateFileRefsAndNanosecondBoundary() {
        var file = UUID.randomUUID(); var original = draft(id);
        var reviewed = original.update(1, new WeeklyReport.Content("Work", "", "Next", List.of(file, file)))
                .submit(2, original.dueAt().plusNanos(1)).review(3, mentor, "Feedback", original.dueAt().plusNanos(123456789));
        var current = reviewed.revise(4, "CORRECTION"); stored(current);
        var read = inTransaction(() -> session.find(id));
        assertEquals(current.revisions(), read.revisions()); assertEquals(current.key(), read.key());
        assertEquals(original.dueAt().plusNanos(1), read.revision(1).submittedAt()); assertTrue(read.revision(1).lateFlag());
        assertEquals("Feedback", read.revision(1).review().comment()); assertEquals(List.of(file, file), read.current().content().evidenceFileIds());
        assertTrue(writes.isEmpty());
    }
    @Test void missingRootReturnsNullWithoutQueryingChildren() {
        assertNull(inTransaction(() -> session.find(id))); assertEquals(1, reads.size());
    }
    @Test void rootInsertAndInitialRevisionShareExistingTransaction() {
        assertTrue(rc(() -> session.insertIfAbsent(draft(id))));
        assertEquals(2, writes.size()); assertEquals(id.toString(), writes.getFirst().parameters().getFirst());
        assertEquals(caseId.toString(), writes.getFirst().parameters().get(1)); assertEquals(1, manager.commits);
        assertTrue(writes.stream().noneMatch(s -> s.sql().contains("operation_records") || s.sql().contains("placements")));
    }
    @Test void uniqueRootConflictReturnsFalseWithoutChildWrites() {
        write = statement -> { throw new DuplicateKeyException("Synthetic root collision"); };
        assertFalse(rc(() -> session.insertIfAbsent(draft(id)))); assertEquals(1, writes.size());
    }
    @Test void failedRevisionInsertRequiresRollbackOfRootInsert() {
        write = statement -> { if (statement.sql().contains("_revisions")) throw new DataAccessResourceFailureException("Synthetic"); return 1; };
        code(503, "DEPENDENCY_UNAVAILABLE", () -> rc(() -> session.insertIfAbsent(draft(id))));
        assertEquals(1, manager.rollbacks); assertEquals(0, manager.commits);
    }
    @Test void draftFileEditUsesRootCasAndOnlyCurrentDraftRefs() {
        var before = draft(id); stored(before); var file = UUID.randomUUID();
        var updated = before.update(1, new WeeklyReport.Content("Changed", "", "Next", List.of(file, file)));
        assertTrue(rc(() -> session.replace(updated, 1)));
        assertEquals(List.of(1, 2L, id.toString(), 1L), writes.getFirst().parameters());
        assertEquals(2, writes.stream().filter(s -> s.sql().startsWith("INSERT INTO internship_weekly_report_files")).count());
        assertEquals(1, writes.stream().filter(s -> s.sql().startsWith("DELETE FROM internship_weekly_report_files")).count());
        assertTrue(reads.getFirst().sql().endsWith("FOR UPDATE"));
    }
    @Test void submitDoesNotRewriteEvidenceAndPreservesOneNanosecondLateness() {
        var initial = draft(id); var file = UUID.randomUUID();
        var before = initial.update(1, new WeeklyReport.Content("Work", "", "Next", List.of(file))); stored(before);
        var submitted = before.submit(2, before.dueAt().plusNanos(1));
        assertTrue(rc(() -> session.replace(submitted, 2)));
        assertEquals(2, writes.size());
        assertEquals(1, writes.get(1).parameters().get(6)); assertEquals(true, writes.get(1).parameters().get(7));
    }
    @Test void reviewIsInsertedOnceAndDoesNotUpdateReviewRowsOrFileRefs() {
        var before = draft(id).submit(1, now); stored(before);
        var reviewed = before.review(2, mentor, "Feedback", now.plusNanos(987654321));
        assertTrue(rc(() -> session.replace(reviewed, 2)));
        assertEquals(3, writes.size()); assertTrue(writes.getLast().sql().startsWith("INSERT INTO internship_weekly_report_reviews"));
        assertEquals(mentor.toString(), writes.getLast().parameters().get(2)); assertEquals(321, writes.getLast().parameters().getLast());
        assertTrue(writes.stream().noneMatch(s -> s.sql().startsWith("UPDATE internship_weekly_report_reviews")));
    }
    @Test void newRevisionAppendsWithoutUpdatingAnyHistoricalRevisionReviewOrFiles() {
        var before = draft(id).submit(1, now).review(2, mentor, "Frozen", now); stored(before);
        assertTrue(rc(() -> session.replace(before.revise(3, "CORRECTION"), 3)));
        assertEquals(2, writes.size()); assertTrue(writes.getLast().sql().startsWith("INSERT INTO internship_weekly_report_revisions"));
        assertEquals(2, writes.getLast().parameters().get(1));
        assertTrue(writes.stream().noneMatch(s -> s.sql().startsWith("DELETE") || s.sql().startsWith("UPDATE internship_weekly_report_revisions")));
    }
    @Test void staleVersionAndRootCasRaceNeverWriteChildren() {
        var before = draft(id); stored(before); var updated = before.update(1, before.current().content());
        assertFalse(rc(() -> session.replace(updated, 0))); assertTrue(writes.isEmpty());
        write = statement -> 0;
        assertFalse(rc(() -> session.replace(updated, 1))); assertEquals(1, writes.size());
    }
    @Test void revisionCasFailureRollsBackTheParentCas() {
        var before = draft(id); stored(before);
        write = statement -> statement.sql().startsWith("UPDATE internship_weekly_report_revisions") ? 0 : 1;
        code(409, "VERSION_CONFLICT", () -> rc(() -> session.replace(before.submit(1, now), 1)));
        assertEquals(1, manager.rollbacks); assertEquals(0, manager.commits);
    }
    @Test void forgedHistoricalContentCannotBeWrittenEvenWithCurrentVersion() {
        var before = draft(id).submit(1, now).review(2, mentor, "Frozen", now).revise(3, "CORRECTION"); stored(before);
        var changed = before.update(4, before.current().content()); var history = new ArrayList<>(changed.revisions()); var first = history.getFirst();
        history.set(0, new WeeklyReport.Revision(1, first.version(), first.state(), new WeeklyReport.Content("Forged history", "", "Next", List.of()),
                null, first.submittedAt(), first.review(), first.lateFlag()));
        var forged = WeeklyReport.restore(id, changed.key(), changed.dueAt(), changed.version(), history);
        code(503, "DEPENDENCY_UNAVAILABLE", () -> rc(() -> session.replace(forged, 4))); assertTrue(writes.isEmpty());
    }
    @Test void rootBindingCannotBeChangedByPersistenceCaller() {
        var before = draft(id); stored(before);
        var foreign = WeeklyReport.create(id, UUID.randomUUID(), before.key().weekStart(), scope.startDate(), scope.endDate(), before.current().content()).update(1, before.current().content());
        code(503, "DEPENDENCY_UNAVAILABLE", () -> rc(() -> session.replace(foreign, 1))); assertTrue(writes.isEmpty());
    }
    @Test void invalidInitialAggregateAndMissingSchemaFailBeforeSql() {
        code(503, "DEPENDENCY_UNAVAILABLE", () -> rc(() -> session.insertIfAbsent(draft(id).submit(1, now))));
        doThrow(new DataAccessResourceFailureException("Missing approved schema")).when(schema).run();
        code(503, "DEPENDENCY_UNAVAILABLE", () -> inTransaction(() -> session.find(id)));
        assertTrue(reads.isEmpty()); assertTrue(writes.isEmpty());
    }
    @Test void noTransactionReadOnlyOrUnknownIsolationCannotWrite() {
        code(503, "DEPENDENCY_UNAVAILABLE", () -> session.find(id));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> inReadOnlyTransaction(() -> session.insertIfAbsent(draft(id))));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> inTransaction(() -> session.insertIfAbsent(draft(id))));
        verifyNoInteractions(schema); assertTrue(writes.isEmpty());
    }
    @ParameterizedTest @ValueSource(strings = {"review", "fileOrdinal", "rootPointer", "nanos", "version", "dueAt", "nullId"})
    void corruptStorageFailsClosed(String corruption) {
        var value = draft(id).update(1, new WeeklyReport.Content("Work", "", "Next", List.of(UUID.randomUUID())))
                .submit(2, now).review(3, mentor, "Feedback", now); stored(value);
        switch (corruption) {
            case "review" -> reviews.clear();
            case "fileOrdinal" -> files.getFirst().put("ordinal_no", 2);
            case "rootPointer" -> roots.getFirst().put("current_revision_no", 2);
            case "nanos" -> revisions.getFirst().put("submitted_submicro_nanos", 1000);
            case "version" -> revisions.getFirst().put("report_version", 0);
            case "dueAt" -> roots.getFirst().put("due_at", Dev06Jdbc.micros(value.dueAt().plusSeconds(1)));
            case "nullId" -> roots.getFirst().put("id", null);
        }
        code(503, "DEPENDENCY_UNAVAILABLE", () -> inTransaction(() -> session.find(id))); assertTrue(writes.isEmpty());
    }
    @Test void detectsRootChangingBetweenReadStatementsInsteadOfReturningMixedHistory() {
        stored(draft(id)); var ordinary = query; int[] count = {0};
        query = (sql, args) -> { if (sql.startsWith("SELECT id,case_id") && ++count[0] == 2) roots.getFirst().put("version", 2L); return ordinary.apply(sql, args); };
        code(409, "VERSION_CONFLICT", () -> inTransaction(() -> session.find(id)));
    }
    @Test void emptyOutOfRangePageStillReportsTotalFromSameSqlSnapshot() {
        pageRows = List.of(row("id", null, "total_elements", 67L));
        var page = inTransaction(() -> session.list(caseId, null, 3, 25));
        assertEquals(67, page.totalElements()); assertTrue(page.content().isEmpty()); assertEquals(1, reads.size());
        assertEquals(75L, reads.getFirst().parameters().getLast()); assertEquals(caseId.toString(), reads.getFirst().parameters().getFirst());
    }
    @Test void listFiltersCurrentStateAndRestoresOnlyOneRootPerPageRow() {
        var value = draft(id).submit(1, now); stored(value);
        var entry = new LinkedHashMap<>(roots.getFirst()); entry.put("total_elements", 1L); pageRows = List.of(entry);
        var result = inTransaction(() -> session.list(caseId, WeeklyReport.State.SUBMITTED, 0, 25));
        assertEquals(1, result.content().size()); assertEquals(value.revisions(), result.content().getFirst().revisions());
        assertEquals("SUBMITTED", reads.getFirst().parameters().get(1));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> inTransaction(() -> session.list(UUID.randomUUID(), null, 0, 25)));
    }
}
