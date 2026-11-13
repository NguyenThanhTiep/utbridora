package vn.utbridora.auth.modules.internship.api;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException;

/** Scoped transport failures only. Shared OperationErrors still handles OperationFailure and
 * security-denied audit; Core/DEV05 advice is not modified. Same default-OFF gate as controllers. */
@RestControllerAdvice(assignableTypes = {WeeklyReportController.class, TaskCommentController.class})
@ConditionalOnProperty(name = Week3RequestValues.HTTP_ENABLED, havingValue = "true")
public final class Week3HttpErrors {
    @ExceptionHandler(WorkspaceRuleException.class)
    public ResponseEntity<WorkspaceErrorMapping.Envelope> domain(WorkspaceRuleException failure, HttpServletRequest request) {
        var mapped = WorkspaceErrorMapping.map(failure, requestId(request));
        return ResponseEntity.status(mapped.status()).body(mapped.body());
    }
    @ExceptionHandler({IllegalArgumentException.class, ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<WorkspaceErrorMapping.Envelope> invalid(Exception failure, HttpServletRequest request) {
        var mapped = WorkspaceErrorMapping.invalidRequest(requestId(request));
        return ResponseEntity.status(mapped.status()).body(mapped.body());
    }
    private UUID requestId(HttpServletRequest request) {
        if (request.getAttribute("bridora.request_id") instanceof String id) {
            try { return UUID.fromString(id); } catch (IllegalArgumentException ignored) { /* server fallback */ }
        }
        return UUID.randomUUID();
    }
}
