package vn.utbridora.auth.modules.internship.domain;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.utbridora.auth.operations.OperationFailure;
import static org.junit.jupiter.api.Assertions.*;
import static vn.utbridora.auth.modules.internship.domain.WeeklyReport.*;

class WeeklyReportTest {
    private final UUID id = UUID.randomUUID(), caseId = UUID.randomUUID(), mentor = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-10T16:59:59Z");
    private Content content(String text) { return new Content(text, "", "Next", List.of()); }
    private WeeklyReport draft() {
        return WeeklyReport.create(id, caseId, LocalDate.parse("2026-11-02"),
                LocalDate.parse("2026-11-04"), LocalDate.parse("2026-12-31"), content("First"));
    }

    @Test void rootIdentityAndOldReviewedContentSurviveTwoEditsAndNewSubmission() {
        var draft = draft(); var submitted = draft.submit(1, now);
        var reviewed = submitted.review(2, mentor, " Original review ", now);
        var old = reviewed.current();
        var revision = reviewed.revise(3, "FIX_REPORT");
        var edited = revision.update(4, content("Second")).update(5, content("Third"));
        var submittedAgain = edited.submit(6, now.plusNanos(1));
        assertEquals(id, submittedAgain.reportId()); assertEquals(draft.key(), submittedAgain.key());
        assertEquals(2, submittedAgain.current().revisionNo()); assertEquals(7, submittedAgain.version());
        assertSame(old, submittedAgain.revision(1));
        assertEquals("First", old.content().completedWork()); assertEquals("Original review", old.review().comment());
        assertEquals(mentor, old.review().reviewerUserId()); assertEquals(State.REVIEWED, old.state());
        assertFalse(old.lateFlag()); assertTrue(submittedAgain.current().lateFlag());
        assertEquals("Third", submittedAgain.current().content().completedWork());
        assertNull(revision.current().submittedAt()); assertNull(revision.current().review());
        assertFalse(revision.current().lateFlag()); assertEquals("FIX_REPORT", revision.current().reasonCode());
        assertEquals(State.DRAFT, draft.current().state());
    }
    @Test void submittedContentCannotBeUpdatedAndOldRevisionCannotBeReviewedAgain() {
        var submitted = draft().submit(1, now);
        code("STATE_CONFLICT", () -> submitted.update(2, content("Overwrite")));
        code("STATE_CONFLICT", () -> submitted.submit(2, now));
        var reviewed = submitted.review(2, mentor, "Good", now);
        code("STATE_CONFLICT", () -> reviewed.review(3, mentor, "Overwrite review", now));
        code("STATE_CONFLICT", () -> reviewed.update(3, content("Overwrite")));
    }
    @Test void draftCannotBeReviewedOrHaveAnotherConcurrentDraft() {
        code("STATE_CONFLICT", () -> draft().review(1, mentor, "Review", now));
        code("STATE_CONFLICT", () -> draft().revise(1, "FIX"));
    }
    @Test void revisionIsAlsoAllowedFromSubmittedAndPreservesFileRefsWithoutMutableAliases() {
        var file = UUID.randomUUID(); var source = new ArrayList<>(List.of(file));
        var updated = draft().update(1, new Content("Work", "", "Plan", source)); source.clear();
        var submitted = updated.submit(2, now); var revised = submitted.revise(3, "FIX");
        assertEquals(List.of(file), revised.current().content().evidenceFileIds());
        assertSame(submitted.current(), revised.revision(1));
        assertThrows(UnsupportedOperationException.class, () -> revised.revisions().clear());
        assertThrows(UnsupportedOperationException.class, () -> revised.current().content().evidenceFileIds().clear());
    }
    @Test void staleVersionIsRejectedForEveryCommand() {
        code("VERSION_CONFLICT", () -> draft().update(0, content("Changed")));
        code("VERSION_CONFLICT", () -> draft().submit(0, now));
        var submitted = draft().submit(1, now);
        code("VERSION_CONFLICT", () -> submitted.review(1, mentor, "Review", now));
        code("VERSION_CONFLICT", () -> submitted.revise(1, "FIX"));
    }
    @Test void initialCreateCannotBindFilesBeforeTheRootExists() {
        code("FILE_BINDING_INVALID", () -> WeeklyReport.create(id, caseId, LocalDate.parse("2026-11-02"),
                LocalDate.parse("2026-11-04"), LocalDate.parse("2026-12-31"), new Content("Work", "", "Plan", List.of(id))));
    }
    @Test void revisionLookupNeverFallsBackToCurrentAndReasonIsMandatory() {
        code("RESOURCE_NOT_FOUND", () -> draft().revision(0));
        code("RESOURCE_NOT_FOUND", () -> draft().revision(2));
        var submitted = draft().submit(1, now);
        for (String invalid : new String[]{null, "", "ab", "lower_case", " BAD ", "A".repeat(65)}) {
            assertThrows(IllegalArgumentException.class, () -> submitted.revise(2, invalid));
        }
    }
    private void code(String expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(OperationFailure.class, action).code);
    }
}
