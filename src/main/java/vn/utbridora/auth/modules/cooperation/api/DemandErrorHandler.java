package vn.utbridora.auth.modules.cooperation.api;

import java.util.List;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import vn.utbridora.auth.modules.cooperation.domain.DemandFailure;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.FieldError;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=DemandController.class)
public class DemandErrorHandler {
    public record ErrorBody(String errorCode,String error,String message,String requestId,List<FieldError> fieldErrors,Integer retryAfterSeconds) {}
    @ExceptionHandler(DemandFailure.class)
    ResponseEntity<ErrorBody> failure(DemandFailure failure,HttpServletRequest request) {
        return response(failure.status(),failure.code(),failure.getMessage(),failure.fields(),request);
    }
    @ExceptionHandler({org.springframework.web.bind.MissingRequestHeaderException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorBody> schema(Exception failure,HttpServletRequest request) {
        return response(400,"SCHEMA_VALIDATION","Kiểm tra trường dữ liệu, tham số và header bắt buộc.",List.of(),request);
    }
    @ExceptionHandler(org.springframework.dao.ConcurrencyFailureException.class)
    ResponseEntity<ErrorBody> concurrent(Exception failure,HttpServletRequest request) {
        return response(409,"REQUEST_IN_PROGRESS","Thao tác đang được xử lý, vui lòng thử lại.",List.of(),request);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> unexpected(Exception failure,HttpServletRequest request) {
        return response(500,"INTERNAL_ERROR","Không thể hoàn tất thao tác.",List.of(),request);
    }
    private ResponseEntity<ErrorBody> response(int status,String code,String message,List<FieldError> fields,HttpServletRequest request) {
        Object requestId=request.getAttribute("bridora.request_id");
        Integer retry=code.equals("REQUEST_IN_PROGRESS")?1:null;
        var body=new ErrorBody(code,code,message,requestId instanceof String id?id:UUID.randomUUID().toString(),fields,retry);
        var response=ResponseEntity.status(status); if(retry!=null) response.header("Retry-After",retry.toString());
        return response.body(body);
    }
}
