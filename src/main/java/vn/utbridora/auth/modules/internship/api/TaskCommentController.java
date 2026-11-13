package vn.utbridora.auth.modules.internship.api;

import java.util.Objects;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.internship.application.TaskCommentPorts.CommentPage;
import vn.utbridora.auth.modules.internship.application.TaskCommentService;
import vn.utbridora.auth.modules.internship.infrastructure.TaskCommentCommands;
import static vn.utbridora.auth.modules.internship.api.SubmissionDtos.*;
import static vn.utbridora.auth.modules.internship.api.Week3RequestValues.*;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** CP-WS2 FR-17 HTTP transport using scoped guards, shared audit and ledger. */
@RestController
@ConditionalOnProperty(name = HTTP_ENABLED, havingValue = "true")
@RequestMapping("/api/v1/t3/assignments/{assignmentId}/comments")
public final class TaskCommentController {
    private final TaskCommentService comments;
    private final TaskCommentCommands commands;
    public TaskCommentController(TaskCommentService comments, TaskCommentCommands commands) {
        this.comments = Objects.requireNonNull(comments); this.commands = Objects.requireNonNull(commands);
    }
    @PostMapping(consumes = "application/json") @ResponseStatus(HttpStatus.CREATED)
    public CommentDto create(Authentication auth, @PathVariable String assignmentId,
                             @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody String body) {
        actor(auth);
        return commands.create(auth, identifier(assignmentId), key(key), SubmissionJson.comment(body));
    }
    @GetMapping
    public CommentPage list(Authentication auth, @PathVariable String assignmentId,
                            @RequestParam MultiValueMap<String, String> parameters) {
        actor(auth); var id = identifier(assignmentId); var query = unique(parameters);
        if (!Set.of("page", "size").containsAll(query.keySet())) throw failure(400, "SCHEMA_VALIDATION");
        int page = integer(query, "page", 0), size = integer(query, "size", 25);
        page(page, size);
        return comments.list(auth, id, page, size);
    }
}
