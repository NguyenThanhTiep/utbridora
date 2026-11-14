package vn.utbridora.auth.modules.internship.api;

import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.internship.application.SubmissionService;
import vn.utbridora.auth.modules.internship.infrastructure.WorkspaceRuntime.Page;
import vn.utbridora.auth.operations.OperationFailure;
import static vn.utbridora.auth.modules.internship.api.SubmissionDtos.*;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** CP-WS2 HTTP adapter only. A real SubmissionService bean must be supplied by approved runtime
 * configuration. Missing runtime returns 503; no placeholder service or persistence is installed.
 * Existing SecurityConfig protects these routes with session authentication and CSRF.
 */
@RestController
@RequestMapping("/api/v1/t3/assignments/{assignmentId}/submissions")
public final class SubmissionController {
    private final ObjectProvider<SubmissionService> services;

    public SubmissionController(ObjectProvider<SubmissionService> services) { this.services = services; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubmissionDto submit(Authentication auth, @PathVariable String assignmentId,
                                @RequestHeader("Idempotency-Key") String key, @RequestBody String body) {
        actor(auth);
        var id = UUID.fromString(uuid(assignmentId));
        var input = SubmissionJson.submission(body);
        String normalizedKey;
        try { normalizedKey = uuid(key); }
        catch (OperationFailure invalid) { throw failure(400, "INVALID_REQUEST"); }
        // Resource ownership, current scoped permission and source guards belong to the service.
        return service().submitUrl(auth, id, normalizedKey, input);
    }

    @GetMapping
    public Page<SubmissionDto> history(Authentication auth, @PathVariable String assignmentId,
                                       @RequestParam(defaultValue="0") int page,
                                       @RequestParam(defaultValue="25") int size) {
        actor(auth);
        var id = UUID.fromString(uuid(assignmentId));
        page(page, size);
        var result = service().history(auth, id, page, size);
        long pages = Math.ceilDiv(result.totalElements(), (long) size);
        if (pages > Integer.MAX_VALUE) throw failure(500, "INTERNAL_ERROR");
        return new Page<>(result.content(), page, size, result.totalElements(), (int) pages);
    }

    private SubmissionService service() {
        var service = services.getIfAvailable();
        if (service == null) throw failure(503, "DEPENDENCY_UNAVAILABLE");
        return service;
    }
}
