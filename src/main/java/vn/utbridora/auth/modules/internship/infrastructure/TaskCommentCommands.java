package vn.utbridora.auth.modules.internship.infrastructure;

import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.modules.internship.api.SubmissionDtos.*;
import vn.utbridora.auth.modules.internship.api.SubmissionJson;
import vn.utbridora.auth.modules.internship.application.TaskCommentService;
import vn.utbridora.auth.modules.internship.application.WorkspacePorts;
import vn.utbridora.auth.operations.OperationLedger;
import static vn.utbridora.auth.modules.internship.infrastructure.WeeklyReportRuntimeChecks.*;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** Internal FR-17 command adapter. Existing transaction, parser and ledger only; no HTTP/bean. */
public final class TaskCommentCommands {
    private final WorkspacePorts.UnitOfWork transactions;
    private final TaskCommentService comments;
    private final OperationLedger ledger;
    public TaskCommentCommands(WorkspacePorts.UnitOfWork transactions, TaskCommentService comments, OperationLedger ledger) {
        this.transactions = available(transactions); this.comments = available(comments); this.ledger = available(ledger);
    }
    public CommentDto create(Authentication actor, UUID assignmentId, UUID key, String body) {
        return create(actor, assignmentId, key, SubmissionJson.comment(body));
    }
    public CommentDto create(Authentication actor, UUID assignmentId, UUID key, CommentInput input) {
        if (key == null || assignmentId == null) throw failure(400, "INVALID_REQUEST");
        available(input);
        return available(transactions.execute(session -> {
            available(session); mutationTransaction();
            var scope = available(comments.authorizeCreate(actor, assignmentId));
            var command = new OperationLedger.Command(available(scope.actorId()).toString(), available(scope.schoolOrgId()).toString(),
                    "T3_TASK_COMMENT_CREATE", key.toString(), Map.of("assignmentId", assignmentId, "request", input), 201);
            var result = available(ledger.execute(command, CommentDto.class,
                    () -> comments.create(actor, assignmentId, input), value -> value.commentId().toString()));
            if (!assignmentId.equals(result.assignmentId()) || !scope.actorId().equals(result.authorUserId())
                    || !input.text().equals(result.text())) throw unavailable();
            return result;
        }));
    }
}
