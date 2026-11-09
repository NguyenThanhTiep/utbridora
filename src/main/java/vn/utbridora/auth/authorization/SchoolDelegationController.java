package vn.utbridora.auth.authorization;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import static vn.utbridora.auth.operations.WorkflowValues.body;
import static vn.utbridora.auth.authorization.SchoolDelegationService.*;

@RestController @RequestMapping("/api/v1")
public class SchoolDelegationController {
    private final SchoolDelegationService service;
    public SchoolDelegationController(SchoolDelegationService service) {this.service=service;}
    @PostMapping("/admin/school-delegations") @ResponseStatus(HttpStatus.CREATED)
    public Delegation bootstrap(Authentication a,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return service.bootstrap(a,key,body(data,Bootstrap.class));}
    @PostMapping("/school/terms/{termId}/delegations") @ResponseStatus(HttpStatus.CREATED)
    public Delegation grant(Authentication a,@PathVariable String termId,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return service.grant(a,termId,key,body(data,Grant.class));}
    @DeleteMapping("/school/delegations/{id}")
    public RevocationAck revoke(Authentication a,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return service.revoke(a,id,key,body(data,Revoke.class));}
}
