package vn.utbridora.auth.modules.internship.application;

import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.modules.internship.api.SubmissionDtos.CommentDto;
import vn.utbridora.auth.modules.internship.api.TaskDtos.AssignmentDto;
import vn.utbridora.auth.modules.internship.domain.*;
import static vn.utbridora.auth.modules.internship.domain.WeeklyReportPolicy.required;

/** FR-17 consumer ports only; schema ownership in V29 remains pending Lead confirmation.
 * Join existing Workspace UnitOfWork/manager. Owner locks, comment append, exact TASK_COMMENTED
 * audit and common ledger effect must commit/rollback together. No production providers here.
 */
public final class TaskCommentPorts {
    private TaskCommentPorts() {}
    /** Trusted owner projection, not a client DTO. Do not require current group membership here:
     * a historical Task keeps its group snapshot; current handover belongs to Access. */
    public record Target(Cohort cohort, InternshipGroup group, InternshipTask task, AssignmentDto assignment) {
        public Target {
            required(cohort, "cohort"); required(group, "group"); required(task, "task"); required(assignment, "assignment");
            if (!cohort.id().equals(group.cohortId()) || !group.id().equals(task.value().groupId())
                    || !task.value().assignments().contains(assignment)) throw new IllegalArgumentException("Inconsistent assignment target");
        }
        public boolean matches(WeeklyReportPorts.CaseScope scope) {
            return scope != null && assignment.caseId().equals(scope.caseId()) && assignment.studentUserId().equals(scope.studentUserId())
                    && cohort.scope().schoolOrgId().equals(scope.schoolOrgId()) && cohort.scope().enterpriseOrgId().equals(scope.enterpriseOrgId())
                    && cohort.scope().termId().equals(scope.termId());
        }
    }
    public interface Assignments {
        Target current(UUID assignmentId);
        /** Hold/recheck the actual assignment and immutable owner linkage until commit.
         * Never write DEV05's assignment repository from a comment adapter. */
        default Target holdForMutation(Target expected) {
            throw vn.utbridora.auth.operations.WorkflowValues.failure(503, "DEPENDENCY_UNAVAILABLE");
        }
    }
    /** Reuse the Task owner's CaseChecks with the real target; Report's generic Case fence
     * is not a prerequisite for FR-17. Keep authoritative source locks until commit. */
    public interface CaseGuard {
        void hold(Authentication actor, Target target,
                  WeeklyReportPorts.CaseScope expected, Instant now);
    }
    public interface Access {
        UUID currentUser(Authentication actor);
        /** Exact t3:comments:write + self student or current assigned Mentor/handover.
         * HR/ADMIN/enterprise membership alone never grants comment write. */
        void requireWrite(Authentication actor, Target target, WeeklyReportPorts.CaseScope scope, Instant now);
        /** t3:workspace:read + assignment-specific visibility, including historical Task handover. */
        void requireRead(Authentication actor, Target target, WeeklyReportPorts.CaseScope scope, Instant now);
    }
    public record AppendReceipt(CommentDto comment, long assignmentVersion) {
        public AppendReceipt {
            required(comment, "comment"); WeeklyReportPolicy.version(assignmentVersion);
        }
    }
    public record CommentPage(List<CommentDto> content, int page, int size, long totalElements, int totalPages) {
        public CommentPage {
            content = List.copyOf(required(content, "content"));
            vn.utbridora.auth.operations.WorkflowValues.page(page, size);
            if (totalElements < 0 || totalPages < 0) throw new IllegalArgumentException("Invalid comment page");
        }
    }
    public interface Session {
        /** Insert only; never update/delete old comments, review a submission, or change task state.
         * Return the authoritative assignmentVersion for E.3. Whether append bumps that version
         * is an owner/schema decision still pending; no expectedVersion field is added to wire input. */
        AppendReceipt append(Target locked, TaskComment comment);
        /** Filter this assignment before pagination; sort createdAt ASC,commentId ASC. */
        CommentPage comments(UUID assignmentId, int page, int size);
    }
    public record AuditEvent(UUID actorId, UUID assignmentId, UUID commentId, UUID schoolOrgId, long assignmentVersion) {
        public AuditEvent {
            required(actorId, "actorId"); required(assignmentId, "assignmentId"); required(commentId, "commentId");
            required(schoolOrgId, "schoolOrgId"); WeeklyReportPolicy.version(assignmentVersion);
        }
        public Map<String, Object> metadata() {
            return Map.of("commentId", commentId.toString(), "assignmentVersion", assignmentVersion);
        }
    }
    public interface Audit {
        /** TASK_COMMENTED, resourceType task_assignment, resourceId assignmentId, no free text. */
        void append(AuditEvent event);
    }
}
