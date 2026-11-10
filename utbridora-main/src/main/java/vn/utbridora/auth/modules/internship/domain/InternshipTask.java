package vn.utbridora.auth.modules.internship.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import vn.utbridora.auth.modules.internship.api.TaskDtos.*;

/** Task create/read only; group snapshot is retained when a member moves later. */
public record InternshipTask(TaskDto value) {
    public InternshipTask {
        Objects.requireNonNull(value, "value");
        var cases = new HashSet<UUID>();
        var assignments = new HashSet<UUID>();
        if (value.assignments().isEmpty() || value.assignments().size() > 1_000) {
            throw new IllegalArgumentException("Task requires 1..1000 assignments");
        }
        for (var assignment : value.assignments()) {
            if (!cases.add(assignment.caseId()) || !assignments.add(assignment.assignmentId())) {
                throw new IllegalArgumentException("Task assignments must be distinct");
            }
        }
    }
    public static InternshipTask create(UUID taskId, CreateTask input, List<AssignmentDto> assignments) {
        if (input == null || assignments == null || assignments.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Task input and assignments are required");
        }
        if (input.caseIds().size() != assignments.size()
                || !new HashSet<>(input.caseIds()).equals(new HashSet<>(assignments.stream().map(AssignmentDto::caseId).toList()))
                || assignments.stream().anyMatch(a -> a.state() != AssignmentState.NEW
                    || a.version() != 1 || a.currentSubmissionId() != null)) {
            throw new IllegalArgumentException("Initial assignments must match requested cases");
        }
        return new InternshipTask(new TaskDto(taskId, input.groupId(), input.title(), input.description(),
            input.dueAt(), input.priority(), input.lateAllowed(), 1L, assignments));
    }
}
