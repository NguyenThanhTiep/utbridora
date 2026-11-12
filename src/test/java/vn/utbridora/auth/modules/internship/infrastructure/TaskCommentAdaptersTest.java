package vn.utbridora.auth.modules.internship.infrastructure;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.utbridora.auth.audit.*;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.modules.internship.api.TaskDtos.*;
import vn.utbridora.auth.modules.internship.application.TaskCommentPorts.*;
import vn.utbridora.auth.modules.internship.domain.*;
import vn.utbridora.auth.user.Role;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskCommentAdaptersTest extends WeeklyReportAdapterTestSupport {
    private final WeeklyReportAccessAdapter identity = mock(WeeklyReportAccessAdapter.class);
    private final AuthorizationPolicy core = mock(AuthorizationPolicy.class);
    private final WorkspaceAuthority authority = mock(WorkspaceAuthority.class);
    private final WorkspaceRuntime runtime = mock(WorkspaceRuntime.class);
    private final TaskCommentAccessAdapter access = new TaskCommentAccessAdapter(identity, core, authority, runtime);
    private final UUID assignmentId = UUID.randomUUID(), taskId = UUID.randomUUID(), groupId = UUID.randomUUID(), cohortId = UUID.randomUUID();
    private Target target;
    @BeforeEach void setup() {
        var assignment = new AssignmentDto(assignmentId, caseId, student, AssignmentState.NEW, 3L, null);
        target = new Target(new Cohort(cohortId, new WorkspaceScope(enterprise, school, term), "C", "Cohort", 1),
                new InternshipGroup(groupId, cohortId, "G", List.of(caseId), List.of(mentor), 1),
                new InternshipTask(new TaskDto(taskId, groupId, "Task", "Description", now, TaskPriority.NORMAL, true, 1L, List.of(assignment))), assignment);
    }
    @Test void studentMustHoldExactCommentPermissionNotTheSubmissionPermission() {
        var actor = auth(student); when(identity.currentUser(actor)).thenReturn(student);
        when(core.holdOrganization(actor, school.toString(), Role.STUDENT, "internship:student:submission_write")).thenReturn(true);
        code(404, "RESOURCE_NOT_FOUND", () -> inTransaction(() -> { access.requireWrite(actor, target, scope, now); return true; }));
        when(core.holdOrganization(actor, school.toString(), Role.STUDENT, "t3:comments:write")).thenReturn(true);
        inTransaction(() -> { access.requireWrite(actor, target, scope, now); return true; });
        verify(core, times(2)).holdOrganization(actor, school.toString(), Role.STUDENT, "t3:comments:write");
        verifyNoInteractions(authority);
    }
    @Test void enterpriseOrHrPermissionCeilingDoesNotGrantMentorCommentWrite() {
        var actor = auth(mentor); when(identity.currentUser(actor)).thenReturn(mentor);
        when(core.holdOrganization(actor, enterprise.toString(), Role.ENTERPRISE, "t3:comments:write")).thenReturn(true);
        when(authority.selected(actor)).thenReturn(assignment("HR", mentor, groupId));
        code(404, "RESOURCE_NOT_FOUND", () -> inTransaction(() -> { access.requireWrite(actor, target, scope, now); return true; }));
        verify(authority, never()).canReadTask(any(), any(), any(), any());
    }
    @Test void mentorRequiresCurrentAssignmentAndOwnerHandoverDecisionForTheTaskSnapshot() {
        var actor = auth(mentor); when(identity.currentUser(actor)).thenReturn(mentor);
        when(core.holdOrganization(actor, enterprise.toString(), Role.ENTERPRISE, "t3:comments:write")).thenReturn(true);
        when(authority.selected(actor)).thenReturn(assignment("MENTOR", mentor, UUID.randomUUID()));
        when(authority.canReadTask(actor, target.cohort().scope(), target.task(), caseId)).thenReturn(false);
        code(404, "RESOURCE_NOT_FOUND", () -> inTransaction(() -> { access.requireWrite(actor, target, scope, now); return true; }));
        when(authority.canReadTask(actor, target.cohort().scope(), target.task(), caseId)).thenReturn(true);
        inTransaction(() -> { access.requireWrite(actor, target, scope, now); return true; });
        verify(authority, times(2)).canReadTask(actor, target.cohort().scope(), target.task(), caseId);
    }
    @Test void missingCurrentAssignmentFailsClosed() {
        var actor = auth(mentor); when(identity.currentUser(actor)).thenReturn(mentor);
        when(core.holdOrganization(actor, enterprise.toString(), Role.ENTERPRISE, "t3:comments:write")).thenReturn(true);
        when(authority.selected(actor)).thenReturn(null);
        code(503, "DEPENDENCY_UNAVAILABLE", () -> inTransaction(() -> { access.requireWrite(actor, target, scope, now); return true; }));
    }
    @Test void readRequiresBothWorkspacePermissionAndTheActualAssignmentInCurrentTaskView() {
        var actor = auth(student);
        when(runtime.readTask(actor, taskId)).thenReturn(target.task().value());
        inTransaction(() -> { access.requireRead(actor, target, scope, now); return true; });
        verify(identity).requirePermission(actor, scope, "t3:workspace:read", now);
        var other = new AssignmentDto(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AssignmentState.NEW, 1L, null);
        when(runtime.readTask(actor, taskId)).thenReturn(new TaskDto(taskId, groupId, "Task", "Description", now, TaskPriority.NORMAL, true, 1L, List.of(other)));
        code(404, "RESOURCE_NOT_FOUND", () -> inTransaction(() -> { access.requireRead(actor, target, scope, now); return true; }));
    }
    @Test void taskCommentAuditHasExactMetadataAndNoReportOrSubmissionActionFallback() {
        var audit = mock(AuditService.class); var adapter = new TaskCommentAuditAdapter(audit);
        var event = new AuditEvent(student, assignmentId, UUID.randomUUID(), school, 3);
        assertEquals(Set.of("commentId", "assignmentVersion"), event.metadata().keySet());
        boolean supported = Arrays.stream(AuditAction.values()).anyMatch(a -> a.name().equals("TASK_COMMENTED"));
        if (!supported) {
            code(503, "DEPENDENCY_UNAVAILABLE", () -> inTransaction(() -> { adapter.append(event); return true; }));
            verifyNoInteractions(audit);
        } else {
            inTransaction(() -> { adapter.append(event); return true; });
            verify(audit).workflow(student.toString(), "TASK_COMMENTED", assignmentId.toString(), school.toString(), event.metadata());
        }
    }
    private WorkspaceAuthority.Assignment assignment(String kind, UUID user, UUID group) {
        return new WorkspaceAuthority.Assignment(UUID.randomUUID().toString(), kind, user.toString(),
                new WorkspaceAuthority.Scope(school.toString(), enterprise.toString(), term.toString(), group.toString(), caseId.toString()),
                true, now.minusSeconds(10), now.plusSeconds(10), 1, UUID.randomUUID().toString(), "SYNTHETIC");
    }
}
