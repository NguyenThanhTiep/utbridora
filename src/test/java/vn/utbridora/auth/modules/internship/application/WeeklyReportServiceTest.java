package vn.utbridora.auth.modules.internship.application;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import vn.utbridora.auth.modules.internship.api.ReportDtos.*;
import vn.utbridora.auth.modules.internship.domain.WeeklyReport;
import vn.utbridora.auth.operations.OperationFailure;
import static vn.utbridora.auth.modules.internship.application.WeeklyReportPorts.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Consumer-side unit tests. The transactional memory store exists only in this test, not as
 * production persistence, an ACTIVE Placement provider, or evidence of MySQL/QA acceptance. */
class WeeklyReportServiceTest {
    private final UUID caseId = UUID.randomUUID(), student = UUID.randomUUID(), mentor = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-10T16:59:59Z");
    private final Authentication actor = UsernamePasswordAuthenticationToken.authenticated("student-session", null, List.of());
    private final Authentication mentorActor = UsernamePasswordAuthenticationToken.authenticated("mentor-session", null, List.of());
    private final Cases cases = mock(Cases.class);
    private final Authority authority = mock(Authority.class);
    private final Assignments assignments = mock(Assignments.class);
    private final Files files = mock(Files.class);
    private final Memory memory = new Memory();
    private CaseScope scope;
    private WeeklyReportService service;

    @BeforeEach void setup() {
        scope = new CaseScope(caseId, UUID.randomUUID(), student, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "IN_PROGRESS", "ACTIVE", now.minusSeconds(3600), now.minusSeconds(7200),
                LocalDate.parse("2026-11-04"), LocalDate.parse("2026-12-31"), 1, 7);
        when(cases.current(caseId)).thenReturn(scope);
        // Test provider deliberately supplies the hold; real read-only providers default to DENY.
        doNothing().when(cases).holdForMutation(any());
        when(authority.currentUser(actor)).thenReturn(student); when(authority.currentUser(mentorActor)).thenReturn(mentor);
        when(assignments.isCurrentMentor(mentorActor, scope, now)).thenReturn(true);
        service = service(files, Clock.fixed(now, ZoneOffset.UTC));
    }
    private WeeklyReportService service(Files fileProvider, Clock clock) {
        return new WeeklyReportService(memory, cases, authority, assignments, fileProvider, memory, clock, UUID::randomUUID);
    }
    private ReportInput input() { return new ReportInput(caseId, LocalDate.parse("2026-11-02"), "Work", "", "Plan", List.of()); }
    private ReportDto draft() { return service.create(actor, input()); }
    private ReportDto submitted() {
        var draft = draft(); return service.submit(actor, draft.reportId(), new SubmitReport(draft.version()));
    }
    private UpdateReport update(long version, List<UUID> ids) { return new UpdateReport(version, "Updated work", "", "Next", ids); }

