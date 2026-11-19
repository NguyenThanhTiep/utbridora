package vn.utbridora.auth.modules.internship.api;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import vn.utbridora.auth.operations.OperationErrors.Error;
import vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException;

@RestControllerAdvice(assignableTypes={InternshipOperationsController.class,PlacementIntakeController.class,CaseClosureController.class})
public class OperationsHttpErrors {
    @ExceptionHandler(WorkspaceRuleException.class) public ResponseEntity<WorkspaceErrorMapping.Envelope> workspace(WorkspaceRuleException failure,HttpServletRequest request) {
        var mapped=WorkspaceErrorMapping.map(failure,java.util.UUID.fromString(id(request)));return ResponseEntity.status(mapped.status()).body(mapped.body());
    }
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.bind.ServletRequestBindingException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.web.multipart.MultipartException.class})
    public ResponseEntity<Error> schema(Exception error,HttpServletRequest request) {return ResponseEntity.badRequest().body(new Error("SCHEMA_VALIDATION","SCHEMA_VALIDATION","Vui lòng kiểm tra các trường yêu cầu.",id(request),java.util.List.of(),null));}
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class) public ResponseEntity<Error> tooLarge(Exception error,HttpServletRequest request) {return ResponseEntity.status(413).body(new Error("FILE_TOO_LARGE","FILE_TOO_LARGE","Tệp vượt quá giới hạn cho phép.",id(request),java.util.List.of(),null));}
    @ExceptionHandler({org.springframework.dao.ConcurrencyFailureException.class,org.springframework.dao.DuplicateKeyException.class}) public ResponseEntity<Error> conflict(Exception error,HttpServletRequest request) {return ResponseEntity.status(409).body(new Error("VERSION_CONFLICT","VERSION_CONFLICT","Dữ liệu đã thay đổi, vui lòng tải lại.",id(request),java.util.List.of(),null));}
    private static String id(HttpServletRequest request) {return request.getAttribute("bridora.request_id") instanceof String value?value:java.util.UUID.randomUUID().toString();}
}
