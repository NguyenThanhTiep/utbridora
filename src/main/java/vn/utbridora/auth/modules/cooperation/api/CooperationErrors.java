package vn.utbridora.auth.modules.cooperation.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.cooperation.domain.DemandFailure;
import vn.utbridora.auth.modules.matching.api.MatchingController;
import java.util.UUID;

/** T5 domain/provider errors keep C.0 when consumed by workflow controllers. */
@Order(-110)
@RestControllerAdvice(assignableTypes={CooperationController.class,ProposalController.class,PlacementController.class,MatchingController.class,ProfileShareController.class,SchoolSharingPolicyController.class})
public class CooperationErrors {
    @ExceptionHandler(DemandFailure.class)
    ResponseEntity<DemandErrorHandler.ErrorBody> domain(DemandFailure e,HttpServletRequest r) {
        String id=r.getAttribute("bridora.request_id") instanceof String value?value:UUID.randomUUID().toString();
        Integer retry=e.code().equals("REQUEST_IN_PROGRESS")?1:null;
        var response=ResponseEntity.status(e.status());if(retry!=null) response.header("Retry-After",retry.toString());
        return response.body(new DemandErrorHandler.ErrorBody(e.code(),e.code(),e.getMessage(),id,e.fields(),retry));
    }
    @ExceptionHandler({org.springframework.web.bind.MissingRequestHeaderException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<DemandErrorHandler.ErrorBody> schema(Exception e,HttpServletRequest r) {
        return domain(new DemandFailure(400,"SCHEMA_VALIDATION","Kiểm tra body, tham số và header bắt buộc."),r);
    }
    @ExceptionHandler(org.springframework.dao.ConcurrencyFailureException.class)
    ResponseEntity<DemandErrorHandler.ErrorBody> concurrent(Exception e,HttpServletRequest r) {
        return domain(new DemandFailure(409,"REQUEST_IN_PROGRESS","Thao tác đang được xử lý; thử lại cùng key."),r);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<DemandErrorHandler.ErrorBody> unexpected(Exception e,HttpServletRequest r) {
        return domain(new DemandFailure(500,"INTERNAL_ERROR","Không thể hoàn tất thao tác."),r);
    }
}
