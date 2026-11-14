package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.TransactionSystemException;
import vn.utbridora.auth.modules.internship.api.ReportDtos.*;
import vn.utbridora.auth.modules.internship.api.SubmissionDtos.CommentInput;
import vn.utbridora.auth.modules.internship.application.*;
import vn.utbridora.auth.modules.internship.domain.WeeklyReport.State;
import vn.utbridora.auth.operations.OperationFailure;
import static org.junit.jupiter.api.Assertions.*;

/** Full consumer command/service scenarios over synthetic adapters, not runtime API/DB tests. */
class Dev06WorkflowScenarioTest extends Dev06WorkflowFixture {
    @Test void sampleCommentReportAndMentorReviewKeepSeparateHistory() {
        var first = comment(UUID.randomUUID(), "Task discussion, not a submission review");
        var root = draft();
        var submitted = reportCommands.submit(studentActor, root.reportId(), UUID.randomUUID(), new SubmitReport(root.version()));
        var reviewed = reportCommands.review(mentorActor, root.reportId(), UUID.randomUUID(), new ReportReview(submitted.version(), "Report feedback"));
        var frozen = store.roots.get(root.reportId()).current();
        var revised = reportCommands.revision(studentActor, root.reportId(), UUID.randomUUID(), new CreateReportRevision(reviewed.version(), "MENTOR_FEEDBACK"));
        var edited = reportCommands.update(studentActor, root.reportId(), UUID.randomUUID(),
                new UpdateReport(revised.version(), "Updated work", "Resolved", "Next iteration", List.of()));
        assertEquals(State.DRAFT, edited.state()); assertEquals(2, edited.revisionNo());
        assertEquals(frozen, store.roots.get(root.reportId()).revision(1));
        assertEquals("Report feedback", frozen.review().comment()); assertEquals(mentor, frozen.review().reviewerUserId());
        assertEquals(first, comments.list(studentActor, assignmentId, null, null).content().getFirst());
        assertNull(target.assignment().currentSubmissionId());
        assertEquals(3, target.assignment().version());
        assertEquals(1, reports.read(studentActor, root.reportId(), new ReportReadQuery(1)).revisionNo());
        assertEquals(1, reports.list(studentActor, new ReportQuery(caseId, State.DRAFT, null, null)).totalElements());
    }

    @Test void commentLedgerReplayUsesOriginalServerIdentityAndDoesNotAppendAgain() {
        var key = UUID.randomUUID(); var first = comment(key, "Discussion");
        clock.instant = now.plusSeconds(30);
        assertEquals(first, comment(key, "Discussion"));
        assertEquals(1, store.comments.size()); assertEquals(1, store.events.size()); assertEquals(1, store.cache.size());
        code(409, "IDEMPOTENCY_KEY_REUSED", () -> comment(key, "Different body"));
    }

    @Test void reportReplayRechecksCurrentPermissionBeforeReturningCachedBody() {
        var key = UUID.randomUUID(); var root = reportCommands.create(studentActor, key, sampleReport());
        assertEquals(root, reportCommands.create(studentActor, key, sampleReport()));
        deniedPermissions.add("internship:student:submission_write");
        code(403, "PERMISSION_DENIED", () -> reportCommands.create(studentActor, key, sampleReport()));
        assertEquals(1, store.roots.size()); assertEquals(1, store.events.size());
    }

    @Test void commentReplayRechecksRevokedAccountAndPermission() {
        var key = UUID.randomUUID(); comment(key, "Private"); revoked.add(student);
        code(401, "SESSION_EXPIRED", () -> comment(key, "Private")); revoked.clear();
        deniedPermissions.add("t3:comments:write");
        code(403, "PERMISSION_DENIED", () -> comment(key, "Private"));
        assertEquals(1, store.comments.size()); assertEquals(1, store.events.size());
    }

