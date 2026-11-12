package vn.utbridora.auth.authorization;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page;
import static vn.utbridora.auth.authorization.SchoolDelegationManagement.*;

@RestController
@RequestMapping("/api/v1/{side:admin|school}/school-delegations")
public class SchoolDelegationReadController {
    private final SchoolDelegationManagement service;
    public SchoolDelegationReadController(SchoolDelegationManagement service) {this.service=service;}
    @GetMapping("/scopes")
    public Page<Scope> scopes(Authentication auth,@PathVariable String side,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {
        return service.scopes(auth,side.equals("admin"),page,size);
    }
    @GetMapping("/options")
    public Options options(Authentication auth,@PathVariable String side,@RequestParam String termId) {return service.options(auth,side.equals("admin"),termId);}
    @GetMapping("/candidates")
    public Page<Candidate> candidates(Authentication auth,@PathVariable String side,@RequestParam String termId,@RequestParam(required=false) String search,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return service.candidates(auth,side.equals("admin"),termId,search,page,size);}
    @GetMapping
    public Page<Detail> list(Authentication auth,@PathVariable String side,@RequestParam String termId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {
        return service.list(auth,side.equals("admin"),termId,page,size);
    }
    @GetMapping("/{id}")
    public Detail detail(Authentication auth,@PathVariable String side,@PathVariable String id) {return service.detail(auth,side.equals("admin"),id);}
}
