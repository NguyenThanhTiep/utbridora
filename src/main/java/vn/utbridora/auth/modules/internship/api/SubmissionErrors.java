package vn.utbridora.auth.modules.internship.api;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException;
import vn.utbridora.auth.operations.OperationErrors;
import vn.utbridora.auth.operations.OperationFailure;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** Scoped transport/domain mapping. Existing OperationErrors owns OperationFailure handling,
 * including Retry-After, server request IDs and denial audit. No legacy controller is affected.
 */
@RestControllerAdvice(assignableTypes=SubmissionController.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public final class SubmissionErrors {
    private final OperationErrors operations;

    public SubmissionErrors(OperationErrors operations) { this.operations = operations; }

    @ExceptionHandler(WorkspaceRuleException.class)
    ResponseEntity<OperationErrors.Error> domain(WorkspaceRuleException failure, HttpServletRequest request) {
        UUID id = request.getAttribute("bridora.request_id") instanceof String value
                ? UUID.fromString(value) : UUID.randomUUID();
        var mapped = WorkspaceErrorMapping.map(failure, id);
        return operations.failure(new OperationFailure(mapped.status(), mapped.body().errorCode(),
                mapped.body().message(), mapped.body().retryAfterSeconds()), request);
    }

    @ExceptionHandler({IllegalArgumentException.class, ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
    ResponseEntity<OperationErrors.Error> invalid(Exception ignored, HttpServletRequest request) {
        return operations.failure(failure(400, "INVALID_REQUEST"), request);
    }

    @ExceptionHandler({org.springframework.dao.ConcurrencyFailureException.class, org.springframework.dao.DuplicateKeyException.class})
    ResponseEntity<OperationErrors.Error> conflict(Exception ignored, HttpServletRequest request) {
        return operations.failure(failure(409, "VERSION_CONFLICT"), request);
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<OperationErrors.Error> unexpected(RuntimeException ignored, HttpServletRequest request) {
        return operations.failure(failure(500, "INTERNAL_ERROR"), request);
    }
}