    @Test void mentorAssignmentRevocationBlocksReportReviewReplayAndTaskComment() {
        var root = draft(); var submitted = reportCommands.submit(studentActor, root.reportId(), UUID.randomUUID(), new SubmitReport(root.version()));
        var key = UUID.randomUUID(); var input = new ReportReview(submitted.version(), "Reviewed");
        reportCommands.review(mentorActor, root.reportId(), key, input);
        mentorAssigned = false;
        code(404, "RESOURCE_NOT_FOUND", () -> reportCommands.review(mentorActor, root.reportId(), key, input));
        code(404, "RESOURCE_NOT_FOUND", () -> commentCommands.create(mentorActor, assignmentId, UUID.randomUUID(), new CommentInput("No longer assigned")));
    }

    @Test void mentorMayDiscussTaskButOutsiderCannotWriteOrReadAnyHistory() {
        var created = commentCommands.create(mentorActor, assignmentId, UUID.randomUUID(), new CommentInput("Mentor discussion"));
        assertEquals(mentor, created.authorUserId());
        code(404, "RESOURCE_NOT_FOUND", () -> commentCommands.create(auth(outsider), assignmentId, UUID.randomUUID(), new CommentInput("Hidden")));
        var root = draft();
        code(404, "RESOURCE_NOT_FOUND", () -> reports.read(auth(outsider), root.reportId(), new ReportReadQuery(null)));
        code(404, "RESOURCE_NOT_FOUND", () -> comments.list(auth(outsider), assignmentId, null, null));
        code(404, "RESOURCE_NOT_FOUND", () -> reportCommands.update(auth(outsider), root.reportId(), UUID.randomUUID(),
                new UpdateReport(root.version(), "Denied", "Denied", "Denied", List.of())));
    }

    @Test void mentorExpiryWhileWaitingForCaseGuardDeniesReportReviewAndComment() {
        var root = draft(); var submitted = reportCommands.submit(studentActor, root.reportId(), UUID.randomUUID(), new SubmitReport(root.version()));
        mentorValidUntil = now.plusSeconds(1); duringCaseFence = () -> clock.instant = mentorValidUntil;
        code(404, "RESOURCE_NOT_FOUND", () -> reportCommands.review(mentorActor, root.reportId(), UUID.randomUUID(),
                new ReportReview(submitted.version(), "Expired while waiting")));
        assertEquals(State.SUBMITTED, store.roots.get(root.reportId()).current().state());
        clock.instant = now;
        code(404, "RESOURCE_NOT_FOUND", () -> commentCommands.create(mentorActor, assignmentId, UUID.randomUUID(), new CommentInput("Expired while waiting")));
        assertTrue(store.comments.isEmpty()); assertEquals(2, store.cache.size());
    }

    @Test void permissionRevokedWhileWaitingForGuardDeniesNewRoot() {
        duringCaseFence = () -> deniedPermissions.add("internship:student:submission_write");
        code(403, "PERMISSION_DENIED", this::draft);
        assertTrue(store.roots.isEmpty()); assertTrue(store.events.isEmpty()); assertTrue(store.cache.isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"CANCELLED", "COMPLETED", "APPROVED"})
    void authoritativePlacementStateBlocksNewEffectsEvenWithInProgressCase(String state) {
        placementState(state);
        code(409, "STATE_CONFLICT", this::draft);
        code(409, "STATE_CONFLICT", () -> comment(UUID.randomUUID(), "Blocked"));
        assertTrue(store.roots.isEmpty()); assertTrue(store.comments.isEmpty()); assertTrue(store.cache.isEmpty());
    }

    @Test void caseFenceDetectsConcurrentPlacementVersionDriftAndRollsBack() {
        duringCaseFence = () -> placementState("CANCELLED");
        code(409, "VERSION_CONFLICT", this::draft);
        assertTrue(store.roots.isEmpty()); assertTrue(store.events.isEmpty()); assertTrue(store.cache.isEmpty());
    }

