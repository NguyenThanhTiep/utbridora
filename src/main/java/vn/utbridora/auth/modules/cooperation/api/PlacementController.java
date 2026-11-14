package vn.utbridora.auth.modules.cooperation.api;

import static vn.utbridora.auth.operations.WorkflowValues.body;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.cooperation.application.PlacementService;
import vn.utbridora.auth.modules.cooperation.application.PlacementService.*;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page;

/**
 * REST endpoints for the Placement state machine (T5-07 / CP-T5UI1).
 *
 * Supports both X-Organization-Id header routes (CP-T5UI1 frontend) and
 * path-variable routes (/api/v1/school/{schoolOrgId}/placements).
 */
@RestController
public class PlacementController {

    private final PlacementService placements;

    public PlacementController(PlacementService placements) {
        this.placements = placements;
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    @PostMapping("/api/v1/school/placements")
    @ResponseStatus(HttpStatus.CREATED)
    public PlacementView createHeader(
            Authentication auth,
            @RequestHeader("X-Organization-Id") String schoolOrgId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody String data) {
        return placements.create(auth, schoolOrgId, idempotencyKey, body(data,CreatePlacement.class));
    }

    @PostMapping("/api/v1/school/{schoolOrgId}/placements")
    @ResponseStatus(HttpStatus.CREATED)
    public PlacementView create(
            Authentication auth,
            @PathVariable String schoolOrgId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody String data) {
        return placements.create(auth, schoolOrgId, idempotencyKey, body(data,CreatePlacement.class));
    }

    @PostMapping("/api/v1/placements/{placementId}/approve")
    public PlacementView approve(
            Authentication auth,
            @PathVariable String placementId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody String data) {
        return placements.approve(auth, placementId, idempotencyKey, body(data,PlacementCommand.class));
    }

    @PostMapping("/api/v1/placements/{placementId}/activate")
    public PlacementView activate(
            Authentication auth,
            @PathVariable String placementId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody String data) {
        return placements.activate(auth, placementId, idempotencyKey, body(data,PlacementCommand.class));
    }

    @PostMapping("/api/v1/placements/{placementId}/cancel")
    public PlacementView cancel(
            Authentication auth,
            @PathVariable String placementId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody String data) {
        return placements.cancel(auth, placementId, idempotencyKey, body(data,PlacementCommand.class));
    }

    // ── Reads (Header-based CP-T5UI1) ─────────────────────────────────────────

    @GetMapping("/api/v1/school/placements")
    public Page<PlacementView> listSchoolHeader(
            Authentication auth,
            @RequestHeader("X-Organization-Id") String schoolOrgId,
            @RequestParam(required = false) String termId,
            @RequestParam(required = false) String demandId,
            @RequestParam(required = false) String cooperationId,
            @RequestParam(required = false) String state,
            @RequestParam(name="schoolOrgId",required=false) String filterSchoolOrgId,
            @RequestParam(name="q",required=false) String query,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "25") int size) {
        return placements.listForSchool(auth, schoolOrgId, termId, demandId, cooperationId, state, filterSchoolOrgId, query, page, size);
    }

