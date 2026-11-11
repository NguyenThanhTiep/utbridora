package vn.utbridora.auth.modules.student.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.student.api.StudentProfileDtos.*;
import vn.utbridora.auth.modules.student.application.StudentProfileException;

/**
 * C.0 error envelope for the student profile API only (Contract Pack v1.0 lines 97-103): error equals errorCode,
 * requestId is the RequestIdFilter UUID. The core ApiErrorHandler keeps its legacy body for other routes.
 */
@RestControllerAdvice(assignableTypes={StudentProfileController.class,AcademicCatalogController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StudentProfileErrors {
    @ExceptionHandler(StudentProfileException.class)
    ResponseEntity<ErrorEnvelope> domain(StudentProfileException e,HttpServletRequest request) {
        return envelope(request,e.status,e.errorCode,e.getMessage(),e.fieldErrors);
    }
    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorEnvelope> missingParameter(MissingServletRequestParameterException e,HttpServletRequest request) {
        return envelope(request,400,"INVALID_REQUEST","Yêu cầu chưa hợp lệ.",
            List.of(new FieldErrorView(e.getParameterName(),"INVALID_REQUEST","Thiếu tham số bắt buộc.")));
    }
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorEnvelope> wrongParameter(Exception e,HttpServletRequest request) {
        return envelope(request,400,"INVALID_REQUEST","Tham số chưa hợp lệ.",List.of());
    }
    /** A concurrent first save of the same term loses on the unique key or the row lock. */
    @ExceptionHandler({DataIntegrityViolationException.class,ConcurrencyFailureException.class})
    ResponseEntity<ErrorEnvelope> concurrent(RuntimeException e,HttpServletRequest request) {
        return envelope(request,409,"PROFILE_STALE","Hồ sơ đã thay đổi, vui lòng tải lại.",List.of());
    }
    private static ResponseEntity<ErrorEnvelope> envelope(HttpServletRequest request,int status,String code,String message,
                                                          List<FieldErrorView> fieldErrors) {
        String requestId=request.getAttribute("bridora.request_id") instanceof String id ? id : UUID.randomUUID().toString();
        return ResponseEntity.status(status).body(new ErrorEnvelope(code,code,message,requestId,fieldErrors,null));
    }
}