    @Test void wrongCaseStudentTenantAndAssignmentNeverProduceEffects() {
        code(404, "RESOURCE_NOT_FOUND", () -> commentCommands.create(studentActor, UUID.randomUUID(), UUID.randomUUID(), new CommentInput("Wrong assignment")));
        var s = scope;
        currentScope = new WeeklyReportPorts.CaseScope(caseId, placement, outsider, school, enterprise, term, s.caseState(), s.placementState(),
                s.effectiveAt(), s.approvedAt(), s.startDate(), s.endDate(), s.caseVersion(), s.placementVersion());
        code(404, "RESOURCE_NOT_FOUND", () -> comment(UUID.randomUUID(), "Wrong owner"));
        code(404, "RESOURCE_NOT_FOUND", this::draft);
        currentScope = new WeeklyReportPorts.CaseScope(caseId, placement, student, UUID.randomUUID(), enterprise, term, s.caseState(), s.placementState(),
                s.effectiveAt(), s.approvedAt(), s.startDate(), s.endDate(), s.caseVersion(), s.placementVersion());
        code(404, "RESOURCE_NOT_FOUND", this::draft);
        assertTrue(store.events.isEmpty());
    }

    @Test void unavailableFencesFailClosedForBothWorkflows() {
        caseFenceAvailable = false;
        code(503, "DEPENDENCY_UNAVAILABLE", this::draft);
        code(503, "DEPENDENCY_UNAVAILABLE", () -> comment(UUID.randomUUID(), "No Case fence"));
        caseFenceAvailable = true; assignmentFenceAvailable = false;
        code(503, "DEPENDENCY_UNAVAILABLE", () -> comment(UUID.randomUUID(), "No assignment fence"));
        assertTrue(store.cache.isEmpty()); assertTrue(store.comments.isEmpty());
    }

    @Test void reportAuditFailureRollsBackEffectEventAndLedgerThenSameKeyCanRetry() {
        var key = UUID.randomUUID(); store.failAudit = true;
        code(503, "DEPENDENCY_UNAVAILABLE", () -> reportCommands.create(studentActor, key, sampleReport()));
        assertTrue(store.roots.isEmpty()); assertTrue(store.events.isEmpty()); assertTrue(store.cache.isEmpty());
        assertTrue(manager.rollbacks > 0);
        store.failAudit = false; reportCommands.create(studentActor, key, sampleReport());
        assertEquals(1, store.roots.size()); assertEquals(1, store.events.size()); assertEquals(1, store.cache.size());
    }

    @Test void commentAuditFailureRollsBackAppendEventAndLedgerThenSameKeyCanRetry() {
        var key = UUID.randomUUID(); store.failAudit = true;
        code(503, "DEPENDENCY_UNAVAILABLE", () -> comment(key, "Retry me"));
        assertTrue(store.comments.isEmpty()); assertTrue(store.events.isEmpty()); assertTrue(store.cache.isEmpty());
        store.failAudit = false; comment(key, "Retry me");
        assertEquals(1, store.comments.size()); assertEquals(1, store.events.size()); assertEquals(1, store.cache.size());
    }

    @Test void reviewAuditFailureRestoresSubmittedRevisionAndRetryWorks() {
        var root = draft(); var submitted = reportCommands.submit(studentActor, root.reportId(), UUID.randomUUID(), new SubmitReport(root.version()));
        var before = store.roots.get(root.reportId()); int events = store.events.size(), cache = store.cache.size();
        store.failAudit = true; var key = UUID.randomUUID(); var input = new ReportReview(submitted.version(), "Feedback");
        code(503, "DEPENDENCY_UNAVAILABLE", () -> reportCommands.review(mentorActor, root.reportId(), key, input));
        assertEquals(before, store.roots.get(root.reportId())); assertEquals(events, store.events.size()); assertEquals(cache, store.cache.size());
        store.failAudit = false;
        assertEquals(State.REVIEWED, reportCommands.review(mentorActor, root.reportId(), key, input).state());
    }

