package vn.utbridora.auth.onboarding;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import vn.utbridora.auth.files.RosterImportQuarantine;
import static vn.utbridora.auth.onboarding.StudentImportContracts.*;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@RestController
@RequestMapping("/api/v1/school/student-imports")
public class StudentImportController {
    private final StudentImportService imports;private final RosterImportQuarantine quarantine;
    private final StudentActivationResend resend;
    public StudentImportController(StudentImportService imports,RosterImportQuarantine quarantine,StudentActivationResend resend) {this.imports=imports;this.quarantine=quarantine;this.resend=resend;}
    @ModelAttribute void privateResponse(jakarta.servlet.http.HttpServletResponse response) {response.setHeader("Cache-Control","private, no-store");response.setHeader("X-Content-Type-Options","nosniff");}
    @PostMapping(value="/preview",consumes="multipart/form-data")
    public ResponseEntity<ImportPreviewDto> preview(Authentication auth,MultipartHttpServletRequest request,@RequestHeader("Idempotency-Key") String key) {
        if(!request.getMultiFileMap().keySet().equals(java.util.Set.of("file")) || request.getFiles("file").size()!=1
            || !request.getParameterMap().keySet().equals(java.util.Set.of("termId")) || request.getParameterMap().values().stream().anyMatch(values->values.length!=1)) throw failure(400,"SCHEMA_VALIDATION");
        String termId=request.getParameter("termId");MultipartFile file=request.getFile("file");
        imports.authorizePreview(auth,termId);uuid(key);
        try(var scanned=quarantine.prepare(file)) {return ResponseEntity.status(201).body(imports.preview(auth,termId,key,scanned));}
        catch(vn.utbridora.auth.operations.OperationFailure unavailable) {
            if(unavailable.status==503 && java.util.Set.of("SCANNER_UNAVAILABLE","FILE_STORAGE_UNAVAILABLE").contains(unavailable.code)) {
                try {
                    var digest=java.security.MessageDigest.getInstance("SHA-256");
                    try(var input=file.getInputStream()) {byte[] bytes=input.readNBytes(5*1024*1024+1);if(bytes.length<=5*1024*1024) imports.failedPreview(auth,termId,java.util.HexFormat.of().formatHex(digest.digest(bytes)),unavailable.code);}
                } catch(java.io.IOException | java.security.NoSuchAlgorithmException | RuntimeException checkpointUnavailable) { /* Keep the original safe 503; no selectable preview exists. */ }
            }
            throw unavailable;
        }
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<vn.utbridora.auth.operations.OperationErrors.Error> oversize(jakarta.servlet.http.HttpServletRequest request) {
        String id=request.getAttribute("bridora.request_id") instanceof String value?value:java.util.UUID.randomUUID().toString();
        return ResponseEntity.status(413).body(new vn.utbridora.auth.operations.OperationErrors.Error("FILE_TOO_LARGE","FILE_TOO_LARGE","Tệp vượt quá giới hạn cho phép.",id,java.util.List.of(),null));
    }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.bind.MissingRequestHeaderException.class,org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.web.multipart.support.MissingServletRequestPartException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.web.multipart.MultipartException.class})
    public ResponseEntity<vn.utbridora.auth.operations.OperationErrors.Error> schema(Exception error,jakarta.servlet.http.HttpServletRequest request) {
        String code=error instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException mismatch && java.util.Set.of("page","size").contains(mismatch.getName())?"INVALID_PAGINATION":"SCHEMA_VALIDATION";
        String id=request.getAttribute("bridora.request_id") instanceof String value?value:java.util.UUID.randomUUID().toString();
        return ResponseEntity.badRequest().body(new vn.utbridora.auth.operations.OperationErrors.Error(code,code,"Vui lòng kiểm tra các trường yêu cầu.",id,java.util.List.of(),null));
    }
    @GetMapping("/{id}") public ImportJobDto get(Authentication auth,@PathVariable String id) {return imports.get(auth,id);}
    @GetMapping("/{id}/rows") public Page<ImportRowDto> rows(Authentication auth,@PathVariable String id,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size,
        @RequestParam(required=false) String validationState,@RequestParam(required=false) String rowState,@RequestParam(required=false) String mailState,
        @RequestParam(required=false) String sort) {
        if(sort!=null && !sort.equals("rowNo,asc")) throw failure(400,"INVALID_REQUEST");
        return imports.listRows(auth,id,page,size,validationState,rowState,mailState);
    }
    @PostMapping("/{id}/confirm") public ResponseEntity<ImportAccepted> confirm(Authentication auth,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String body) {
        return ResponseEntity.status(202).body(imports.confirm(auth,id,key,body(body,ConfirmImport.class)));
    }
    @PostMapping("/{id}/resume") public ResponseEntity<?> resume(Authentication auth,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String body) {
        var result=imports.resume(auth,id,key,body(body,ResumeImport.class));return ResponseEntity.status(result.status()).body(result.body());
    }
    @PostMapping("/{id}/rows/{row}/resend") public ResponseEntity<ResendAccepted> resend(Authentication auth,@PathVariable String id,@PathVariable String row,@RequestHeader("Idempotency-Key") String key,@RequestBody String body) {
        return ResponseEntity.status(202).body(resend.resend(auth,id,row,key,body(body,ResendActivation.class)));
    }
}
