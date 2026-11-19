package vn.utbridora.auth.onboarding;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.utbridora.auth.files.RosterImportQuarantine;
import static vn.utbridora.auth.onboarding.StudentImportContracts.*;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@RestController
@RequestMapping("/api/v1/school/student-imports")
public class StudentImportController {
    private final StudentImportService imports;private final RosterImportQuarantine quarantine;
    private final StudentActivationResend resend;
    public StudentImportController(StudentImportService imports,RosterImportQuarantine quarantine,StudentActivationResend resend) {this.imports=imports;this.quarantine=quarantine;this.resend=resend;}
    @PostMapping(value="/preview",consumes="multipart/form-data")
    public ResponseEntity<ImportPreviewDto> preview(Authentication auth,@RequestParam String termId,@RequestParam MultipartFile file,@RequestHeader("Idempotency-Key") String key) {
        imports.authorizePreview(auth,termId);uuid(key);
        try(var scanned=quarantine.prepare(file)) {return ResponseEntity.status(201).body(imports.preview(auth,termId,key,scanned));}
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