    @Test void revisionAuditFailureCannotMoveCurrentPointerOrChangeFrozenHistory() {
        var root = draft(); var submitted = reportCommands.submit(studentActor, root.reportId(), UUID.randomUUID(), new SubmitReport(root.version()));
        var before = store.roots.get(root.reportId()); var key = UUID.randomUUID();
        store.failAudit = true;
        code(503, "DEPENDENCY_UNAVAILABLE", () -> reportCommands.revision(studentActor, root.reportId(), key,
                new CreateReportRevision(submitted.version(), "CORRECTION")));
        assertEquals(before, store.roots.get(root.reportId())); assertEquals(2, store.cache.size());
        store.failAudit = false;
        var revised = reportCommands.revision(studentActor, root.reportId(), key, new CreateReportRevision(submitted.version(), "CORRECTION"));
        assertEquals(2, revised.revisionNo()); assertEquals(before.revision(1), store.roots.get(root.reportId()).revision(1));
    }

    @Test void commitFailureRollsBackMemoryAndLedgerAndAllowsRetry() {
        var key = UUID.randomUUID(); manager.failCommit = true;
        assertThrows(TransactionSystemException.class, () -> comment(key, "Commit retry"));
        assertTrue(store.comments.isEmpty()); assertTrue(store.events.isEmpty()); assertTrue(store.cache.isEmpty());
        manager.failCommit = false; comment(key, "Commit retry"); assertEquals(1, store.comments.size());
    }

    @Test void outerTransactionFailureRollsBackBothCommentAndReportEvenAfterNestedCommandsReturn() {
        assertThrows(IllegalStateException.class, () -> workspace.execute(session -> {
            comment(UUID.randomUUID(), "Nested"); draft(); throw new IllegalStateException("Outer failure");
        }));
        assertTrue(store.roots.isEmpty()); assertTrue(store.comments.isEmpty()); assertTrue(store.events.isEmpty()); assertTrue(store.cache.isEmpty());
    }

    @Test void draftPendingFileIsAllowedButSubmitRequiresCleanAndSameKeyCanRetry() {
        var root = draft(); var file = files.add(root.reportId(), "PENDING");
        var attached = reportCommands.update(studentActor, root.reportId(), UUID.randomUUID(),
                new UpdateReport(root.version(), root.completedWork(), root.difficulties(), root.nextPlan(), List.of(file)));
        var key = UUID.randomUUID(); var input = new SubmitReport(attached.version());
        code(409, "FILE_NOT_CLEAN", () -> reportCommands.submit(studentActor, root.reportId(), key, input));
        assertEquals(State.DRAFT, store.roots.get(root.reportId()).current().state());
        files.values.put(file, new FakeFileReadPort.File(student, root.reportId(), "WEEKLY_REPORT", "CLEAN"));
        assertEquals(State.SUBMITTED, reportCommands.submit(studentActor, root.reportId(), key, input).state());
    }

    @ParameterizedTest @ValueSource(strings = {"PENDING", "REJECTED"})
    void anyNotCleanEvidenceBlocksEntireSubmit(String scanStatus) {
        var root = draft(); var clean = files.add(root.reportId(), "CLEAN"); var pending = files.add(root.reportId(), "PENDING");
        var attached = reportCommands.update(studentActor, root.reportId(), UUID.randomUUID(),
                new UpdateReport(root.version(), "Work", "Issue", "Plan", List.of(clean, pending)));
        files.values.put(pending, new FakeFileReadPort.File(student, root.reportId(), "WEEKLY_REPORT", scanStatus));
        code(409, "FILE_NOT_CLEAN", () -> reportCommands.submit(studentActor, root.reportId(), UUID.randomUUID(), new SubmitReport(attached.version())));
        assertEquals(State.DRAFT, store.roots.get(root.reportId()).current().state());
        assertEquals(attached.version(), store.roots.get(root.reportId()).version()); assertEquals(2, store.cache.size());
    }

