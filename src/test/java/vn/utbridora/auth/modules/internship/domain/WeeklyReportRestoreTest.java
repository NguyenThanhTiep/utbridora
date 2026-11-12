package vn.utbridora.auth.modules.internship.domain;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static vn.utbridora.auth.modules.internship.domain.WeeklyReport.*;

/** Storage-independent rehydration tests; no JDBC schema or production store is assumed. */
class WeeklyReportRestoreTest {
    private final UUID id = UUID.randomUUID(), caseId = UUID.randomUUID(), mentor = UUID.randomUUID();
    private final LocalDate week = LocalDate.parse("2026-11-02");
    private final Content content = new Content("Work", "", "Next", List.of());
    private WeeklyReport draft() { return WeeklyReport.create(id, caseId, week, week, week.plusWeeks(8), content); }
    private WeeklyReport reviewed() {
        var report = draft(); return report.submit(1, report.dueAt().plusNanos(1)).review(2, mentor, "Review", report.dueAt().plusSeconds(1));
    }
    private WeeklyReport restore(List<Revision> revisions, long version) {
        return WeeklyReport.restore(id, draft().key(), draft().dueAt(), version, revisions);
    }
    @Test void restoresAllRevisionFieldsAndSupportsFurtherCommandsWithoutReplayingOldEffects() {
        var original = reviewed().revise(3, "FIX_REPORT").update(4, new Content("New", "", "Next", List.of()));
        var restored = restore(original.revisions(), 5);
        assertEquals(id, restored.reportId()); assertEquals(original.key(), restored.key());
        assertEquals(original.revisions(), restored.revisions()); assertEquals(5, restored.version());
        assertEquals(mentor, restored.revision(1).review().reviewerUserId());
        assertEquals("Review", restored.revision(1).review().comment());
        assertEquals(original.dueAt().plusNanos(1), restored.revision(1).submittedAt());
        assertTrue(restored.revision(1).lateFlag());
        var resubmitted = restored.submit(5, restored.dueAt().plusSeconds(2));
        assertEquals(6, resubmitted.version()); assertEquals(State.SUBMITTED, resubmitted.current().state());
        assertSame(restored.revision(1), resubmitted.revision(1));
    }
    @Test void permitsVersionGapsFromDraftEditsAndMakesDefensiveHistoryCopy() {
        var edited = draft().update(1, content).update(2, content);
        var input = new ArrayList<>(edited.revisions()); var result = restore(input, 3); input.clear();
        assertEquals(1, result.revisions().size()); assertEquals(3, result.version());
        assertThrows(UnsupportedOperationException.class, () -> result.revisions().clear());
    }
    @Test void missingOrEmptyHistoryNeverCreatesAnEmptyRoot() {
        assertThrows(IllegalArgumentException.class, () -> restore(null, 1));
        assertThrows(IllegalArgumentException.class, () -> restore(List.of(), 1));
        assertThrows(IllegalArgumentException.class, () -> restore(Arrays.asList((Revision) null), 1));
    }
    @Test void rejectsRootVersionDriftAndZeroOrOutOfRangeStoredVersions() {
        for (long version : new long[]{0, -1, 2, WeeklyReportPolicy.MAX_VERSION + 1}) {
            assertThrows(IllegalArgumentException.class, () -> restore(draft().revisions(), version));
        }
        var zero = new Revision(1, 0, State.DRAFT, content, null, null, null, false);
        assertThrows(IllegalArgumentException.class, () -> restore(List.of(zero), 0));
    }
    @Test void rejectsMissingDuplicateOrOutOfOrderRevisionNumbers() {
        var first = reviewed().current();
        var third = new Revision(3, 4, State.DRAFT, content, "FIX", null, null, false);
        assertThrows(IllegalArgumentException.class, () -> restore(List.of(first, third), 4));
        assertThrows(IllegalArgumentException.class, () -> restore(List.of(first, first), 3));
        var second = new Revision(2, 4, State.DRAFT, content, "FIX", null, null, false);
        assertThrows(IllegalArgumentException.class, () -> restore(List.of(second, first), 3));
    }
    @Test void rejectsNonIncreasingVersionsAndDraftsInHistoricalPositions() {
        var first = reviewed().current();
        for (long version : new long[]{1, 3}) {
            var second = new Revision(2, version, State.DRAFT, content, "FIX", null, null, false);
            assertThrows(IllegalArgumentException.class, () -> restore(List.of(first, second), version));
        }
        var second = new Revision(2, 2, State.DRAFT, content, "FIX", null, null, false);
        assertThrows(IllegalArgumentException.class, () -> restore(List.of(draft().current(), second), 2));
    }
    @Test void rejectsDeadlineDriftAndInitialRevisionReason() {
        assertThrows(IllegalArgumentException.class, () -> WeeklyReport.restore(id, draft().key(), draft().dueAt().plusNanos(1), 1, draft().revisions()));
        var bad = new Revision(1, 1, State.DRAFT, content, "FIX", null, null, false);
        assertThrows(IllegalArgumentException.class, () -> restore(List.of(bad), 1));
    }
    @Test void rejectsLateFlagInconsistentWithExactStoredSubmissionTime() {
        var due = draft().dueAt();
        var hiddenLate = new Revision(1, 2, State.SUBMITTED, content, null, due.plusNanos(1), null, false);
        var falseLate = new Revision(1, 2, State.SUBMITTED, content, null, due, null, true);
        assertThrows(IllegalArgumentException.class, () -> restore(List.of(hiddenLate), 2));
        assertThrows(IllegalArgumentException.class, () -> restore(List.of(falseLate), 2));
    }
}
