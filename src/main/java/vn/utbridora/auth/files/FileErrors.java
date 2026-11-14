package vn.utbridora.auth.files;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.*;
import vn.utbridora.auth.operations.OperationErrors;

@RestControllerAdvice(assignableTypes=FileController.class) @Order(Ordered.HIGHEST_PRECEDENCE+1)
public class FileErrors {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<OperationErrors.Error> size(Exception error,HttpServletRequest request) {return error(413,"FILE_TOO_LARGE",request);}
    @ExceptionHandler({MultipartException.class,IllegalArgumentException.class,org.springframework.web.bind.ServletRequestBindingException.class,org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    ResponseEntity<OperationErrors.Error> invalid(Exception error,HttpServletRequest request) {return error(400,"SCHEMA_VALIDATION",request);}
    @ExceptionHandler(org.springframework.dao.ConcurrencyFailureException.class)
    ResponseEntity<OperationErrors.Error> concurrent(Exception error,HttpServletRequest request) {return error(503,"TRANSIENT_FAILURE",request);}
    @ExceptionHandler(Exception.class)
    ResponseEntity<OperationErrors.Error> unavailable(Exception error,HttpServletRequest request) {return error(500,"INTERNAL_ERROR",request);}
    private static ResponseEntity<OperationErrors.Error> error(int status,String code,HttpServletRequest request) {
        String id=request.getAttribute("bridora.request_id") instanceof String value?value:UUID.randomUUID().toString();
        return ResponseEntity.status(status).header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff")
            .body(new OperationErrors.Error(code,code,"Không thể thực hiện thao tác với tệp.",id,List.of(),null));
    }
}
