package vn.utbridora.auth.modules.student.api;

import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.FieldError;
import vn.utbridora.auth.operations.OperationFailure;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = StudentPreferenceController.class)
public class StudentPreferenceErrors {
    record ErrorEnvelope(String errorCode, String error, String message, String requestId,
                         List<FieldError> fieldErrors, Integer retryAfterSeconds) {}

    @ExceptionHandler(OperationFailure.class)
    ResponseEntity<ErrorEnvelope> operation(OperationFailure failure, HttpServletRequest request) {
        Object value = request.getAttribute("bridora.request_id");
        String requestId = value instanceof String id ? id : UUID.randomUUID().toString();
        var response = ResponseEntity.status(failure.status);
        if (failure.retryAfterSeconds != null) response.header("Retry-After", failure.retryAfterSeconds.toString());
        return response.body(new ErrorEnvelope(failure.code, failure.code, failure.getMessage(), requestId,
            List.of(), failure.retryAfterSeconds));
    }

    @ExceptionHandler({org.springframework.web.bind.MissingRequestHeaderException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorEnvelope> malformed(Exception failure, HttpServletRequest request) {
        Object value = request.getAttribute("bridora.request_id");
        String requestId = value instanceof String id ? id : UUID.randomUUID().toString();
        return ResponseEntity.badRequest().body(new ErrorEnvelope("SCHEMA_VALIDATION", "SCHEMA_VALIDATION",
            "Kiểm tra trường dữ liệu, tham số và header bắt buộc.", requestId, List.of(), null));
    }

    @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class,
            org.springframework.dao.ConcurrencyFailureException.class})
    ResponseEntity<ErrorEnvelope> concurrent(RuntimeException failure, HttpServletRequest request) {
        Object value = request.getAttribute("bridora.request_id");
        String requestId = value instanceof String id ? id : UUID.randomUUID().toString();
        return ResponseEntity.status(409).body(new ErrorEnvelope("VERSION_CONFLICT", "VERSION_CONFLICT",
            "Dữ liệu đã thay đổi, vui lòng tải lại.", requestId, List.of(), null));
    }
}
