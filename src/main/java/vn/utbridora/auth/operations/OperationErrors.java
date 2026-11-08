package vn.utbridora.auth.operations;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Handles only new operation-ledger failures; legacy Core error handling is unchanged. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OperationErrors {
    private final vn.utbridora.auth.audit.AuditService audit;
    public OperationErrors(vn.utbridora.auth.audit.AuditService audit) {this.audit=audit;}
    public record Error(String errorCode, String error, String message, String requestId,
                        List<Object> fieldErrors, Integer retryAfterSeconds) {}
    @ExceptionHandler(OperationFailure.class)
    public ResponseEntity<Error> failure(OperationFailure failure, HttpServletRequest request) {
        if(failure.status==403 || failure.status==404) {
            var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            String actor=auth!=null && auth.getPrincipal() instanceof vn.utbridora.auth.security.AppPrincipal p?p.userId():null;
            try {audit.securityDenied(actor,failure.code);}
            catch(RuntimeException unavailable) {org.slf4j.LoggerFactory.getLogger(OperationErrors.class).warn("Denied request audit unavailable");}
        }
        String id=request.getAttribute("bridora.request_id") instanceof String value
                ? value : UUID.randomUUID().toString();
        var response=ResponseEntity.status(failure.status);
        if (failure.retryAfterSeconds!=null) response.header("Retry-After",failure.retryAfterSeconds.toString());
        return response.body(new Error(failure.code,failure.code,failure.getMessage(),id,List.of(),failure.retryAfterSeconds));
    }
}