    @Test void completeWorkflowHasStableRootVersionsImmutableReviewAndExactAuditMetadata() {
        var draft = draft(); assertEquals(1L, draft.version()); assertEquals(1, draft.revisionNo());
        assertEquals(now, draft.dueAt()); assertFalse(draft.lateFlag());
        var submitted = service.submit(actor, draft.reportId(), new SubmitReport(1L));
        var reviewed = service.review(mentorActor, draft.reportId(), new ReportReview(2L, "First review"));
        var revised = service.createRevision(actor, draft.reportId(), new CreateReportRevision(3L, "FIX_REPORT"));
        var updated = service.update(actor, draft.reportId(), update(4, List.of()));
        var updatedAgain = service.update(actor, draft.reportId(), update(5, List.of()));
        assertEquals(6L, updatedAgain.version()); assertEquals(2, revised.revisionNo());
        var historic = service.read(actor, draft.reportId(), new ReportReadQuery(1));
        assertEquals(WeeklyReport.State.REVIEWED, historic.state()); assertEquals("Work", historic.completedWork());
        assertEquals(3L, historic.version()); assertEquals("Updated work", updated.completedWork());
        assertEquals(draft.reportId(), revised.reportId()); assertEquals(submitted.reportId(), reviewed.reportId());
        var root = memory.rows.get(draft.reportId());
        assertEquals("First review", root.revision(1).review().comment());
        assertEquals(mentor, root.revision(1).review().reviewerUserId());
        assertEquals(6, memory.events.size());
        assertEquals(Set.of("caseId", "weekStart", "revisionNo", "reportVersion"), memory.events.getFirst().metadata().keySet());
        assertEquals(Set.of("caseId", "weekStart", "revisionNo", "lateFlag"), memory.events.get(1).metadata().keySet());
        assertEquals(Set.of("caseId", "weekStart", "revisionNo", "reportVersion", "reasonCode"), memory.events.get(3).metadata().keySet());
        assertEquals(mentor, memory.events.get(2).actorId());
        assertFalse(memory.events.get(2).metadata().containsKey("comment"));
        verify(authority, times(2)).requirePermission(mentorActor, scope, "internship:enterprise:task_write", now);
    }
    @Test void clockControlsInclusiveDeadlineRegardlessOfJvmOrClockZone() {
        var report = draft();
        var atDue = service.submit(actor, report.reportId(), new SubmitReport(1L)); assertFalse(atDue.lateFlag());
        service.createRevision(actor, report.reportId(), new CreateReportRevision(2L, "FIX"));
        var later = service(files, Clock.fixed(now.plusNanos(1), ZoneId.of("America/New_York")));
        var late = later.submit(actor, report.reportId(), new SubmitReport(3L));
        assertTrue(late.lateFlag()); assertEquals(now.plusNanos(1), late.submittedAt());
        assertFalse(later.read(actor, report.reportId(), new ReportReadQuery(1)).lateFlag());
    }
    @Test void duplicateLogicalRootFailsWithoutAnotherSuccessAudit() {
        draft(); code(409, "REPORT_ALREADY_EXISTS", () -> draft());
        assertEquals(1, memory.rows.size()); assertEquals(1, memory.events.size());
    }
    @Test void secondWriterWithSameExpectedVersionCannotOverwriteNewDraft() {
        var draft = draft(); service.update(actor, draft.reportId(), update(1, List.of()));
        code(409, "VERSION_CONFLICT", () -> service.update(actor, draft.reportId(), update(1, List.of())));
        assertEquals(2, memory.events.size()); assertEquals(2L, memory.rows.get(draft.reportId()).version());
        memory.rejectCas = true;
        code(409, "VERSION_CONFLICT", () -> service.update(actor, draft.reportId(), update(2, List.of())));
        assertEquals(2, memory.events.size());
    }
    @Test void wrongStudentCannotCreateOrMutateAnotherStudentsReportEvenWithPermission() {
        var draft = draft(); when(authority.currentUser(actor)).thenReturn(UUID.randomUUID());
        code(404, "RESOURCE_NOT_FOUND", () -> draft());
        code(404, "RESOURCE_NOT_FOUND", () -> service.update(actor, draft.reportId(), update(1, List.of())));
        code(404, "RESOURCE_NOT_FOUND", () -> service.submit(actor, draft.reportId(), new SubmitReport(1L)));
        assertEquals(1, memory.events.size());
    }
    @Test void mentorPermissionAloneDoesNotGrantReviewForWrongGroupHrOrRevokedAssignment() {
        var submitted = submitted(); when(assignments.isCurrentMentor(mentorActor, scope, now)).thenReturn(false);
        code(404, "RESOURCE_NOT_FOUND", () -> service.review(mentorActor, submitted.reportId(), new ReportReview(2L, "Review")));
        assertEquals(WeeklyReport.State.SUBMITTED, memory.rows.get(submitted.reportId()).current().state());
        assertEquals(2, memory.events.size());
    }
    @Test void revokedPermissionIsRecheckedOnEveryMutationAndRead() {
        var draft = draft();
        doThrow(new OperationFailure(403, "ACCESS_DENIED", "Denied", null)).when(authority)
                .requirePermission(eq(actor), eq(scope), anyString(), eq(now));
        code(403, "ACCESS_DENIED", () -> service.update(actor, draft.reportId(), update(1, List.of())));
        code(403, "ACCESS_DENIED", () -> service.read(actor, draft.reportId(), new ReportReadQuery(null)));
        assertEquals(1, memory.events.size());
    }
    @ParameterizedTest @ValueSource(strings = {"APPROVED", "PENDING_APPROVAL", "CANCELLED", "COMPLETED"})
    void authoritativePlacementStateBlocksEvenIfCaseProjectionStillSaysInProgress(String state) {
        var draft = draft(); when(cases.current(caseId)).thenReturn(changedScope("IN_PROGRESS", state, scope.effectiveAt(), scope.approvedAt()));
        code(409, "STATE_CONFLICT", () -> service.update(actor, draft.reportId(), update(1, List.of())));
        code(409, "STATE_CONFLICT", () -> service.submit(actor, draft.reportId(), new SubmitReport(1L)));
        assertEquals(1, memory.events.size());
    }
    @Test void futureOrMissingEffectiveApprovalAndInactiveCaseAllFailClosed() {
        for (var blocked : List.of(changedScope("WAITING_START", "ACTIVE", now, now),
                changedScope("IN_PROGRESS", "ACTIVE", now.plusNanos(1), now),
                changedScope("IN_PROGRESS", "ACTIVE", null, now),
                changedScope("IN_PROGRESS", "ACTIVE", now, null),
                changedScope("IN_PROGRESS", "ACTIVE", now, now.plusNanos(1)))) {
            when(cases.current(caseId)).thenReturn(blocked); code(409, "STATE_CONFLICT", () -> draft());
        }
        assertTrue(memory.rows.isEmpty()); assertTrue(memory.events.isEmpty());
        when(cases.current(caseId)).thenReturn(changedScope("IN_PROGRESS", "ACTIVE", now, now));
        assertNotNull(draft());
    }
    @Test void readsDoNotRequireAnActivePlacementButAlwaysRequireCurrentReadScope() {
        var submitted = submitted();
        when(cases.current(caseId)).thenReturn(changedScope("COMPLETED", "COMPLETED", scope.effectiveAt(), scope.approvedAt()));
        assertEquals(submitted.reportId(), service.read(actor, submitted.reportId(), new ReportReadQuery(null)).reportId());
        when(cases.current(caseId)).thenReturn(scope);
        code(404, "RESOURCE_NOT_FOUND", () -> service.read(mentorActor, submitted.reportId(), new ReportReadQuery(null)));
        when(assignments.canRead(mentorActor, scope, now)).thenReturn(true);
        assertEquals(submitted.reportId(), service.read(mentorActor, submitted.reportId(), new ReportReadQuery(null)).reportId());
    }
    @Test void nonexistentHistoryDoesNotFallbackAndUnauthorizedReadersCannotProbeIt() {
        var draft = draft();
        code(404, "RESOURCE_NOT_FOUND", () -> service.read(actor, draft.reportId(), new ReportReadQuery(99)));
        code(404, "RESOURCE_NOT_FOUND", () -> service.read(mentorActor, draft.reportId(), new ReportReadQuery(99)));
        code(404, "RESOURCE_NOT_FOUND", () -> service.read(actor, UUID.randomUUID(), new ReportReadQuery(null)));
    }
    @Test void missingSecurityTransactionAuditOrCaseProviderCannotInstantiateUsableService() {
        code(503, "DEPENDENCY_UNAVAILABLE", () -> new WeeklyReportService(null, cases, authority, assignments, files, memory, Clock.systemUTC(), UUID::randomUUID));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> new WeeklyReportService(memory, null, authority, assignments, files, memory, Clock.systemUTC(), UUID::randomUUID));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> new WeeklyReportService(memory, cases, null, assignments, files, memory, Clock.systemUTC(), UUID::randomUUID));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> new WeeklyReportService(memory, cases, authority, null, files, memory, Clock.systemUTC(), UUID::randomUUID));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> new WeeklyReportService(memory, cases, authority, assignments, files, null, Clock.systemUTC(), UUID::randomUUID));
    }
    @Test void unavailableTransactionSessionCannotReturnASuccessOrRunWithoutGuards() {
        UnitOfWork missingSession = new UnitOfWork() {
            public <T> T execute(Function<Session, T> operation) { return operation.apply(null); }
        };
        var unavailable = new WeeklyReportService(missingSession, cases, authority, assignments, files, memory,
                Clock.fixed(now, ZoneOffset.UTC), UUID::randomUUID);
        code(503, "DEPENDENCY_UNAVAILABLE", () -> unavailable.create(actor, input()));
        UnitOfWork noResult = new UnitOfWork() {
            public <T> T execute(Function<Session, T> operation) { return null; }
        };
        var noResultService = new WeeklyReportService(noResult, cases, authority, assignments, files, memory,
                Clock.fixed(now, ZoneOffset.UTC), UUID::randomUUID);
        code(503, "DEPENDENCY_UNAVAILABLE", () -> noResultService.create(actor, input()));
        verifyNoInteractions(cases); assertTrue(memory.rows.isEmpty());
    }
    @Test void nullAuthorityResultsOrWrongCaseAndReportBindingsFailClosed() {
        when(cases.current(caseId)).thenReturn(null); code(503, "DEPENDENCY_UNAVAILABLE", () -> draft());
        when(cases.current(caseId)).thenReturn(new CaseScope(UUID.randomUUID(), scope.placementId(), student,
                scope.schoolOrgId(), scope.enterpriseOrgId(), scope.termId(), scope.caseState(), scope.placementState(),
                scope.effectiveAt(), scope.approvedAt(), scope.startDate(), scope.endDate(), scope.caseVersion(), scope.placementVersion()));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> draft());
        when(cases.current(caseId)).thenReturn(scope); when(authority.currentUser(actor)).thenReturn(null);
        code(503, "DEPENDENCY_UNAVAILABLE", () -> draft());
        assertTrue(memory.rows.isEmpty());
        when(authority.currentUser(actor)).thenReturn(student); var draft = draft();
        var alias = UUID.randomUUID(); memory.rows.put(alias, memory.rows.get(draft.reportId()));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> service.read(actor, alias, new ReportReadQuery(null)));
    }
    @Test void textOnlyWorkflowWorksWithoutFilesAndNonEmptyFileBranchFailsClosed() {
        var textOnly = service(null, Clock.fixed(now, ZoneOffset.UTC));
        var draft = textOnly.create(actor, input());
        code(503, "DEPENDENCY_UNAVAILABLE", () -> textOnly.update(actor, draft.reportId(), update(1, List.of(UUID.randomUUID()))));
        assertEquals(1L, memory.rows.get(draft.reportId()).version());
        assertEquals(WeeklyReport.State.SUBMITTED, textOnly.submit(actor, draft.reportId(), new SubmitReport(1L)).state());
    }
    @Test void draftPermitsPendingButSubmitRequiresCleanForTheSameStableRootAndStudent() {
        var draft = draft(); var ids = List.of(UUID.randomUUID());
        service.update(actor, draft.reportId(), update(1, ids));
        verify(files).requireBound(actor, student, draft.reportId(), ids, false);
        doThrow(new OperationFailure(422, "FILE_BINDING_INVALID", "Invalid file", null))
                .when(files).requireBound(actor, student, draft.reportId(), ids, true);
        code(422, "FILE_BINDING_INVALID", () -> service.submit(actor, draft.reportId(), new SubmitReport(2L)));
        assertEquals(WeeklyReport.State.DRAFT, memory.rows.get(draft.reportId()).current().state());
        doNothing().when(files).requireBound(actor, student, draft.reportId(), ids, true);
        var submitted = service.submit(actor, draft.reportId(), new SubmitReport(2L));
        service.createRevision(actor, draft.reportId(), new CreateReportRevision(submitted.version(), "FIX"));
        assertEquals(ids, memory.rows.get(draft.reportId()).current().content().evidenceFileIds());
    }
    @Test void initialNonEmptyEvidenceAndInvalidWeekNeverPersistOrAudit() {
        code(422, "FILE_BINDING_INVALID", () -> service.create(actor,
                new ReportInput(caseId, input().weekStart(), "Work", "", "Next", List.of(UUID.randomUUID()))));
        code(422, "REPORT_WEEK_INVALID", () -> service.create(actor,
                new ReportInput(caseId, input().weekStart().plusDays(1), "Work", "", "Next", List.of())));
        assertTrue(memory.rows.isEmpty()); assertTrue(memory.events.isEmpty()); verifyNoInteractions(files);
    }
    @Test void auditFailurePropagatesAndUnitOfWorkRollsBackCreateAndRevision() {
        memory.failAudit = true; code(500, "AUDIT_WRITE_FAILED", () -> draft());
        assertTrue(memory.rows.isEmpty()); assertTrue(memory.events.isEmpty());
        memory.failAudit = false; var submitted = submitted();
        memory.failAudit = true;
        code(500, "AUDIT_WRITE_FAILED", () -> service.createRevision(actor, submitted.reportId(), new CreateReportRevision(2L, "FIX")));
        assertEquals(1, memory.rows.get(submitted.reportId()).revisions().size()); assertEquals(2, memory.events.size());
    }
    @Test void listReturnsOnlyCurrentRevisionsFilteredBeforePaginationAndSortedByContract() {
        var first = submitted(); service.createRevision(actor, first.reportId(), new CreateReportRevision(2L, "FIX"));
        var second = service.create(actor, new ReportInput(caseId, input().weekStart().plusWeeks(1), "Second week", "", "Next", List.of()));
        var page = service.list(actor, new ReportQuery(caseId, WeeklyReport.State.DRAFT, 0, 1));
        assertEquals(2L, page.totalElements()); assertEquals(2, page.totalPages());
        assertEquals(second.reportId(), page.content().getFirst().reportId());
        var nextPage = service.list(actor, new ReportQuery(caseId, WeeklyReport.State.DRAFT, 1, 1));
        assertEquals(2, nextPage.content().getFirst().revisionNo());
        assertTrue(service.list(actor, new ReportQuery(caseId, WeeklyReport.State.SUBMITTED, 0, 10)).content().isEmpty());
        var empty = service.list(actor, new ReportQuery(caseId, null, 100, 25));
        assertTrue(empty.content().isEmpty()); assertEquals(2L, empty.totalElements());
    }
    @Test void listRevalidatesScopeAndRejectsForeignRowsFromBrokenProvider() {
        draft(); code(404, "RESOURCE_NOT_FOUND", () -> service.list(mentorActor, new ReportQuery(caseId, null, null, null)));
        var foreign = WeeklyReport.create(UUID.randomUUID(), UUID.randomUUID(), input().weekStart(), scope.startDate(), scope.endDate(), input().content());
        memory.overridePage = new PageSlice(List.of(foreign), 1);
        code(503, "DEPENDENCY_UNAVAILABLE", () -> service.list(actor, new ReportQuery(caseId, null, null, null)));
    }
    @Test void unauthenticatedAndAnonymousCallsNeverReachProviders() {
        var anonymous = new AnonymousAuthenticationToken("key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        for (Authentication invalid : Arrays.asList(null, anonymous, UsernamePasswordAuthenticationToken.unauthenticated("claimed-user", null))) {
            code(401, "SESSION_EXPIRED", () -> service.create(invalid, input()));
        }
        verifyNoInteractions(cases); assertTrue(memory.rows.isEmpty());
    }
    @Test void readOnlyCaseProviderCannotSilentlyAuthorizeMutation() {
        Cases snapshotsOnly = id -> scope;
        var readOnly = new WeeklyReportService(memory, snapshotsOnly, authority, assignments, files, memory,
                Clock.fixed(now, ZoneOffset.UTC), UUID::randomUUID);
        code(503, "DEPENDENCY_UNAVAILABLE", () -> readOnly.create(actor, input()));
        assertTrue(memory.rows.isEmpty()); assertTrue(memory.events.isEmpty());
    }
    @Test void caseFenceFailurePreventsInsertAndAudit() {
        doThrow(new OperationFailure(409, "VERSION_CONFLICT", "Stale Case", null)).when(cases).holdForMutation(scope);
        code(409, "VERSION_CONFLICT", () -> draft()); assertTrue(memory.rows.isEmpty()); assertTrue(memory.events.isEmpty());
    }
    @Test void ledgerPreflightChecksCurrentAuthorityWithoutApplyingStaleVersionOrState() {
        var submitted = submitted(); var preflight = service.authorizeChange(actor, submitted.reportId(), false);
        assertEquals(student, preflight.actorId()); assertEquals(scope.schoolOrgId(), preflight.organizationId());
        var mentorPreflight = service.authorizeChange(mentorActor, submitted.reportId(), true);
        assertEquals(scope.enterpriseOrgId(), mentorPreflight.organizationId());
        assertEquals(2, memory.events.size());
        when(assignments.isCurrentMentor(mentorActor, scope, now)).thenReturn(false);
        code(404, "RESOURCE_NOT_FOUND", () -> service.authorizeChange(mentorActor, submitted.reportId(), true));
    }
    @Test void listRejectsSameRootReturnedTwiceEvenWhenWeeksDiffer() {
        var first = draft();
        var forged = WeeklyReport.create(first.reportId(), caseId, input().weekStart().plusWeeks(1),
                scope.startDate(), scope.endDate(), input().content());
        memory.overridePage = new PageSlice(List.of(forged, memory.rows.get(first.reportId())), 2);
        code(503, "DEPENDENCY_UNAVAILABLE", () -> service.list(actor, new ReportQuery(caseId, null, null, null)));
    }
    @Test void restoredHistoryStillRequiresCurrentAuthorityAndExpectedVersion() {
        var original = WeeklyReport.create(UUID.randomUUID(), caseId, input().weekStart(), scope.startDate(), scope.endDate(), input().content())
                .submit(1, now).review(2, mentor, "Preserved review", now).revise(3, "FIX");
        var restored = WeeklyReport.restore(original.reportId(), original.key(), original.dueAt(), original.version(), original.revisions());
        memory.rows.put(restored.reportId(), restored);
        when(authority.currentUser(actor)).thenReturn(UUID.randomUUID());
        code(404, "RESOURCE_NOT_FOUND", () -> service.read(actor, restored.reportId(), new ReportReadQuery(1)));
        when(authority.currentUser(actor)).thenReturn(student);
        code(409, "VERSION_CONFLICT", () -> service.update(actor, restored.reportId(), update(1, List.of())));
        var updated = service.update(actor, restored.reportId(), update(4, List.of()));
        assertEquals(5L, updated.version());
        assertEquals("Preserved review", memory.rows.get(restored.reportId()).revision(1).review().comment());
    }

    private CaseScope changedScope(String caseState, String placementState, Instant effective, Instant approved) {
        return new CaseScope(caseId, scope.placementId(), student, scope.schoolOrgId(), scope.enterpriseOrgId(), scope.termId(),
                caseState, placementState, effective, approved, scope.startDate(), scope.endDate(), scope.caseVersion(), scope.placementVersion());
    }
    private void code(int status, String code, org.junit.jupiter.api.function.Executable action) {
        var failure = assertThrows(OperationFailure.class, action); assertEquals(status, failure.status); assertEquals(code, failure.code);
    }
    private static final class Memory implements UnitOfWork, Session, Audit {
        private Map<UUID, WeeklyReport> rows = new HashMap<>();
        private List<AuditEvent> events = new ArrayList<>();
        private boolean failAudit, rejectCas;
        private PageSlice overridePage;
        public synchronized <T> T execute(Function<Session, T> operation) {
            var oldRows = new HashMap<>(rows); var oldEvents = new ArrayList<>(events);
            try { return operation.apply(this); }
            catch (RuntimeException failure) { rows = oldRows; events = oldEvents; throw failure; }
        }
        public WeeklyReport find(UUID id) { return rows.get(id); }
        public boolean insertIfAbsent(WeeklyReport report) {
            if (rows.containsKey(report.reportId()) || rows.values().stream().anyMatch(r -> r.key().equals(report.key()))) return false;
            rows.put(report.reportId(), report); return true;
        }
        public boolean replace(WeeklyReport report, long expected) {
            if (rejectCas || rows.get(report.reportId()).version() != expected) return false;
            rows.put(report.reportId(), report); return true;
        }
        public PageSlice list(UUID caseId, WeeklyReport.State state, int page, int size) {
            if (overridePage != null) return overridePage;
            var all = rows.values().stream().filter(r -> r.key().caseId().equals(caseId))
                    .filter(r -> state == null || state == r.current().state())
                    .sorted(Comparator.comparing((WeeklyReport r) -> r.key().weekStart()).reversed()
                            .thenComparing(r -> r.reportId().toString())).toList();
            return new PageSlice(all.stream().skip((long) page * size).limit(size).toList(), all.size());
        }
        public void append(AuditEvent event) {
            if (failAudit) throw new OperationFailure(500, "AUDIT_WRITE_FAILED", "Audit failed", null);
            events.add(event);
        }
    }
}
