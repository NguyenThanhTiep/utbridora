package vn.utbridora.auth.modules.internship.infrastructure;

import java.util.UUID;
import vn.utbridora.auth.modules.internship.application.SubmissionPorts;
import vn.utbridora.auth.modules.internship.application.TaskCommentPorts;
import static vn.utbridora.auth.modules.internship.infrastructure.WeeklyReportRuntimeChecks.*;

/** Reads and locks through DEV05's owner adapter; never writes Assignment state/version. */
public final class TaskCommentAssignmentsAdapter implements TaskCommentPorts.Assignments {
    private final SubmissionPorts.Session owner;
    public TaskCommentAssignmentsAdapter(SubmissionPorts.Session owner) { this.owner = available(owner); }
    @Override public TaskCommentPorts.Target current(UUID id) {
        transaction(); return target(owner.assignment(id));
    }
    @Override public TaskCommentPorts.Target holdForMutation(TaskCommentPorts.Target expected) {
        mutationTransaction(); return target(owner.lockAssignment(expected.assignment().assignmentId()));
    }
    private static TaskCommentPorts.Target target(SubmissionPorts.AssignmentContext value) {
        return value == null ? null : new TaskCommentPorts.Target(value.cohort(), value.group(), value.task(), value.assignment());
    }
}
