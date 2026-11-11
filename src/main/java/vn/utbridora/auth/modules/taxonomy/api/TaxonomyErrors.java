package vn.utbridora.auth.modules.taxonomy.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.operations.OperationErrors.Error;

@RestControllerAdvice(assignableTypes=SkillTaxonomyController.class)
@Order(-100)
public class TaxonomyErrors {
    @ExceptionHandler({IllegalStateException.class,org.springframework.dao.DataIntegrityViolationException.class,
        org.springframework.dao.ConcurrencyFailureException.class})
    ResponseEntity<Error> conflict(Exception e,HttpServletRequest r) { return error(r,409,"TAXONOMY_CONFLICT"); }
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.web.bind.MethodArgumentNotValidException.class,
        org.springframework.web.bind.MissingRequestHeaderException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<Error> invalid(Exception e,HttpServletRequest r) { return error(r,400,"SCHEMA_VALIDATION"); }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ResponseEntity<Error> missing(org.springframework.web.server.ResponseStatusException e,HttpServletRequest r) {
        return error(r,e.getStatusCode().value(),"RESOURCE_NOT_FOUND");
    }
    private ResponseEntity<Error> error(HttpServletRequest r,int status,String code) {
        String id=r.getAttribute("bridora.request_id") instanceof String value?value:UUID.randomUUID().toString();
        return ResponseEntity.status(status).body(new Error(code,code,"Yêu cầu không hợp lệ với trạng thái danh mục hiện tại.",id,List.of(),null));
    }
}
