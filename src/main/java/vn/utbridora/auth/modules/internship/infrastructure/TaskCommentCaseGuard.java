package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.utbridora.auth.modules.internship.application.TaskCommentPorts;
import vn.utbridora.auth.modules.internship.application.WeeklyReportPorts;
import vn.utbridora.auth.modules.internship.application.WorkspacePorts;
import static vn.utbridora.auth.modules.internship.infrastructure.WeeklyReportRuntimeChecks.*;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** FR-17 bridge to existing CaseRuntime/CaseChecks, using the actual Task/cohort/group.
 * Does not query Case/T5 tables, manufacture a Task, or implement Report's pending Case fence.
 * RC is mandatory: owner reads after waiting for locks must not reuse a pre-lock RR snapshot.
 * No bean is installed; the final runtime composition must provide this transaction isolation.
 */
public final class TaskCommentCaseGuard implements TaskCommentPorts.CaseGuard {
    private final WorkspacePorts.CaseChecks owner;
    private final WeeklyReportPorts.Cases cases;
    public TaskCommentCaseGuard(WorkspacePorts.CaseChecks owner, WeeklyReportPorts.Cases cases) {
        this.owner = available(owner); this.cases = available(cases);
    }
    @Override public void hold(Authentication actor, TaskCommentPorts.Target target,
                               WeeklyReportPorts.CaseScope expected, Instant now) {
        mutationTransaction(); available(actor); available(target); available(expected); available(now);
        if (!Integer.valueOf(TransactionDefinition.ISOLATION_READ_COMMITTED).equals(
                TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())) throw unavailable();
        if (!target.matches(expected)) throw failure(404, "RESOURCE_NOT_FOUND");
        var student = workspace(() -> available(owner.requireTaskParticipant(actor, target.cohort(), target.group(), expected.caseId(), now)));
        if (!student.equals(expected.studentUserId())) throw failure(404, "RESOURCE_NOT_FOUND");
        var fresh = workspace(() -> available(cases.current(expected.caseId())));
        if (!expected.caseId().equals(fresh.caseId()) || !target.matches(fresh)
                || !expected.placementId().equals(fresh.placementId())) throw failure(404, "RESOURCE_NOT_FOUND");
        if (!expected.equals(fresh)) throw failure(409, "VERSION_CONFLICT");
    }
}
