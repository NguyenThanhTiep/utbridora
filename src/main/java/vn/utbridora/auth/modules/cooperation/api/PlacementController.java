package vn.utbridora.auth.modules.cooperation.api;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.cooperation.application.PlacementService;
import vn.utbridora.auth.modules.cooperation.application.PlacementService.*;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page;
import vn.utbridora.auth.security.AppPrincipal;

/**
 * REST endpoints for the Placement state machine (T5-07).
 *
 * All routes require an active session, CSRF header and Idempotency-Key UUID header
 * for mutations. Business guards live in PlacementService.
 *
 * Routes registered in SecurityConfig:
 *   POST   /api/v1/school/placements
 *   POST   /api/v1/placements/{id}/approve
 *   POST   /api/v1/placements/{id}/activate
 *   POST   /api/v1/placements/{id}/cancel
 *   GET    /api/v1/school/{schoolOrgId}/placements
 *   GET    /api/v1/enterprise/{enterpriseOrgId}/placements
 *   GET    /api/v1/school/{schoolOrgId}/placements/summary?demandId=...
 *   GET    /api/v1/school/{schoolOrgId}/placements/{id}/internship-link
 */
@RestController
public class PlacementController {

    private final PlacementService placements;

    public PlacementController(PlacementService placements) {
        this.placements = placements;
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    @PostMapping("/api/v1/school/{schoolOrgId}/placements")
    public PlacementView create(
            Authentication auth,
            @PathVariable String schoolOrgId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePlacement body) {
        return placements.create(auth, schoolOrgId, idempotencyKey, body);
    }

    @PostMapping("/api/v1/placements/{placementId}/approve")
    public PlacementView approve(
            Authentication auth,
            @PathVariable String placementId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PlacementCommand body) {
        return placements.approve(auth, placementId, idempotencyKey, body);
    }

    @PostMapping("/api/v1/placements/{placementId}/activate")
    public PlacementView activate(
            Authentication auth,
            @PathVariable String placementId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PlacementCommand body) {
        return placements.activate(auth, placementId, idempotencyKey, body);
    }

    @PostMapping("/api/v1/placements/{placementId}/cancel")
    public PlacementView cancel(
            Authentication auth,
            @PathVariable String placementId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PlacementCommand body) {
        return placements.cancel(auth, placementId, idempotencyKey, body);
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @GetMapping("/api/v1/school/{schoolOrgId}/placements")
    public Page<PlacementView> listSchool(
            Authentication auth,
            @PathVariable String schoolOrgId,
            @RequestParam(required = false) String termId,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "25") int size) {
        return placements.listForSchool(auth, schoolOrgId, termId, state, page, size);
    }

    @GetMapping("/api/v1/enterprise/{enterpriseOrgId}/placements")
    public Page<PlacementView> listEnterprise(
            Authentication auth,
            @PathVariable String enterpriseOrgId,
            @RequestParam(required = false) String termId,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "25") int size) {
        return placements.listForEnterprise(auth, enterpriseOrgId, termId, state, page, size);
    }

    @GetMapping("/api/v1/school/{schoolOrgId}/placements/summary")
    public PlacementSummary summarySchool(
            Authentication auth,
            @PathVariable String schoolOrgId,
            @RequestParam String demandId) {
        return placements.summaryForSchool(auth, schoolOrgId, demandId);
    }

    @GetMapping("/api/v1/school/{schoolOrgId}/placements/{placementId}/internship-link")
    public InternshipLink internshipLink(
            Authentication auth,
            @PathVariable String schoolOrgId,
            @PathVariable String placementId) {
        return placements.internshipLinkForSchool(auth, schoolOrgId, placementId);
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    // schoolOrgId for approve/activate/cancel is derived from the placement record
    // inside PlacementService under a pessimistic lock; no sentinel needed here.
}
