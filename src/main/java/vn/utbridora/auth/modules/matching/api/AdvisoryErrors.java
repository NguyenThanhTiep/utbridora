package vn.utbridora.auth.modules.matching.api;

import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.operations.OperationErrors;

/** Dependency failures stay closed and never expose SQL/transaction exception text. */
@RestControllerAdvice(assignableTypes=AdvisoryController.class) @Order(Ordered.HIGHEST_PRECEDENCE)
public class AdvisoryErrors {
    @ExceptionHandler({DataAccessException.class,TransactionException.class})
    public ResponseEntity<OperationErrors.Error> unavailable(RuntimeException failure,HttpServletRequest request) {
        String id=request.getAttribute("bridora.request_id") instanceof String value?value:UUID.randomUUID().toString();
        return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).header("X-Request-ID",id)
            .body(new OperationErrors.Error("DEPENDENCY_UNAVAILABLE","DEPENDENCY_UNAVAILABLE","Nguồn dữ liệu tạm thời không sẵn sàng.",id,List.of(),null));
    }
}
