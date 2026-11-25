package vn.utbridora.auth.modules.internship.api;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.authorization.EnterpriseMemberReferencePort.Member;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page;
import vn.utbridora.auth.modules.internship.infrastructure.AssignmentManagementReadService;
import vn.utbridora.auth.modules.internship.infrastructure.AssignmentManagementReadService.*;

@RestController @RequestMapping("/api/v1/t3/assignment-management")
public class AssignmentManagementReadController {
    private final AssignmentManagementReadService service;
    public AssignmentManagementReadController(AssignmentManagementReadService service) {this.service=service;}
    @ModelAttribute void privateResponse(jakarta.servlet.http.HttpServletResponse response) {response.setHeader("Cache-Control","private, no-store");}
    @GetMapping("/scopes") public Page<ManagementScope> scopes(Authentication a,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return service.scopes(a,page,size);}
    @GetMapping("/enterprises") public Page<EnterpriseOption> enterprises(Authentication a,@RequestParam String schoolOrgId,@RequestParam String termId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return service.enterprises(a,schoolOrgId,termId,page,size);}
    @GetMapping("/candidates") public Page<Member> candidates(Authentication a,@RequestParam String schoolOrgId,@RequestParam String enterpriseOrgId,@RequestParam String termId,@RequestParam(required=false) String search,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return service.candidates(a,schoolOrgId,enterpriseOrgId,termId,search,page,size);}
    @GetMapping("/assignments") public Page<ManagedAssignment> assignments(Authentication a,@RequestParam String schoolOrgId,@RequestParam String enterpriseOrgId,@RequestParam String termId,@RequestParam(defaultValue="false") boolean includeInactive,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return service.assignments(a,schoolOrgId,enterpriseOrgId,termId,includeInactive,page,size);}
    @GetMapping("/assignments/{id}") public ManagedAssignment detail(Authentication a,@RequestParam String schoolOrgId,@RequestParam String enterpriseOrgId,@RequestParam String termId,@PathVariable String id) {return service.detail(a,schoolOrgId,enterpriseOrgId,termId,id);}
    @GetMapping("/groups") public Page<GroupOption> groups(Authentication a,@RequestParam String schoolOrgId,@RequestParam String enterpriseOrgId,@RequestParam String termId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return service.groups(a,schoolOrgId,enterpriseOrgId,termId,page,size);}
    @GetMapping("/cases") public Page<CaseOption> cases(Authentication a,@RequestParam String schoolOrgId,@RequestParam String enterpriseOrgId,@RequestParam String termId,@RequestParam(required=false) String groupId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return service.cases(a,schoolOrgId,enterpriseOrgId,termId,groupId,page,size);}
    @GetMapping("/cases/{caseId}/mentors") public Page<Member> mentors(Authentication a,@RequestParam String schoolOrgId,@RequestParam String enterpriseOrgId,@RequestParam String termId,@PathVariable String caseId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return service.handoverCandidates(a,schoolOrgId,enterpriseOrgId,termId,caseId,page,size);}
}
