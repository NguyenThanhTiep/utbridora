package vn.utbridora.auth.modules.internship.api;

import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.internship.infrastructure.InternshipOperationsService;
import vn.utbridora.auth.modules.internship.infrastructure.WorkspaceRuntime.Page;
import static vn.utbridora.auth.modules.internship.api.InternshipOperationsDtos.*;

@RestController
@RequestMapping("/api/v1/t3")
public class InternshipOperationsController {
    private final InternshipOperationsService service;
    public InternshipOperationsController(InternshipOperationsService service) {this.service=service;}
    @ModelAttribute void privateResponse(jakarta.servlet.http.HttpServletResponse response) {response.setHeader("Cache-Control","private, no-store");response.setHeader("X-Content-Type-Options","nosniff");}
    @GetMapping("/school/dashboard") public WorkspaceSummary school(Authentication auth,@RequestParam UUID termId,@RequestParam(required=false) UUID cohortId) {return service.summary(auth,termId,cohortId,"school");}
    @GetMapping("/hr/dashboard") public WorkspaceSummary hr(Authentication auth,@RequestParam UUID termId,@RequestParam(required=false) UUID cohortId) {return service.summary(auth,termId,cohortId,"hr");}
    @GetMapping("/mentor/dashboard") public WorkspaceSummary mentor(Authentication auth,@RequestParam UUID termId,@RequestParam(required=false) UUID cohortId) {return service.summary(auth,termId,cohortId,"mentor");}
    @GetMapping("/calendar") public Page<CalendarEvent> calendar(Authentication auth,@RequestParam Instant from,@RequestParam Instant until,@RequestParam(required=false) UUID caseId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return service.calendar(auth,from,until,caseId,page,size);}
    @GetMapping("/enterprise-quality/templates") public Page<EnterpriseQualityTemplate> templates(Authentication auth,@RequestParam UUID termId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return service.templates(auth,termId,page,size);}
    @PostMapping("/school/enterprise-feedback") @ResponseStatus(HttpStatus.CREATED) public EnterpriseQualityDto feedback(Authentication auth,@RequestHeader("Idempotency-Key") String key,@RequestBody String body) {return service.feedback(auth,key,InternshipOperationsJson.read(body,EnterpriseQualityInput.class));}
    @PostMapping("/school/issues") @ResponseStatus(HttpStatus.CREATED) public CoordinationIssue createIssue(Authentication auth,@RequestHeader("Idempotency-Key") String key,@RequestBody String body) {return service.createIssue(auth,key,InternshipOperationsJson.read(body,CreateCoordinationIssue.class));}
    @PatchMapping("/school/issues/{issueId}") public CoordinationIssue updateIssue(Authentication auth,@PathVariable UUID issueId,@RequestHeader("Idempotency-Key") String key,@RequestBody String body) {return service.updateIssue(auth,issueId,key,InternshipOperationsJson.read(body,UpdateCoordinationIssue.class));}
    @GetMapping("/school/issues") public Page<CoordinationIssue> issues(Authentication auth,@RequestParam UUID caseId,@RequestParam(required=false) IssueState state,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return service.issues(auth,caseId,state,page,size);}
    @GetMapping("/cases/{caseId}/hiring-interest") public HiringInterestDto interest(Authentication auth,@PathVariable UUID caseId) {return service.interest(auth,caseId);}
    @PutMapping("/cases/{caseId}/hiring-interest") public HiringInterestDto interest(Authentication auth,@PathVariable UUID caseId,@RequestHeader("Idempotency-Key") String key,@RequestBody String body) {return service.interest(auth,caseId,key,InternshipOperationsJson.read(body,HiringInterestInput.class));}
}
