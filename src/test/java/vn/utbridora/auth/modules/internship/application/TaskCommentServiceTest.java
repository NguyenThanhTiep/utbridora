package vn.utbridora.auth.modules.internship.application;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import vn.utbridora.auth.modules.internship.api.SubmissionDtos.*;
import vn.utbridora.auth.modules.internship.api.TaskDtos.*;
import vn.utbridora.auth.modules.internship.domain.*;
import vn.utbridora.auth.operations.OperationFailure;
import static vn.utbridora.auth.modules.internship.application.TaskCommentPorts.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** No database or production fixture. Comment state is isolated from Submission and Report reviews. */
class TaskCommentServiceTest {
    private final UUID caseId = UUID.randomUUID(), student = UUID.randomUUID(), mentor = UUID.randomUUID(),
            assignmentId = UUID.randomUUID(), taskId = UUID.randomUUID(), groupId = UUID.randomUUID(), cohortId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-10T00:00:00Z");
    private final WorkspaceScope workspace = new WorkspaceScope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    private final WeeklyReportPorts.CaseScope scope = new WeeklyReportPorts.CaseScope(caseId, UUID.randomUUID(), student,
            workspace.schoolOrgId(), workspace.enterpriseOrgId(), workspace.termId(), "IN_PROGRESS", "ACTIVE",
            now.minusSeconds(10), now.minusSeconds(20), LocalDate.parse("2026-11-01"), LocalDate.parse("2027-01-01"), 2, 8);
    private final Authentication actor = UsernamePasswordAuthenticationToken.authenticated("not-an-owner-id", null, List.of());
    private final Access access = mock(Access.class);
    private final Assignments assignments = mock(Assignments.class);
    private final WeeklyReportPorts.Cases cases = mock(WeeklyReportPorts.Cases.class);
    private final CaseGuard caseGuard = mock(CaseGuard.class);
    private final Memory memory = new Memory();
    private Target target;
    private TaskCommentService service;
    private Target target(AssignmentState state, long version, UUID task) {
        var assignment = new AssignmentDto(assignmentId, caseId, student, state, version, null);
        return new Target(new Cohort(cohortId, workspace, "C", "Cohort", 1),
                new InternshipGroup(groupId, cohortId, "G", List.of(caseId), List.of(mentor), 1),
                new InternshipTask(new TaskDto(task, groupId, "Task", "Description", now, TaskPriority.NORMAL, true, 1L, List.of(assignment))), assignment);
    }
    private TaskCommentService service(Assignments owner, java.util.function.Supplier<Session> sessions) {
        return new TaskCommentService(memory, sessions, owner, cases, caseGuard, access, memory, Clock.fixed(now, ZoneOffset.UTC), UUID::randomUUID);
    }
    @BeforeEach void setup() {
        target = target(AssignmentState.NEW, 3, taskId);
        when(assignments.current(assignmentId)).thenReturn(target);
        when(assignments.holdForMutation(target)).thenReturn(target);
        when(cases.current(caseId)).thenReturn(scope);
        when(access.currentUser(actor)).thenReturn(student);
        service = service(assignments, () -> memory);
    }
    @Test void appendsAndListsHistoryWithoutOverwritingOrChangingAssignmentState() {
        var first = service.create(actor, assignmentId, new CommentInput(" First "));
        var second = service.create(actor, assignmentId, new CommentInput("Second"));
        var page = service.list(actor, assignmentId, null, null);
        assertEquals(2, page.totalElements()); assertEquals(Set.of(first, second), new HashSet<>(page.content()));
        assertEquals(student, first.authorUserId()); assertEquals(now, first.createdAt()); assertEquals("First", first.text());
        assertEquals(AssignmentState.NEW, target.assignment().state()); assertNull(target.assignment().currentSubmissionId());
        assertEquals(3, target.assignment().version());
        assertEquals(Set.of("commentId", "assignmentVersion"), memory.events.getFirst().metadata().keySet());
        assertEquals(assignmentId, memory.events.getFirst().assignmentId());
        assertFalse(memory.events.getFirst().metadata().containsKey("text"));
    }
    @ParameterizedTest @EnumSource(AssignmentState.class)
    void commentDoesNotInventASubmissionOrAssignmentStateTransition(AssignmentState state) {
        var current = target(state, 3, taskId);
        when(assignments.current(assignmentId)).thenReturn(current); when(assignments.holdForMutation(current)).thenReturn(current);
        service.create(actor, assignmentId, new CommentInput("Discussion"));
        assertEquals(state, current.assignment().state()); assertEquals(1, memory.comments.size());
    }
    @Test void mentorAuthorIsResolvedByAuthorityNotByInputOrAuthenticationName() {
        when(access.currentUser(actor)).thenReturn(mentor);
        var created = service.create(actor, assignmentId, new CommentInput("Mentor reply"));
        assertEquals(mentor, created.authorUserId()); assertEquals(mentor, memory.events.getFirst().actorId());
        verify(access, times(2)).requireWrite(actor, target, scope, now);
    }
    @Test void forbiddenWriterNeverAcquiresMutationLocksOrPersists() {
        doThrow(new OperationFailure(404, "RESOURCE_NOT_FOUND", "Denied", null)).when(access).requireWrite(actor, target, scope, now);
        code(404, "RESOURCE_NOT_FOUND", () -> service.create(actor, assignmentId, new CommentInput("Hidden")));
        verify(assignments, never()).holdForMutation(any()); verifyNoInteractions(caseGuard);
        assertTrue(memory.comments.isEmpty()); assertTrue(memory.events.isEmpty());
    }
    @Test void currentCancelledPlacementBlocksEvenWhenCaseProjectionIsStillInProgress() {
        when(cases.current(caseId)).thenReturn(new WeeklyReportPorts.CaseScope(caseId, scope.placementId(), student,
                scope.schoolOrgId(), scope.enterpriseOrgId(), scope.termId(), "IN_PROGRESS", "CANCELLED", scope.effectiveAt(),
                scope.approvedAt(), scope.startDate(), scope.endDate(), 2, 9));
        code(409, "STATE_CONFLICT", () -> service.create(actor, assignmentId, new CommentInput("Blocked")));
        assertTrue(memory.comments.isEmpty());
    }
    @Test void historicalReadRemainsAvailableAfterCaseAndPlacementClose() {
        var created = service.create(actor, assignmentId, new CommentInput("Original"));
        when(cases.current(caseId)).thenReturn(new WeeklyReportPorts.CaseScope(caseId, scope.placementId(), student,
                scope.schoolOrgId(), scope.enterpriseOrgId(), scope.termId(), "COMPLETED", "COMPLETED", scope.effectiveAt(),
                scope.approvedAt(), scope.startDate(), scope.endDate(), 3, 9));
        assertEquals(created, service.list(actor, assignmentId, 0, 1).content().getFirst());
        code(409, "STATE_CONFLICT", () -> service.create(actor, assignmentId, new CommentInput("No new comment")));
    }
    @Test void wrongTenantOrStudentScopeFromOwnerIsRejectedBeforeReadingHistory() {
        when(cases.current(caseId)).thenReturn(new WeeklyReportPorts.CaseScope(caseId, scope.placementId(), UUID.randomUUID(),
                scope.schoolOrgId(), scope.enterpriseOrgId(), scope.termId(), scope.caseState(), scope.placementState(), scope.effectiveAt(),
                scope.approvedAt(), scope.startDate(), scope.endDate(), 2, 8));
        code(404, "RESOURCE_NOT_FOUND", () -> service.list(actor, assignmentId, null, null));
        code(404, "RESOURCE_NOT_FOUND", () -> service.create(actor, assignmentId, new CommentInput("Hidden")));
        assertTrue(memory.events.isEmpty());
    }
    @Test void assignmentFenceRejectsVersionDriftOrResourceSubstitution() {
        when(assignments.holdForMutation(target)).thenReturn(target(AssignmentState.NEW, 4, taskId));
        code(409, "VERSION_CONFLICT", () -> service.create(actor, assignmentId, new CommentInput("Stale")));
        when(assignments.holdForMutation(target)).thenReturn(target(AssignmentState.NEW, 3, UUID.randomUUID()));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> service.create(actor, assignmentId, new CommentInput("Wrong resource")));
        assertTrue(memory.comments.isEmpty());
    }
    @Test void missingProvidersAndReadOnlyOwnerProjectionFailClosed() {
        code(503, "DEPENDENCY_UNAVAILABLE", () -> service(assignments, () -> null).create(actor, assignmentId, new CommentInput("No session")));
        Assignments readonly = id -> target;
        code(503, "DEPENDENCY_UNAVAILABLE", () -> service(readonly, () -> memory).create(actor, assignmentId, new CommentInput("No fence")));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> new TaskCommentService(memory, () -> memory, assignments, cases, caseGuard, null, memory, Clock.systemUTC(), UUID::randomUUID));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> new TaskCommentService(memory, () -> memory, assignments, cases, null, access, memory, Clock.systemUTC(), UUID::randomUUID));
        when(cases.current(caseId)).thenReturn(null);
        code(503, "DEPENDENCY_UNAVAILABLE", () -> service.create(actor, assignmentId, new CommentInput("No Case")));
        assertTrue(memory.comments.isEmpty());
    }
    @Test void auditFailureRollsBackTheCommentInTheConsumerTransaction() {
        memory.failAudit = true;
        code(503, "DEPENDENCY_UNAVAILABLE", () -> service.create(actor, assignmentId, new CommentInput("Must rollback")));
        assertTrue(memory.comments.isEmpty()); assertTrue(memory.events.isEmpty());
    }
    @Test void appendReceiptCannotSubstituteAuthorTextOrAssignmentVersion() {
        memory.badReceipt = true;
        code(503, "DEPENDENCY_UNAVAILABLE", () -> service.create(actor, assignmentId, new CommentInput("Original")));
        assertTrue(memory.comments.isEmpty()); assertTrue(memory.events.isEmpty());
    }
    @Test void listChecksAssignmentBindingOrderPaginationAndDuplicateIds() {
        service.create(actor, assignmentId, new CommentInput("First"));
        var foreign = new CommentDto(UUID.randomUUID(), UUID.randomUUID(), student, "Other", now);
        memory.override = new CommentPage(List.of(foreign), 0, 25, 1, 1);
        code(503, "DEPENDENCY_UNAVAILABLE", () -> service.list(actor, assignmentId, 0, 25));
        var own = memory.comments.getFirst(); memory.override = new CommentPage(List.of(own, own), 0, 25, 2, 1);
        code(503, "DEPENDENCY_UNAVAILABLE", () -> service.list(actor, assignmentId, 0, 25));
        memory.override = new CommentPage(List.of(own), 1, 25, 1, 1);
        code(503, "DEPENDENCY_UNAVAILABLE", () -> service.list(actor, assignmentId, 0, 25));
    }
    @Test void listReadRevocationCannotReturnStoredText() {
        service.create(actor, assignmentId, new CommentInput("Private discussion"));
        doThrow(new OperationFailure(404, "RESOURCE_NOT_FOUND", "Denied", null)).when(access).requireRead(actor, target, scope, now);
        code(404, "RESOURCE_NOT_FOUND", () -> service.list(actor, assignmentId, null, null));
    }
    @Test void preflightRechecksWriterWithoutAppendingOrRequiringClientVersion() {
        var checked = service.authorizeCreate(actor, assignmentId);
        assertEquals(student, checked.actorId()); assertEquals(scope.schoolOrgId(), checked.schoolOrgId());
        assertTrue(memory.comments.isEmpty()); verify(assignments, never()).holdForMutation(any());
    }
    @Test void unauthenticatedCallsAndInvalidPaginationNeverReachStores() {
        code(401, "SESSION_EXPIRED", () -> service.create(null, assignmentId, new CommentInput("No actor")));
        var anonymous = new AnonymousAuthenticationToken("key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        code(401, "SESSION_EXPIRED", () -> service.list(anonymous, assignmentId, null, null));
        code(400, "INVALID_PAGINATION", () -> service.list(actor, assignmentId, 0, 101));
        verifyNoInteractions(access); assertTrue(memory.comments.isEmpty());
    }
    private void code(int status, String code, org.junit.jupiter.api.function.Executable call) {
        var error = assertThrows(OperationFailure.class, call); assertEquals(status, error.status); assertEquals(code, error.code);
    }
    private static final class Memory implements WorkspacePorts.UnitOfWork, Session, Audit {
        List<CommentDto> comments = new ArrayList<>(); List<AuditEvent> events = new ArrayList<>();
        CommentPage override; boolean failAudit, badReceipt;
        public <T> T execute(Function<WorkspacePorts.Session, T> call) {
            var oldComments = new ArrayList<>(comments); var oldEvents = new ArrayList<>(events);
            try { return call.apply(mock(WorkspacePorts.Session.class)); }
            catch (RuntimeException error) { comments = oldComments; events = oldEvents; throw error; }
        }
        public AppendReceipt append(Target locked, TaskComment comment) {
            comments.add(comment.value());
            var returned = badReceipt ? new CommentDto(comment.value().commentId(), comment.value().assignmentId(), UUID.randomUUID(), "Wrong", comment.value().createdAt()) : comment.value();
            return new AppendReceipt(returned, locked.assignment().version());
        }
        public CommentPage comments(UUID id, int page, int size) {
            if (override != null) return override;
            var all = comments.stream().filter(c -> c.assignmentId().equals(id))
                    .sorted(Comparator.comparing(CommentDto::createdAt).thenComparing(c -> c.commentId().toString())).toList();
            return new CommentPage(all.stream().skip((long) page * size).limit(size).toList(), page, size, all.size(), (all.size() + size - 1) / size);
        }
        public void append(AuditEvent event) {
            if (failAudit) throw new OperationFailure(503, "DEPENDENCY_UNAVAILABLE", "Missing TASK_COMMENTED", null);
            events.add(event);
        }
    }
}
