package vn.utbridora.auth.modules.internship.api;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import static vn.utbridora.auth.modules.internship.api.CohortGroupDtos.*;

/** CP-WS2 v1.0 create/read values only. These values do not authorize a mutation. */
public final class TaskDtos {
    private TaskDtos() {}

    public enum TaskPriority { LOW, NORMAL, HIGH }
    public enum AssignmentState { NEW, IN_PROGRESS, SUBMITTED, REVISION_REQUIRED, COMPLETED }

    public record CreateTask(UUID groupId, String title, String description, Instant dueAt,
                             TaskPriority priority, List<UUID> caseIds, Boolean lateAllowed) {
        public CreateTask {
            groupId = required(groupId, "groupId");
            title = text(title, 200, "title");
            description = text(description, 10_000, "description");
            dueAt = required(dueAt, "dueAt");
            priority = required(priority, "priority");
            caseIds = ids(caseIds, "caseIds");
            if (caseIds.isEmpty() || caseIds.size() > 1_000
                    || new HashSet<>(caseIds).size() != caseIds.size()) {
                throw new IllegalArgumentException("caseIds must contain 1..1000 distinct UUIDs");
            }
            lateAllowed = lateAllowed == null ? true : lateAllowed;
        }
    }

    public record AssignmentDto(UUID assignmentId, UUID caseId, UUID studentUserId,
                                AssignmentState state, Long version, UUID currentSubmissionId) {
        public AssignmentDto {
            assignmentId = required(assignmentId, "assignmentId");
            caseId = required(caseId, "caseId");
            studentUserId = required(studentUserId, "studentUserId");
            state = required(state, "state");
            version = CohortGroupDtos.version(version, "version");
        }
    }

    public record TaskDto(UUID taskId, UUID groupId, String title, String description,
                          Instant dueAt, TaskPriority priority, Boolean lateAllowed,
                          Long version, List<AssignmentDto> assignments) {
        public TaskDto {
            taskId = required(taskId, "taskId");
            groupId = required(groupId, "groupId");
            title = text(title, 200, "title");
            description = text(description, 10_000, "description");
            dueAt = required(dueAt, "dueAt");
            priority = required(priority, "priority");
            lateAllowed = required(lateAllowed, "lateAllowed");
            version = CohortGroupDtos.version(version, "version");
            required(assignments, "assignments");
            if (assignments.stream().anyMatch(value -> value == null)) {
                throw new IllegalArgumentException("assignments must not contain null");
            }
            assignments = List.copyOf(assignments);
        }
    }

    /** Effective filters; null optional fields mean no filter, never an authorization scope. */
    public record TaskQuery(UUID groupId, UUID caseId, AssignmentState state,
                            Instant dueFrom, Instant dueUntil, Integer page, Integer size) {
        public TaskQuery {
            page = CohortGroupDtos.page(page);
            size = pageSize(size);
        }
    }
}
