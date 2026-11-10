package vn.utbridora.auth.modules.internship.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import vn.utbridora.auth.audit.AuditAction;
import vn.utbridora.auth.modules.internship.api.SubmissionDtos.*;
import vn.utbridora.auth.modules.internship.api.TaskDtos.*;
import vn.utbridora.auth.modules.internship.application.SubmissionPorts.*;
import vn.utbridora.auth.modules.internship.application.WorkspacePorts.CaseChecks;
import vn.utbridora.auth.modules.internship.domain.*;
import vn.utbridora.auth.operations.OperationFailure;
import vn.utbridora.auth.operations.OperationLedger;
import vn.utbridora.auth.security.AppPrincipal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** In-process application evidence only. All memory persistence/ledger/TX behavior below is a
 * TEST DOUBLE, not evidence of database locks, rollback, concurrent HTTP requests or Core integration.
 */
class SubmissionServiceTest {
    private static final UUID STUDENT = id(1), ASSIGNMENT = id(2), CASE = id(3), TASK = id(4),
            GROUP = id(5), COHORT = id(6), SCHOOL = id(7), ENTERPRISE = id(8), TERM = id(9), SUBMISSION = id(10);
    private static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");
    private static final String KEY = id(20).toString();
    private final Authentication auth = authentication(STUDENT);
    private final Access access = mock(Access.class);
    private final CaseChecks cases = mock(CaseChecks.class);
    private final MemoryBoundary memory = new MemoryBoundary();
    private SubmissionService service;

    @BeforeEach void setup() {
        memory.target = context(ASSIGNMENT, STUDENT, AssignmentState.IN_PROGRESS, 2L, null, NOW, false);
        when(cases.requireTaskParticipant(eq(auth), any(), any(), eq(CASE), any())).thenAnswer(call -> {
            memory.events.add("case-placement-guard"); return STUDENT;
        });
        doAnswer(call -> { memory.events.add("submit-authorization"); return null; })
                .when(access).requireSubmit(eq(auth), any());
        var sequence = new java.util.concurrent.atomic.AtomicLong(10);
        service = new SubmissionService(memory, access, cases, Clock.fixed(NOW, ZoneOffset.UTC),
                () -> id(sequence.getAndIncrement()));
    }

