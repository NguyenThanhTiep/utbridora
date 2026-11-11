package vn.utbridora.auth.modules.cooperation.api;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.cooperation.application.ProfileShareService;
import vn.utbridora.auth.modules.cooperation.application.ProfileShareService.*;
import static vn.utbridora.auth.operations.WorkflowValues.body;
@RestController @RequestMapping("/api/v1")
public class ProfileShareController {
    private final ProfileShareService service;
    public ProfileShareController(ProfileShareService service) {this.service=service;}
    @PostMapping("/school/proposals/{id}/shares") @ResponseStatus(HttpStatus.CREATED)
    public Share grant(Authentication a,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return service.grant(a,id,key,body(data,GrantProfileShare.class));}
    @PostMapping("/school/profile-shares/{id}/revoke")
    public Share revoke(Authentication a,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return service.revoke(a,id,key,body(data,Revoke.class));}
    @GetMapping("/students/{student}/profile")
    public ProfileProjection read(Authentication a,@PathVariable String student,@RequestParam String termId,@RequestParam(required=false) String shareId,@RequestParam List<String> fields,@RequestParam String purpose) {return service.read(a,student,termId,shareId,fields,purpose);}
}
