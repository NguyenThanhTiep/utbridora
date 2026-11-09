package vn.utbridora.auth.modules.internship.api;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException;

@RestControllerAdvice(assignableTypes=WorkspaceController.class)
@Order(Ordered.HIGHEST_PRECEDENCE+5)
public class WorkspaceErrors {
    @ExceptionHandler(WorkspaceRuleException.class)
    ResponseEntity<WorkspaceErrorMapping.Envelope> domain(WorkspaceRuleException failure,HttpServletRequest request) {
        var mapped=WorkspaceErrorMapping.map(failure,requestId(request));return ResponseEntity.status(mapped.status()).body(mapped.body());
    }
    @ExceptionHandler({IllegalArgumentException.class,ServletRequestBindingException.class,MethodArgumentTypeMismatchException.class})
    ResponseEntity<WorkspaceErrorMapping.Envelope> invalid(Exception failure,HttpServletRequest request) {return error(400,"INVALID_REQUEST",request);}
    @ExceptionHandler({org.springframework.dao.ConcurrencyFailureException.class,org.springframework.dao.DuplicateKeyException.class})
    ResponseEntity<WorkspaceErrorMapping.Envelope> concurrent(Exception failure,HttpServletRequest request) {return error(409,"VERSION_CONFLICT",request);}
    private ResponseEntity<WorkspaceErrorMapping.Envelope> error(int status,String code,HttpServletRequest request) {
        return ResponseEntity.status(status).body(new WorkspaceErrorMapping.Envelope(code,code,"Yêu cầu chưa thể thực hiện.",requestId(request),java.util.List.of(),null));
    }
    private static UUID requestId(HttpServletRequest request) {return request.getAttribute("bridora.request_id") instanceof String id?UUID.fromString(id):UUID.randomUUID();}
}