    private static UUID id(long value) { return new UUID(0, value); }
    private static Authentication authentication(UUID user) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AppPrincipal(user.toString(), "synthetic@example.test", null, 1, List.of()), null, List.of());
    }
    private static SubmissionInput input() { return new SubmissionInput(2L, List.of(), "https://example.test/v1", null); }
    private SubmissionDto submit() { return service.submitUrlV1(auth, ASSIGNMENT, KEY, input()); }

    private static AssignmentContext context(UUID assignmentId, UUID student, AssignmentState state, long version,
                                             UUID current, Instant due, boolean lateAllowed) {
        var cohort = new Cohort(COHORT, new WorkspaceScope(ENTERPRISE, SCHOOL, TERM), "C", "Cohort", 1);
        var group = new InternshipGroup(GROUP, COHORT, "G", List.of(CASE), List.of(), 1);
        var assignment = new AssignmentDto(assignmentId, CASE, student, state, version, current);
        var task = new InternshipTask(new TaskDto(TASK, GROUP, "Task", "Description", due,
                TaskPriority.NORMAL, lateAllowed, 1L, List.of(assignment)));
        return new AssignmentContext(cohort, group, task, assignment);
    }

    @Test void firstUrlSubmissionUsesServerTimeAndUpdatesOnlyItsAssignment() {
        var result = submit();
        assertEquals(new SubmissionDto(SUBMISSION, ASSIGNMENT, 1, List.of(), input().url(), null, NOW, false), result);
        assertEquals(AssignmentState.SUBMITTED, memory.target.assignment().state());
        assertEquals(3L, memory.target.assignment().version());
        assertEquals(SUBMISSION, memory.target.assignment().currentSubmissionId());
        assertEquals(List.of(result), memory.rows);
        assertEquals(List.of("read-assignment", "submit-authorization", "ledger", "case-placement-guard",
                "lock-assignment", "submit-authorization", "append", "audit"), memory.events);
        assertEquals(1, memory.commits);
    }

    @Test void usesExistingAuditActionAndOnlyContractMetadataWithoutUrlOrNote() {
        service.submitUrlV1(auth, ASSIGNMENT, KEY,
                new SubmissionInput(2L, List.of(), "https://example.test/private", "Private note"));
        var audit = memory.audits.getFirst();
        assertEquals(AuditAction.SUBMISSION_CREATED, audit.action());
        assertEquals(STUDENT, audit.actor());assertEquals(SCHOOL, audit.school());assertEquals(SUBMISSION, audit.resource());
        assertEquals(Map.of("assignmentId", ASSIGNMENT.toString(), "versionNo", 1, "fileIds", List.of(), "lateFlag", false), audit.metadata());
        assertFalse(audit.metadata().toString().contains("private"));
        assertFalse(audit.metadata().toString().contains("Private note"));
    }

    @Test void constructsCoreLedgerCommandWithServerActorOrgTargetAndCompletePayload() {
        submit();
        var command = memory.commands.getFirst();
        assertEquals(STUDENT.toString(), command.actorId());assertEquals(SCHOOL.toString(), command.organizationId());
        assertEquals("T3_SUBMISSION_CREATE", command.operationCode());assertEquals(KEY, command.key());
        assertEquals(201, command.responseStatus());
        assertEquals(Map.of("assignmentId", ASSIGNMENT, "request", input()), command.payload());
    }

    @Test void twentySequentialRetriesReturnTheOriginalValueAndHaveOneEffectInTheDouble() {
        var first = submit();
        for (int i = 1; i < 20; i++) assertEquals(first, submit());
        assertEquals(1, memory.rows.size());assertEquals(1, memory.audits.size());assertEquals(1, memory.callbacks);
        assertEquals(3L, memory.target.assignment().version());
        verify(access, times(21)).requireSubmit(eq(auth), any()); // 20 pre-replay + first mutation.
        verify(cases, times(1)).requireTaskParticipant(eq(auth), any(), any(), eq(CASE), any());
    }

    @ParameterizedTest @ValueSource(strings = {"url", "note", "version"})
    void changedPayloadWithSameKeyIsRejectedWithoutAnotherEffect(String field) {
        submit();
        var changed = new SubmissionInput(field.equals("version") ? 3L : 2L, List.of(),
                field.equals("url") ? "https://example.test/other" : input().url(), field.equals("note") ? "Changed" : null);
        error(409, "IDEMPOTENCY_KEY_REUSED", () -> service.submitUrlV1(auth, ASSIGNMENT, KEY, changed));
        assertEquals(1, memory.rows.size());assertEquals(1, memory.audits.size());
    }

    @Test void sameKeyForAnotherAssignmentIsAChangedPayloadInTheCoreActorOrgOperationScope() {
        var first = submit();
        var other = id(30);
        memory.target = context(other, STUDENT, AssignmentState.IN_PROGRESS, 2, null, NOW, false);
        error(409, "IDEMPOTENCY_KEY_REUSED", () -> service.submitUrlV1(auth, other, KEY, input()));
        assertEquals(1, memory.rows.size());assertEquals(1, memory.audits.size());
        var second = service.submitUrlV1(auth, other, id(21).toString(), input());
        assertEquals(other, second.assignmentId());assertEquals(ASSIGNMENT, first.assignmentId());
        assertNotEquals(first.submissionId(), second.submissionId());
        assertEquals(2, memory.callbacks);assertEquals(2, memory.rows.size());
    }

    @Test void freshKeyCannotCreateV1AgainAfterTheAssignmentWasSubmitted() {
        submit();
        error(409, "VERSION_CONFLICT", () -> service.submitUrlV1(auth, ASSIGNMENT, id(21).toString(), input()));
        assertEquals(1, memory.rows.size());assertEquals(1, memory.audits.size());
    }

    @Test void currentAuthorizationIsCheckedBeforeCachedReplay() {
        submit();
        doThrow(failure(403, "ACCESS_DENIED")).when(access).requireSubmit(eq(auth), any());
        error(403, "ACCESS_DENIED", this::submit);
        assertEquals(1, memory.commands.size());assertEquals(1, memory.rows.size());
    }

    static Stream<Arguments> ledgerErrors() {
        return Stream.of(Arguments.of(409, "REQUEST_IN_PROGRESS"), Arguments.of(409, "IDEMPOTENCY_RESPONSE_EXPIRED"),
                Arguments.of(503, "TRANSIENT_FAILURE"));
    }
    @ParameterizedTest @MethodSource("ledgerErrors")
    void ledgerFailuresPropagateWithoutExecutingMutation(int status, String code) {
        memory.ledgerFailure = failure(status, code);
        error(status, code, this::submit);
        assertEquals(0, memory.callbacks);assertTrue(memory.rows.isEmpty());assertTrue(memory.audits.isEmpty());
        verifyNoInteractions(cases);
    }

    @ParameterizedTest @ValueSource(strings = {"", "not-a-uuid", "1-1-1-1-1"})
    void invalidIdempotencyKeyNeverReachesTheLedger(String key) {
        error(400, "INVALID_REQUEST", () -> service.submitUrlV1(auth, ASSIGNMENT, key, input()));
        assertTrue(memory.commands.isEmpty());assertTrue(memory.rows.isEmpty());
    }

    @Test void nullKeyAndUnauthenticatedActorsFailClosed() {
        error(400, "INVALID_REQUEST", () -> service.submitUrlV1(auth, ASSIGNMENT, null, input()));
        Authentication unauthenticated = UsernamePasswordAuthenticationToken.unauthenticated("student", null);
        Authentication anonymous = new AnonymousAuthenticationToken("key", "anonymous",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        Authentication untrusted = UsernamePasswordAuthenticationToken.authenticated("browser-user", null, List.of());
        for (Authentication actor : java.util.Arrays.asList(null, unauthenticated, anonymous, untrusted)) {
            error(401, "SESSION_EXPIRED", () -> service.submitUrlV1(actor, ASSIGNMENT, KEY, input()));
            error(401, "SESSION_EXPIRED", () -> service.history(actor, ASSIGNMENT, 0, 25));
        }
        assertTrue(memory.rows.isEmpty());
    }

    @Test void anotherStudentAndMentorCannotSubmitForTheStoredOwner() {
        for (var other : List.of(authentication(id(60)), authentication(id(61)))) {
            error(404, "RESOURCE_NOT_FOUND", () -> service.submitUrlV1(other, ASSIGNMENT, KEY, input()));
        }
        verifyNoInteractions(access, cases);assertTrue(memory.commands.isEmpty());
    }

    @Test void unavailableOrForbiddenAuthorizationNeverReachesMutation() {
        doThrow(failure(503, "DEPENDENCY_UNAVAILABLE")).when(access).requireSubmit(eq(auth), any());
        error(503, "DEPENDENCY_UNAVAILABLE", this::submit);
        verifyNoInteractions(cases);assertTrue(memory.commands.isEmpty());
    }

    @Test void missingAssignmentReturnsNotFoundBeforeAuthorizationOrHistory() {
        memory.target = null;
        error(404, "RESOURCE_NOT_FOUND", this::submit);
        error(404, "RESOURCE_NOT_FOUND", () -> service.history(auth, ASSIGNMENT, 0, 25));
        verifyNoInteractions(access, cases);assertEquals(0, memory.historyReads);
    }

    @Test void wrongAssignmentReturnedByAdapterFailsClosed() {
        memory.target = context(id(55), STUDENT, AssignmentState.IN_PROGRESS, 2L, null, NOW, false);
        error(503, "DEPENDENCY_UNAVAILABLE", this::submit);
        verifyNoInteractions(access, cases);
    }

    @ParameterizedTest @EnumSource(value = AssignmentState.class, names = "IN_PROGRESS", mode = EnumSource.Mode.EXCLUDE)
    void onlyInProgressCanSubmit(AssignmentState state) {
        memory.target = context(ASSIGNMENT, STUDENT, state, 2L, null, NOW, false);
        error(409, "STATE_CONFLICT", this::submit);
        assertTrue(memory.rows.isEmpty());assertTrue(memory.audits.isEmpty());
    }

    @ParameterizedTest @ValueSource(longs = {0, 1, 3, 9_007_199_254_740_991L})
    void expectedAssignmentVersionMustMatch(long version) {
        error(409, "VERSION_CONFLICT", () -> service.submitUrlV1(auth, ASSIGNMENT, KEY,
                new SubmissionInput(version, List.of(), input().url(), null)));
        assertTrue(memory.rows.isEmpty());
    }

    @Test void rechecksVersionFromLockedAssignmentNotThePreAuthorizationRead() {
        memory.lockedOverride = context(ASSIGNMENT, STUDENT, AssignmentState.IN_PROGRESS, 3L, null, NOW, false);
        error(409, "VERSION_CONFLICT", this::submit);
        assertTrue(memory.rows.isEmpty());
    }

    @Test void authorizationIsRepeatedAfterLockBeforeWrite() {
        doNothing().doThrow(failure(403, "ACCESS_DENIED")).when(access).requireSubmit(eq(auth), any());
        error(403, "ACCESS_DENIED", this::submit);
        assertTrue(memory.rows.isEmpty());assertTrue(memory.audits.isEmpty());
    }

    @Test void cannotAdvanceMaxVersionOrExpandToV2() {
        long max = 9_007_199_254_740_991L;
        memory.target = context(ASSIGNMENT, STUDENT, AssignmentState.IN_PROGRESS, max, null, NOW, false);
        error(409, "VERSION_CONFLICT", () -> service.submitUrlV1(auth, ASSIGNMENT, KEY,
                new SubmissionInput(max, List.of(), input().url(), null)));
        memory.target = context(ASSIGNMENT, STUDENT, AssignmentState.IN_PROGRESS, 2, id(90), NOW, false);
        error(409, "STATE_CONFLICT", this::submit);
        assertTrue(memory.rows.isEmpty());
    }

    @Test void existingHistoryIsNeverOverwrittenEvenWithMissingCurrentPointer() {
        var first = row(1, id(90));memory.rows.add(first);
        error(409, "STATE_CONFLICT", this::submit);
        assertEquals(List.of(first), memory.rows);assertTrue(memory.audits.isEmpty());
    }

    static Stream<Arguments> guardErrors() {
        return Stream.of(Arguments.of(WorkspaceRuleException.Kind.STATE_CONFLICT),
                Arguments.of(WorkspaceRuleException.Kind.SCOPE_MISMATCH),
                Arguments.of(WorkspaceRuleException.Kind.UNAVAILABLE),
                Arguments.of(WorkspaceRuleException.Kind.VERSION_CONFLICT));
    }
    @ParameterizedTest @MethodSource("guardErrors")
    void casePlacementGuardErrorsAbortBeforeAssignmentLock(WorkspaceRuleException.Kind kind) {
        // Synthetic provider failures: state includes inactive Placement / terminal Case / future effectiveAt.
        when(cases.requireTaskParticipant(eq(auth), any(), any(), eq(CASE), any()))
                .thenThrow(new WorkspaceRuleException(kind));
        assertEquals(kind, assertThrows(WorkspaceRuleException.class, this::submit).kind());
        assertFalse(memory.events.contains("lock-assignment"));assertTrue(memory.rows.isEmpty());
    }

    @Test void missingCaseProviderAndWrongSourceStudentFailClosed() {
        when(cases.requireTaskParticipant(eq(auth), any(), any(), eq(CASE), any())).thenReturn(null);
        error(503, "DEPENDENCY_UNAVAILABLE", this::submit);
        when(cases.requireTaskParticipant(eq(auth), any(), any(), eq(CASE), any())).thenReturn(id(99));
        error(503, "DEPENDENCY_UNAVAILABLE", this::submit);
        assertTrue(memory.rows.isEmpty());assertFalse(memory.events.contains("lock-assignment"));
    }

    @Test void scopeOrOwnerChangeDuringLockCannotUseOldGuardResult() {
        memory.lockedOverride = context(ASSIGNMENT, id(99), AssignmentState.IN_PROGRESS, 2, null, NOW, false);
        error(503, "DEPENDENCY_UNAVAILABLE", this::submit);assertTrue(memory.rows.isEmpty());
    }

    @Test void fileBranchIsBlockedWithoutPretendingPendingFilesAreClean() {
        error(503, "DEPENDENCY_UNAVAILABLE", () -> service.submitUrlV1(auth, ASSIGNMENT, KEY,
                new SubmissionInput(2L, List.of(id(44)), null, null)));
        assertTrue(memory.commands.isEmpty());verifyNoInteractions(cases);
    }

    @Test void deadlineBoundaryIsInclusiveAndLateAllowedIsReadFromStoredTask() {
        assertFalse(submit().lateFlag());
        memory.target = context(id(31), STUDENT, AssignmentState.IN_PROGRESS, 2, null, NOW.minusNanos(1), true);
        assertTrue(service.submitUrlV1(auth, id(31), id(21).toString(), input()).lateFlag());
        memory.target = context(id(32), STUDENT, AssignmentState.IN_PROGRESS, 2, null, NOW.plusNanos(1), false);
        assertFalse(service.submitUrlV1(auth, id(32), id(22).toString(), input()).lateFlag());
    }

    @Test void lateForbiddenHasNoSuccessEffects() {
        memory.target = context(ASSIGNMENT, STUDENT, AssignmentState.IN_PROGRESS, 2, null, NOW.minusNanos(1), false);
        error(422, "SUBMISSION_LATE", this::submit);
        assertTrue(memory.rows.isEmpty());assertTrue(memory.audits.isEmpty());assertTrue(memory.ledger.isEmpty());
    }

    @Test void deadlineClockIsReadAfterSourceAndAssignmentLockWaits() {
        var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW, NOW.plusNanos(1));
        service = new SubmissionService(memory, access, cases, clock, () -> SUBMISSION);
        error(422, "SUBMISSION_LATE", this::submit);
        assertTrue(memory.rows.isEmpty());
    }

    @Test void persistenceCasFailurePropagatesAndDoesNotWriteAuditOrLedgerSuccess() {
        memory.appendFailure = failure(409, "VERSION_CONFLICT");
        error(409, "VERSION_CONFLICT", this::submit);
        assertTrue(memory.rows.isEmpty());assertTrue(memory.audits.isEmpty());assertTrue(memory.ledger.isEmpty());
    }

    @Test void auditFailureRollsBackTheTestDoubleAndRetryWithSameKeyCanSucceed() {
        memory.auditFailure = failure(500, "AUDIT_WRITE_FAILED");
        error(500, "AUDIT_WRITE_FAILED", this::submit);
        assertEquals(AssignmentState.IN_PROGRESS, memory.target.assignment().state());
        assertEquals(2L, memory.target.assignment().version());assertNull(memory.target.assignment().currentSubmissionId());
        assertTrue(memory.rows.isEmpty());assertTrue(memory.audits.isEmpty());assertTrue(memory.ledger.isEmpty());
        assertEquals(1, memory.rollbacks);
        memory.auditFailure = null;
        assertEquals(1, submit().versionNo());assertEquals(1, memory.rows.size());assertEquals(1, memory.audits.size());
    }

    @Test void readsImmutableStoredHistoryWithoutCreatingOrReconstructingRevisions() {
        var v1 = row(1, id(90));var v2 = row(2, id(91));
        memory.rows.addAll(List.of(v1, v2));
        var result = service.history(auth, ASSIGNMENT, 0, 25);
        assertEquals(List.of(v2, v1), result.content());assertEquals(2, result.totalElements());
        assertThrows(UnsupportedOperationException.class, () -> result.content().clear());
        memory.rows.clear();assertEquals(List.of(v2, v1), result.content());
        assertEquals("https://example.test/v1", v1.url());assertEquals("Note 1", v1.note());
        verify(access).requireRead(auth, memory.target);verifyNoInteractions(cases);
        assertTrue(memory.commands.isEmpty());assertTrue(memory.audits.isEmpty());
    }

    @Test void historyPaginationPreservesStorageCountAndVersionOrder() {
        for (int v = 1; v <= 4; v++) memory.rows.add(row(v, id(90 + v)));
        var result = service.history(auth, ASSIGNMENT, 1, 2);
        assertEquals(List.of(2, 1), result.content().stream().map(SubmissionDto::versionNo).toList());
        assertEquals(4, result.totalElements());assertEquals(List.of(1, 2), memory.lastPage);
        assertTrue(service.history(auth, ASSIGNMENT, 2, 2).content().isEmpty());
    }

    @Test void emptyHistoryIsNotAnInventedSubmission() {
        var result = service.history(auth, ASSIGNMENT, 0, 25);
        assertTrue(result.content().isEmpty());assertEquals(0, result.totalElements());
        assertTrue(memory.rows.isEmpty());assertTrue(memory.audits.isEmpty());
    }

    @Test void historyRequiresCurrentReadPermissionIncludingForAnOldVersion() {
        memory.rows.add(row(1, id(90)));
        doThrow(failure(404, "RESOURCE_NOT_FOUND")).when(access).requireRead(eq(auth), any());
        error(404, "RESOURCE_NOT_FOUND", () -> service.history(auth, ASSIGNMENT, 0, 25));
        assertEquals(0, memory.historyReads);verifyNoInteractions(cases);
    }

    @ParameterizedTest @ValueSource(ints = {-1, 10_001})
    void historyPageIsBounded(int page) {
        error(400, "INVALID_PAGINATION", () -> service.history(auth, ASSIGNMENT, page, 25));
        assertEquals(0, memory.historyReads);verifyNoInteractions(access);
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 101})
    void historySizeIsBounded(int size) {
        error(400, "INVALID_PAGINATION", () -> service.history(auth, ASSIGNMENT, 0, size));
        assertEquals(0, memory.historyReads);
    }

    @Test void foreignOrUnorderedHistoryFromAdapterIsRejected() {
        var foreign = new SubmissionDto(id(90), id(99), 1, List.of(), input().url(), null, NOW, false);
        memory.historyOverride = new HistoryPage(List.of(foreign), 1);
        error(503, "DEPENDENCY_UNAVAILABLE", () -> service.history(auth, ASSIGNMENT, 0, 25));
        memory.historyOverride = new HistoryPage(List.of(row(1, id(90)), row(2, id(91))), 2);
        error(503, "DEPENDENCY_UNAVAILABLE", () -> service.history(auth, ASSIGNMENT, 0, 25));
        memory.historyOverride = new HistoryPage(List.of(row(1, id(90)), row(2, id(91))), 2);
        error(503, "DEPENDENCY_UNAVAILABLE", () -> service.history(auth, ASSIGNMENT, 0, 1));
    }

    @Test void historyProjectionDefensivelyCopiesListsAndRejectsInvalidCounts() {
        var values = new ArrayList<>(List.of(row(1, id(90))));
        var page = new HistoryPage(values, 1);values.clear();
        assertEquals(1, page.content().size());
        assertThrows(IllegalArgumentException.class, () -> new HistoryPage(page.content(), 0));
        assertThrows(IllegalArgumentException.class, () -> new HistoryPage(List.of(), -1));
    }

    private static SubmissionDto row(int version, UUID id) {
        return new SubmissionDto(id, ASSIGNMENT, version, List.of(), "https://example.test/v" + version,
                "Note " + version, NOW.minusSeconds(10L - version), false);
    }
    private static void error(int status, String code, org.junit.jupiter.api.function.Executable call) {
        var error = assertThrows(OperationFailure.class, call);
        assertEquals(status, error.status);assertEquals(code, error.code);
    }

    private record Audit(AuditAction action, UUID actor, UUID school, UUID resource, Map<String, Object> metadata) {}
    private record Cached(String hash, SubmissionDto response) {}

    /** Test-only sequential transaction/ledger model. Runtime must use Core OperationLedger + real DB. */
    private static final class MemoryBoundary implements UnitOfWork, Session {
        AssignmentContext target, lockedOverride;
        final List<SubmissionDto> rows = new ArrayList<>();
        final List<Audit> audits = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        final Map<String, Cached> ledger = new HashMap<>();
        final List<OperationLedger.Command> commands = new ArrayList<>();
        RuntimeException auditFailure, appendFailure, ledgerFailure;
        HistoryPage historyOverride;
        List<Integer> lastPage;
        int commits, rollbacks, callbacks, historyReads;

        public <T> T execute(Function<Session, T> operation) {
            var beforeTarget = target;var beforeRows = List.copyOf(rows);
            var beforeAudits = List.copyOf(audits);var beforeLedger = Map.copyOf(ledger);
            try { T result = operation.apply(this);commits++;return result; }
            catch (RuntimeException failure) {
                target = beforeTarget;rows.clear();rows.addAll(beforeRows);audits.clear();audits.addAll(beforeAudits);
                ledger.clear();ledger.putAll(beforeLedger);rollbacks++;throw failure;
            }
        }
        public AssignmentContext assignment(UUID id) { events.add("read-assignment");return target; }
        public AssignmentContext lockAssignment(UUID id) {
            events.add("lock-assignment");return lockedOverride == null ? target : lockedOverride;
        }
        public SubmissionDto idempotent(OperationLedger.Command command, Supplier<SubmissionDto> mutation) {
            events.add("ledger");commands.add(command);
            if (ledgerFailure != null) throw ledgerFailure;
            String scope = command.actorId() + "|" + command.organizationId() + "|" + command.operationCode() + "|" + command.key();
            String hash = OperationLedger.payloadHash(command.payload());
            var previous = ledger.get(scope);
            if (previous != null) {
                if (!previous.hash().equals(hash)) throw failure(409, "IDEMPOTENCY_KEY_REUSED");
                return previous.response();
            }
            callbacks++;var result = mutation.get();ledger.put(scope, new Cached(hash, result));return result;
        }
        public boolean hasHistory(UUID id) { return rows.stream().anyMatch(row -> id.equals(row.assignmentId())); }
        public void appendFirst(AssignmentDto expected, SubmissionDto submission) {
            events.add("append");if (appendFailure != null) throw appendFailure;
            assertEquals(target.assignment(), expected);
            assertFalse(hasHistory(expected.assignmentId()));assertEquals(1, submission.versionNo());
            assertNull(expected.currentSubmissionId());assertEquals(AssignmentState.IN_PROGRESS, expected.state());
            rows.add(submission);
            target = context(expected.assignmentId(), expected.studentUserId(), AssignmentState.SUBMITTED,
                    expected.version() + 1, submission.submissionId(), target.task().value().dueAt(), target.task().value().lateAllowed());
        }
        public void audit(AuditAction action, UUID actorId, UUID schoolOrgId, UUID submissionId, Map<String, Object> metadata) {
            events.add("audit");if (auditFailure != null) throw auditFailure;
            audits.add(new Audit(action, actorId, schoolOrgId, submissionId, Map.copyOf(metadata)));
        }
        public HistoryPage history(UUID id, int page, int size) {
            historyReads++;lastPage = List.of(page, size);
            if (historyOverride != null) return historyOverride;
            var selected = rows.stream().filter(row -> id.equals(row.assignmentId()))
                    .sorted(java.util.Comparator.comparing(SubmissionDto::versionNo).reversed()
                            .thenComparing(row -> row.submissionId().toString())).toList();
            return new HistoryPage(selected.stream().skip((long) page * size).limit(size).toList(), selected.size());
        }
    }
}