    @ParameterizedTest @ValueSource(strings = {"root", "owner", "purpose"})
    void bindingMismatchRollsBackWithoutCachingInvalidRequest(String mismatch) {
        var root = draft(); var file = files.add(root.reportId(), "CLEAN");
        files.values.put(file, new FakeFileReadPort.File("owner".equals(mismatch) ? outsider : student,
                "root".equals(mismatch) ? UUID.randomUUID() : root.reportId(), "purpose".equals(mismatch) ? "SUBMISSION" : "WEEKLY_REPORT", "CLEAN"));
        code(422, "FILE_BINDING_INVALID", () -> reportCommands.update(studentActor, root.reportId(), UUID.randomUUID(),
                new UpdateReport(root.version(), "Work", "Issue", "Plan", List.of(file))));
        assertEquals(1, store.cache.size()); assertEquals(root.version(), store.roots.get(root.reportId()).version());
    }

    @Test void unavailableFileProviderDoesNotBlockTextOnlyReportOrTaskComment() {
        files.available = false; var root = draft(); comment(UUID.randomUUID(), "Text only");
        var file = files.add(root.reportId(), "CLEAN");
        code(503, "DEPENDENCY_UNAVAILABLE", () -> reportCommands.update(studentActor, root.reportId(), UUID.randomUUID(),
                new UpdateReport(root.version(), "Work", "Issue", "Plan", List.of(file))));
        assertEquals(State.SUBMITTED, reportCommands.submit(studentActor, root.reportId(), UUID.randomUUID(), new SubmitReport(root.version())).state());
    }

    @Test void submitAtDueAtIsOnTimeButGuardWaitCrossingDeadlineMakesItLate() {
        var root = draft(); clock.instant = root.dueAt();
        duringCaseFence = () -> clock.instant = root.dueAt().plusNanos(1);
        var submitted = reportCommands.submit(studentActor, root.reportId(), UUID.randomUUID(), new SubmitReport(root.version()));
        assertTrue(submitted.lateFlag()); assertEquals(root.dueAt().plusNanos(1), submitted.submittedAt());
    }

    @Test void fileWaitCrossingDeadlineUsesTransitionTimeAndReplayKeepsIt() {
        var root = draft(); var file = files.add(root.reportId(), "CLEAN");
        var attached = reportCommands.update(studentActor, root.reportId(), UUID.randomUUID(), new UpdateReport(root.version(), "Work", "Issue", "Plan", List.of(file)));
        clock.instant = root.dueAt(); files.duringRead = () -> clock.instant = root.dueAt().plusNanos(1);
        var key = UUID.randomUUID(); var input = new SubmitReport(attached.version());
        var submitted = reportCommands.submit(studentActor, root.reportId(), key, input);
        assertTrue(submitted.lateFlag()); assertEquals(clock.instant(), submitted.submittedAt());
        clock.instant = root.dueAt().plusSeconds(3600);
        assertEquals(submitted, reportCommands.submit(studentActor, root.reportId(), key, input));
    }

    @Test void exactDeadlineIsOnTimeAndCommentTimeIsAfterItsGuards() {
        var root = draft(); clock.instant = root.dueAt();
        var submitted = reportCommands.submit(studentActor, root.reportId(), UUID.randomUUID(), new SubmitReport(root.version()));
        assertFalse(submitted.lateFlag());
        duringCaseFence = () -> clock.instant = root.dueAt().plusSeconds(1);
        assertEquals(root.dueAt().plusSeconds(1), comment(UUID.randomUUID(), "After guard").createdAt());
    }

    @Test void finalPermissionCheckMayAlsoWaitAcrossDeadline() {
        var root = draft(); var file = files.add(root.reportId(), "CLEAN");
        var attached = reportCommands.update(studentActor, root.reportId(), UUID.randomUUID(),
                new UpdateReport(root.version(), "Work", "Issue", "Plan", List.of(file)));
        clock.instant = root.dueAt();
        files.duringRead = () -> duringPermission = () -> clock.instant = root.dueAt().plusNanos(1);
        var submitted = reportCommands.submit(studentActor, root.reportId(), UUID.randomUUID(), new SubmitReport(attached.version()));
        assertTrue(submitted.lateFlag()); assertEquals(root.dueAt().plusNanos(1), submitted.submittedAt());
        files.duringRead = () -> {}; duringPermission = () -> {}; clock.instant = now;
        duringCaseFence = () -> duringPermission = () -> clock.instant = now.plusSeconds(1);
        assertEquals(now.plusSeconds(1), comment(UUID.randomUUID(), "After final permission check").createdAt());
    }

