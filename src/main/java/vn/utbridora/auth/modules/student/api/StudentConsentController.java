package vn.utbridora.auth.modules.student.api;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.student.application.StudentConsentService;
import vn.utbridora.auth.modules.student.application.StudentConsentService.*;
import vn.utbridora.auth.modules.student.port.StudentConsentPort.ConsentGrant;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page;
import static vn.utbridora.auth.operations.WorkflowValues.body;
@RestController @RequestMapping("/api/v1/students/me/consents")
public class StudentConsentController {
    private final StudentConsentService service;
    public StudentConsentController(StudentConsentService service) {this.service=service;}
    @GetMapping public Page<ConsentGrant> mine(Authentication a,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return service.mine(a,page,size);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ConsentGrant grant(Authentication a,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return service.grant(a,key,body(data,GrantConsent.class));}
    @PostMapping("/{id}/revoke") public ConsentGrant revoke(Authentication a,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return service.revoke(a,id,key,body(data,Revoke.class));}
}
