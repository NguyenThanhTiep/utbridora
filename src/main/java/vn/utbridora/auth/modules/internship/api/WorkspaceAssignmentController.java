package vn.utbridora.auth.modules.internship.api;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.internship.infrastructure.WorkspaceAssignmentService;
import vn.utbridora.auth.modules.internship.infrastructure.WorkspaceAuthority.Assignment;
import vn.utbridora.auth.tokens.MentorInvitationService;
import static vn.utbridora.auth.operations.WorkflowValues.body;
@RestController @RequestMapping("/api/v1/t3")
public class WorkspaceAssignmentController {
    private final WorkspaceAssignmentService assignments;private final MentorInvitationService invitations;
    public WorkspaceAssignmentController(WorkspaceAssignmentService assignments,MentorInvitationService invitations) {this.assignments=assignments;this.invitations=invitations;}
    @PostMapping("/coordinators/confirm") @ResponseStatus(HttpStatus.CREATED)
    public Assignment confirm(Authentication a,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return assignments.confirm(a,key,body(data,WorkspaceAssignmentService.ConfirmCoordinator.class));}
    @PostMapping("/hr-assignments") @ResponseStatus(HttpStatus.CREATED)
    public Assignment hr(Authentication a,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return assignments.grantHr(a,key,body(data,WorkspaceAssignmentService.GrantHrAssignment.class));}
    @PostMapping("/groups/{groupId}/mentors") @ResponseStatus(HttpStatus.CREATED)
    public Assignment mentor(Authentication a,@PathVariable String groupId,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return assignments.grantMentor(a,groupId,key,body(data,WorkspaceAssignmentService.GrantMentorAssignment.class));}
    @PostMapping("/cases/{caseId}/mentor-handover") public WorkspaceAssignmentService.MutationAck handover(Authentication a,@PathVariable String caseId,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return assignments.handover(a,caseId,key,body(data,WorkspaceAssignmentService.Handover.class));}
    @DeleteMapping("/context-assignments/{id}") public WorkspaceAssignmentService.RevocationAck revoke(Authentication a,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return assignments.revoke(a,id,key,body(data,WorkspaceAssignmentService.Revoke.class));}
    @PostMapping("/mentor-invitations") @ResponseStatus(HttpStatus.ACCEPTED)
    public MentorInvitationService.MentorInviteDto invite(Authentication a,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return invitations.issue(a,key,body(data,MentorInvitationService.MentorInviteRequest.class));}
    @GetMapping("/mentor-invitations/me") public vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page<MentorInvitationService.MentorInviteDto> mine(Authentication a,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return invitations.mine(a,page,size);}
    @PostMapping("/mentor-invitations/{id}/accept") public MentorInvitationService.MentorInviteDto accept(Authentication a,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return invitations.accept(a,id,key,body(data,MentorInvitationService.Accept.class));}
    @PostMapping("/mentor-invitations/{id}/revoke") public MentorInvitationService.MentorInviteDto revokeInvite(Authentication a,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return invitations.revoke(a,id,key,body(data,MentorInvitationService.Revoke.class));}
}
