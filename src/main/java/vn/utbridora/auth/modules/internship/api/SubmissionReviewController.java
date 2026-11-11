package vn.utbridora.auth.modules.internship.api;

import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.internship.application.SubmissionService;
import vn.utbridora.auth.operations.OperationFailure;
import static vn.utbridora.auth.modules.internship.api.SubmissionDtos.*;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** CP-WS2 / FR-16 Review comment; never creates an FR-17 Task Comment. */
@RestController
@RequestMapping("/api/v1/t3/submissions/{submissionId}/reviews")
public final class SubmissionReviewController {
    private final ObjectProvider<SubmissionService> services;
    public SubmissionReviewController(ObjectProvider<SubmissionService> services) { this.services = services; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ReviewDto review(Authentication auth, @PathVariable String submissionId,
            @RequestHeader("Idempotency-Key") String key, @RequestBody String body) {
        actor(auth);
        var id = UUID.fromString(uuid(submissionId));
        var input = SubmissionJson.review(body);
        String normalizedKey;
        try { normalizedKey = uuid(key); }
        catch (OperationFailure invalid) { throw failure(400, "INVALID_REQUEST"); }
        var service = services.getIfAvailable();
        if (service == null) throw failure(503, "DEPENDENCY_UNAVAILABLE");
        return service.review(auth, id, normalizedKey, input);
    }
}