    @Test void twentyConcurrentSameKeyRequestsProduceOneReportAndOneComment() throws Exception {
        var key = UUID.randomUUID();
        var roots = parallel(20, () -> reportCommands.create(studentActor, key, sampleReport()));
        assertEquals(1, new HashSet<>(roots).size()); assertEquals(1, store.roots.size());
        var comments = parallel(20, () -> comment(key, "Same discussion"));
        assertEquals(1, new HashSet<>(comments).size()); assertEquals(1, store.comments.size()); assertEquals(2, store.events.size());
    }

    @Test void concurrentDistinctKeysRespectRootUniquenessAndStaleVersion() throws Exception {
        var outcomes = parallel(20, () -> result(() -> draft()));
        assertEquals(1, outcomes.stream().filter(v -> v.equals("OK")).count());
        assertEquals(19, outcomes.stream().filter(v -> v.equals("REPORT_ALREADY_EXISTS")).count());
        var root = store.roots.values().iterator().next();
        var edits = parallel(20, () -> result(() -> reportCommands.update(studentActor, root.reportId(), UUID.randomUUID(),
                new UpdateReport(root.version(), "Concurrent edit", "Issue", "Plan", List.of()))));
        assertEquals(1, edits.stream().filter(v -> v.equals("OK")).count());
        assertEquals(19, edits.stream().filter(v -> v.equals("VERSION_CONFLICT")).count());
        assertEquals(2, store.events.size()); assertEquals(2, store.cache.size());
    }

    @Test void concurrentDistinctCommentKeysAppendAllWithoutOverwriting() throws Exception {
        var created = parallel(20, () -> comment(UUID.randomUUID(), "Independent append"));
        assertEquals(20, new HashSet<>(created).size());
        var page = comments.list(studentActor, assignmentId, null, null);
        assertEquals(20, page.totalElements()); assertEquals(new HashSet<>(created), new HashSet<>(page.content()));
        assertEquals(20, store.events.size()); assertEquals(20, store.cache.size());
    }

    @Test void concurrentSubmitReviewRevisionReplayDoesNotDuplicateHistory() throws Exception {
        var root = draft(); var submitKey = UUID.randomUUID();
        var submitted = parallel(20, () -> reportCommands.submit(studentActor, root.reportId(), submitKey, new SubmitReport(root.version()))).getFirst();
        var reviewKey = UUID.randomUUID();
        var reviewed = parallel(20, () -> reportCommands.review(mentorActor, root.reportId(), reviewKey, new ReportReview(submitted.version(), "Feedback"))).getFirst();
        var frozen = store.roots.get(root.reportId()).revision(1); var revisionKey = UUID.randomUUID();
        var revised = parallel(20, () -> reportCommands.revision(studentActor, root.reportId(), revisionKey,
                new CreateReportRevision(reviewed.version(), "MENTOR_FEEDBACK")));
        assertEquals(1, new HashSet<>(revised).size()); assertEquals(2, store.roots.get(root.reportId()).revisions().size());
        assertEquals(frozen, store.roots.get(root.reportId()).revision(1)); assertEquals(4, store.events.size());
    }

    private String result(Supplier<?> operation) {
        try { operation.get(); return "OK"; } catch (OperationFailure error) { return error.code; }
    }
    private <T> List<T> parallel(int count, Supplier<T> operation) throws Exception {
        var ready = new CountDownLatch(count); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(count)) {
            var futures = new ArrayList<Future<T>>();
            for (int i = 0; i < count; i++) futures.add(pool.submit(() -> {
                ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                return operation.get();
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            var results = new ArrayList<T>();
            for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            return results;
        }
    }
}
