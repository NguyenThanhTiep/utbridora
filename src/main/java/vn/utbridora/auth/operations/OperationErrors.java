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
    public record Error(String errorCode, String error, String message, String requestId,
                        List<Object> fieldErrors, Integer retryAfterSeconds) {}
    @ExceptionHandler(OperationFailure.class)
    public ResponseEntity<Error> failure(OperationFailure failure, HttpServletRequest request) {
        String id=request.getAttribute("bridora.request_id") instanceof String value
                ? value : UUID.randomUUID().toString();
        var response=ResponseEntity.status(failure.status);
        if (failure.retryAfterSeconds!=null) response.header("Retry-After",failure.retryAfterSeconds.toString());
        return response.body(new Error(failure.code,failure.code,failure.getMessage(),id,List.of(),failure.retryAfterSeconds));
    }
}
