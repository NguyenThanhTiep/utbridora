package vn.utbridora.auth.modules.cooperation.api;

import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.utbridora.auth.modules.cooperation.domain.DemandFailure;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.FieldError;
import vn.utbridora.auth.operations.OperationFailure;

@Order(-100)
@RestControllerAdvice(assignableTypes = OpportunityController.class)
public class OpportunityErrors {
    record ErrorBody(String errorCode, String error, String message, String requestId,
                     List<FieldError> fieldErrors, Integer retryAfterSeconds) {}

    @ExceptionHandler(OperationFailure.class)
    ResponseEntity<ErrorBody> operation(OperationFailure failure, HttpServletRequest request) {
        return response(failure.status, failure.code, failure.getMessage(), List.of(),
            failure.retryAfterSeconds, request);
    }

    @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class,
            org.springframework.dao.ConcurrencyFailureException.class})
    ResponseEntity<ErrorBody> concurrent(RuntimeException failure, HttpServletRequest request) {
        return response(409, "VERSION_CONFLICT", "Dữ liệu đã thay đổi, vui lòng tải lại.",
            List.of(), null, request);
    }

    @ExceptionHandler(DemandFailure.class)
    ResponseEntity<ErrorBody> domain(DemandFailure failure, HttpServletRequest request) {
        return response(failure.status(), failure.code(), failure.getMessage(), failure.fields(), null, request);
    }

    @ExceptionHandler({org.springframework.web.bind.MissingRequestHeaderException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorBody> malformed(Exception failure, HttpServletRequest request) {
        return response(400, "SCHEMA_VALIDATION", "Kiểm tra trường dữ liệu, tham số và header bắt buộc.",
            List.of(), null, request);
    }

    private ResponseEntity<ErrorBody> response(int status, String code, String message,
            List<FieldError> fields, Integer retry, HttpServletRequest request) {
        Object value = request.getAttribute("bridora.request_id");
        String requestId = value instanceof String id ? id : UUID.randomUUID().toString();
        var response = ResponseEntity.status(status);
        if (retry != null) response.header("Retry-After", retry.toString());
        return response.body(new ErrorBody(code, code, message, requestId, fields, retry));
    }
}
