package vn.utbridora.auth.modules.cooperation.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.operations.OperationErrors.Error;

@RestControllerAdvice(assignableTypes=VerificationController.class)
@Order(-100)
public class VerificationErrors {
    @ExceptionHandler({org.springframework.web.bind.MissingRequestHeaderException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<Error> schema(Exception e,HttpServletRequest r) {return response(r,400,"SCHEMA_VALIDATION");}
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<Error> conflict(Exception e,HttpServletRequest r) {return response(r,409,"VERIFICATION_ALREADY_OPEN");}
    @ExceptionHandler(org.springframework.dao.ConcurrencyFailureException.class)
    ResponseEntity<Error> concurrent(Exception e,HttpServletRequest r) {return ResponseEntity.status(409).header("Retry-After","1").body(body(r,"REQUEST_IN_PROGRESS",1));}
    private ResponseEntity<Error> response(HttpServletRequest r,int status,String code) {return ResponseEntity.status(status).body(body(r,code,null));}
    private Error body(HttpServletRequest r,String code,Integer retry) {
        String id=r.getAttribute("bridora.request_id") instanceof String value?value:UUID.randomUUID().toString();
        return new Error(code,code,"Yêu cầu không hợp lệ với dữ liệu hoặc trạng thái hiện tại.",id,List.of(),retry);
    }
}
