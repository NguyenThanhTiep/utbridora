package vn.utbridora.auth.modules.cooperation.api;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.cooperation.application.ProposalService;
import vn.utbridora.auth.modules.cooperation.application.ProposalService.*;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page;
import vn.utbridora.auth.modules.cooperation.port.InternshipInvitationPort.InternshipInvitation;
import static vn.utbridora.auth.operations.WorkflowValues.body;
@RestController @RequestMapping("/api/v1")
public class ProposalController {
    private final ProposalService service;
    public ProposalController(ProposalService service) {this.service=service;}
    @PostMapping("/matching/runs/{id}/nominate") @ResponseStatus(HttpStatus.CREATED)
    public NominationResult nominate(Authentication a,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return service.nominate(a,id,key,body(data,Nominate.class));}
    @GetMapping("/school/cooperations/{id}/capacity") public Capacity capacity(Authentication a,@PathVariable String id) {return service.capacity(a,id);}
    @GetMapping("/school/cooperations/{id}/proposals") public Page<Item> list(Authentication a,@PathVariable String id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return service.list(a,id,page,size);}
    @PostMapping("/school/proposals/{id}/invite") @ResponseStatus(HttpStatus.ACCEPTED)
    public InternshipInvitation invite(Authentication a,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return service.invite(a,id,key,body(data,Invite.class));}
    @GetMapping("/invitations/me") public Page<InternshipInvitation> mine(Authentication a,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return service.mine(a,page,size);}
    @GetMapping("/invitations/{id}") public InternshipInvitation detail(Authentication a,@PathVariable String id) {return service.readMine(a,id);}
    @PostMapping("/invitations/{id}/decision") public InternshipInvitation decide(Authentication a,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return service.decide(a,id,key,body(data,InvitationDecision.class));}
    @PostMapping("/invitations/{id}/withdraw") public InternshipInvitation withdraw(Authentication a,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return service.withdraw(a,id,key,body(data,Withdrawal.class));}
}