    @GetMapping("/api/v1/enterprise/placements")
    public Page<PlacementView> listEnterpriseHeader(
            Authentication auth,
            @RequestHeader("X-Organization-Id") String enterpriseOrgId,
            @RequestParam(required = false) String termId,
            @RequestParam(required = false) String demandId,
            @RequestParam(required = false) String cooperationId,
            @RequestParam(required = false) String state,
            @RequestParam(required=false) String schoolOrgId,
            @RequestParam(name="q",required=false) String query,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "25") int size) {
        return placements.listForEnterprise(auth, enterpriseOrgId, termId, demandId, cooperationId, state, schoolOrgId, query, page, size);
    }

    @GetMapping("/api/v1/school/placements/progress")
    public ProgressPage<ProgressCounters> progressSchool(Authentication auth,@RequestHeader("X-Organization-Id") String org,
            @RequestParam(required=false) String schoolOrgId,@RequestParam(required=false) String termId,
            @RequestParam(required=false) String demandId,@RequestParam(required=false) String cooperationId,
            @RequestParam(required=false) String status,@RequestParam(name="q",required=false) String query,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {
        return placements.progressForSchool(auth,org,schoolOrgId,termId,demandId,cooperationId,status,query,page,size);
    }

    @GetMapping("/api/v1/enterprise/placements/progress")
    public ProgressPage<ProgressCounters> progressEnterprise(Authentication auth,@RequestHeader("X-Organization-Id") String org,
            @RequestParam(required=false) String schoolOrgId,@RequestParam(required=false) String termId,
            @RequestParam(required=false) String demandId,@RequestParam(required=false) String cooperationId,
            @RequestParam(required=false) String status,@RequestParam(name="q",required=false) String query,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {
        return placements.progressForEnterprise(auth,org,schoolOrgId,termId,demandId,cooperationId,status,query,page,size);
    }

    @GetMapping("/api/v1/school/placements/summary")
    public PlacementSummary summarySchoolHeader(
            Authentication auth,
            @RequestHeader("X-Organization-Id") String schoolOrgId,
            @RequestParam String demandId) {
        return placements.summaryForSchool(auth, schoolOrgId, demandId);
    }

    @GetMapping("/api/v1/enterprise/placements/summary")
    public PlacementSummary summaryEnterpriseHeader(
            Authentication auth,
            @RequestHeader("X-Organization-Id") String enterpriseOrgId,
            @RequestParam String demandId) {
        return placements.summaryForEnterprise(auth, enterpriseOrgId, demandId);
    }

    @GetMapping("/api/v1/school/placements/{placementId}/internship-link")
    public InternshipLink internshipLinkSchoolHeader(
            Authentication auth,
            @RequestHeader("X-Organization-Id") String schoolOrgId,
            @PathVariable String placementId) {
        return placements.internshipLinkForSchool(auth, schoolOrgId, placementId);
    }

    @GetMapping("/api/v1/enterprise/placements/{placementId}/internship-link")
    public InternshipLink internshipLinkEnterpriseHeader(
            Authentication auth,
            @RequestHeader("X-Organization-Id") String enterpriseOrgId,
            @PathVariable String placementId) {
        return placements.internshipLinkForEnterprise(auth, enterpriseOrgId, placementId);
    }

    // ── Reads (Path-variable) ─────────────────────────────────────────────────

    @GetMapping("/api/v1/school/{schoolOrgId}/placements")
    public Page<PlacementView> listSchool(
            Authentication auth,
            @PathVariable String schoolOrgId,
            @RequestParam(required = false) String termId,
            @RequestParam(required = false) String demandId,
            @RequestParam(required = false) String cooperationId,
            @RequestParam(required = false) String state,
            @RequestParam(name="schoolOrgId",required=false) String filterSchoolOrgId,
            @RequestParam(name="q",required=false) String query,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "25") int size) {
        return placements.listForSchool(auth, schoolOrgId, termId, demandId, cooperationId, state, filterSchoolOrgId, query, page, size);
    }

    @GetMapping("/api/v1/enterprise/{enterpriseOrgId}/placements")
    public Page<PlacementView> listEnterprise(
            Authentication auth,
            @PathVariable String enterpriseOrgId,
            @RequestParam(required = false) String termId,
            @RequestParam(required = false) String demandId,
            @RequestParam(required = false) String cooperationId,
            @RequestParam(required = false) String state,
            @RequestParam(required=false) String schoolOrgId,
            @RequestParam(name="q",required=false) String query,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "25") int size) {
        return placements.listForEnterprise(auth, enterpriseOrgId, termId, demandId, cooperationId, state, schoolOrgId, query, page, size);
    }

    @GetMapping("/api/v1/school/{schoolOrgId}/placements/summary")
    public PlacementSummary summarySchool(
            Authentication auth,
            @PathVariable String schoolOrgId,
            @RequestParam String demandId) {
        return placements.summaryForSchool(auth, schoolOrgId, demandId);
    }

    @GetMapping("/api/v1/enterprise/{enterpriseOrgId}/placements/summary")
    public PlacementSummary summaryEnterprise(
            Authentication auth,
            @PathVariable String enterpriseOrgId,
            @RequestParam String demandId) {
        return placements.summaryForEnterprise(auth, enterpriseOrgId, demandId);
    }

    @GetMapping("/api/v1/school/{schoolOrgId}/placements/{placementId}/internship-link")
    public InternshipLink internshipLink(
            Authentication auth,
            @PathVariable String schoolOrgId,
            @PathVariable String placementId) {
        return placements.internshipLinkForSchool(auth, schoolOrgId, placementId);
    }

    @GetMapping("/api/v1/enterprise/{enterpriseOrgId}/placements/{placementId}/internship-link")
    public InternshipLink internshipLinkEnterprise(
            Authentication auth,
            @PathVariable String enterpriseOrgId,
            @PathVariable String placementId) {
        return placements.internshipLinkForEnterprise(auth, enterpriseOrgId, placementId);
    }
}
