package vn.utbridora.auth.modules.cooperation.api;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.cooperation.application.ProposalService;
import vn.utbridora.auth.modules.cooperation.application.ProposalProfileService;
import vn.utbridora.auth.modules.cooperation.application.ProposalProfileService.SharedProfile;
import vn.utbridora.auth.modules.cooperation.application.ProposalService.*;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page;
import vn.utbridora.auth.modules.cooperation.port.InternshipInvitationPort.InternshipInvitation;
import static vn.utbridora.auth.operations.WorkflowValues.body;
@RestController @RequestMapping("/api/v1")
public class ProposalController {
    private final ProposalService service;
    private final ProposalProfileService profiles;
    public ProposalController(ProposalService service,ProposalProfileService profiles) {this.service=service;this.profiles=profiles;}
    @GetMapping("/enterprise/cooperations/{id}/proposals")
    public Page<EnterpriseItem> enterpriseList(Authentication a,@RequestHeader("X-Organization-Id") String org,@PathVariable String id,@RequestParam(required=false) String state,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return service.enterpriseList(a,org,id,state,page,size);}
    @PatchMapping("/enterprise/proposals/{id}/decision")
    public EnterpriseItem enterpriseDecision(Authentication a,@RequestHeader("X-Organization-Id") String org,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {
        try {
            var node=vn.utbridora.auth.operations.WorkflowValues.JSON.readTree(data);
            if(node instanceof tools.jackson.databind.node.ObjectNode object && !object.has("reasonCode")) object.putNull("reasonCode");
            return service.enterpriseDecide(a,org,id,key,body(node.toString(),EnterpriseDecision.class));
        } catch(tools.jackson.core.JacksonException invalid) {throw vn.utbridora.auth.operations.WorkflowValues.failure(400,"SCHEMA_VALIDATION");}
    }
    @GetMapping("/enterprise/proposals/{id}/profile")
    public SharedProfile enterpriseProfile(Authentication a,@RequestHeader("X-Organization-Id") String org,@PathVariable String id) {
        return profiles.read(a,org,id);
    }
    @PostMapping("/matching/runs/{id}/nominate") @ResponseStatus(HttpStatus.CREATED)
    public NominationResult nominate(Authentication a,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return service.nominate(a,id,key,body(data,Nominate.class));}
    @GetMapping("/school/cooperations/{id}/capacity") public Capacity capacity(Authentication a,@PathVariable String id) {return service.capacity(a,id);}
    @GetMapping("/school/cooperations/{id}/nomination-candidates") public NominationContext candidates(Authentication a,@PathVariable String id,@RequestParam String matchRunId,@RequestParam(defaultValue="MATCH") String overallStatus,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return service.nominationCandidates(a,id,matchRunId,overallStatus,page,size);}
    @GetMapping("/school/cooperations/{id}/proposals") public Page<SchoolItem> list(Authentication a,@PathVariable String id,@RequestParam(required=false) String state,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return service.list(a,id,state,page,size);}
    @PostMapping("/school/proposals/{id}/invite") @ResponseStatus(HttpStatus.ACCEPTED)
    public InternshipInvitation invite(Authentication a,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return service.invite(a,id,key,body(data,Invite.class));}
    @GetMapping("/invitations/me") public Page<InternshipInvitation> mine(Authentication a,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return service.mine(a,page,size);}
    @GetMapping("/invitations/{id}") public InternshipInvitation detail(Authentication a,@PathVariable String id) {return service.readMine(a,id);}
    @PostMapping("/invitations/{id}/decision") public InternshipInvitation decide(Authentication a,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return service.decide(a,id,key,body(data,InvitationDecision.class));}
    @PostMapping("/invitations/{id}/withdraw") public InternshipInvitation withdraw(Authentication a,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return service.withdraw(a,id,key,body(data,Withdrawal.class));}
}
