package vn.utbridora.auth.modules.cooperation.api;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.cooperation.application.SchoolSharingPolicyService;
import vn.utbridora.auth.modules.cooperation.port.SchoolSharingPolicyPort.SchoolSharingPolicy;
import static vn.utbridora.auth.operations.WorkflowValues.body;
@RestController @RequestMapping("/api/v1/school/terms/{termId}/profile-sharing-policy")
public class SchoolSharingPolicyController {
    private final SchoolSharingPolicyService service;
    public SchoolSharingPolicyController(SchoolSharingPolicyService service) {this.service=service;}
    @GetMapping public SchoolSharingPolicy get(Authentication a,@PathVariable String termId) {return service.get(a,termId);}
    @PutMapping public SchoolSharingPolicy put(Authentication a,@PathVariable String termId,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return service.update(a,termId,key,body(data,SchoolSharingPolicyService.Update.class));}
}
