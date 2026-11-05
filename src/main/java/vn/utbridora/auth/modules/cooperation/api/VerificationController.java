package vn.utbridora.auth.modules.cooperation.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.cooperation.api.CooperationDtos.*;
import vn.utbridora.auth.modules.cooperation.application.VerificationService;
import vn.utbridora.auth.modules.cooperation.domain.VerificationState;
import vn.utbridora.auth.modules.cooperation.port.OrganizationSchoolVerificationPort;
import vn.utbridora.auth.security.AppPrincipal;

/**
 * REST API for School-Enterprise Verification (owned by T5).
 *
 * Routes (registered in SecurityConfig):
 *   POST   /api/v1/enterprise/{enterpriseOrgId}/verifications
 *   PATCH  /api/v1/enterprise/{enterpriseOrgId}/verifications/{id}
 *   PATCH  /api/v1/enterprise/{enterpriseOrgId}/verifications/{id}/submit
 *   GET    /api/v1/enterprise/{enterpriseOrgId}/verifications
 *   GET    /api/v1/enterprise/{enterpriseOrgId}/verifications/{id}
 *
 *   GET    /api/v1/school/{schoolOrgId}/verifications
 *   GET    /api/v1/school/{schoolOrgId}/verifications/{id}
 *   PATCH  /api/v1/school/{schoolOrgId}/verifications/{id}/decision
 *
 *   GET    /api/v1/school-verification/{enterpriseOrgId}/{schoolOrgId} — guard read (any authenticated caller)
 */
@RestController
public class VerificationController {

    private final VerificationService verificationService;
    private final OrganizationSchoolVerificationPort verificationPort;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
        // VerificationService implements the port; inject both via the same bean
        this.verificationPort = verificationService;
    }

    // ── Enterprise routes ───────────────────────────────────────────────────

    /**
     * Enterprise creates a new DRAFT verification targeting a specific school.
     * Body must include schoolOrgId (target). This is immutable after creation.
     */
    @PostMapping("/api/v1/enterprise/{enterpriseOrgId}/verifications")
    @PreAuthorize("@authorizationPolicy.hasOrganization(authentication, #enterpriseOrgId, "
            + "T(vn.utbridora.auth.user.Role).ENTERPRISE, 'cooperation:enterprise:create')")
    public ResponseEntity<VerificationView> createDraft(
            Authentication auth,
            @PathVariable String enterpriseOrgId,
            @Valid @RequestBody VerificationInput body) {
        // schoolOrgId in body is the target school (different from enterpriseOrgId in path)
        VerificationView created = verificationService.createDraft(actor(auth), enterpriseOrgId, body);
        return ResponseEntity.status(201).body(created);
    }

    /**
     * Enterprise updates contact/registration data.
     * Only allowed in DRAFT or NEEDS_CHANGES state.
     */
    @PatchMapping("/api/v1/enterprise/{enterpriseOrgId}/verifications/{verificationId}")
    @PreAuthorize("@authorizationPolicy.hasOrganization(authentication, #enterpriseOrgId, "
            + "T(vn.utbridora.auth.user.Role).ENTERPRISE, 'cooperation:enterprise:create')")
    public VerificationView updateDraft(
            Authentication auth,
            @PathVariable String enterpriseOrgId,
            @PathVariable String verificationId,
            @Valid @RequestBody UpdateVerificationData body) {
        return verificationService.updateDraft(actor(auth), enterpriseOrgId, verificationId, body);
    }

    /**
     * Enterprise submits the verification to the school for review.
     * Body carries only expectedVersion.
     */
    @PatchMapping("/api/v1/enterprise/{enterpriseOrgId}/verifications/{verificationId}/submit")
    @PreAuthorize("@authorizationPolicy.hasOrganization(authentication, #enterpriseOrgId, "
            + "T(vn.utbridora.auth.user.Role).ENTERPRISE, 'cooperation:enterprise:create')")
    public VerificationView submit(
            Authentication auth,
            @PathVariable String enterpriseOrgId,
            @PathVariable String verificationId,
            @RequestBody SubmitVerification body) {
        return verificationService.submit(actor(auth), enterpriseOrgId, verificationId, body.expectedVersion());
    }

    @GetMapping("/api/v1/enterprise/{enterpriseOrgId}/verifications")
    @PreAuthorize("@authorizationPolicy.hasOrganization(authentication, #enterpriseOrgId, "
            + "T(vn.utbridora.auth.user.Role).ENTERPRISE, 'cooperation:enterprise:create')")
    public Page<VerificationView> listForEnterprise(
            @PathVariable String enterpriseOrgId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "25") int size) {
        return verificationService.listForEnterprise(enterpriseOrgId, page, size);
    }

    @GetMapping("/api/v1/enterprise/{enterpriseOrgId}/verifications/{verificationId}")
    @PreAuthorize("@authorizationPolicy.hasOrganization(authentication, #enterpriseOrgId, "
            + "T(vn.utbridora.auth.user.Role).ENTERPRISE, 'cooperation:enterprise:create')")
    public VerificationView getForEnterprise(
            @PathVariable String enterpriseOrgId,
            @PathVariable String verificationId) {
        return verificationService.getForEnterprise(enterpriseOrgId, verificationId);
    }

    // ── School routes ───────────────────────────────────────────────────────

    @GetMapping("/api/v1/school/{schoolOrgId}/verifications")
    @PreAuthorize("@authorizationPolicy.hasOrganization(authentication, #schoolOrgId, "
            + "T(vn.utbridora.auth.user.Role).SCHOOL_STAFF, 'cooperation:school:review')")
    public Page<VerificationView> listForSchool(
            @PathVariable String schoolOrgId,
            @RequestParam(required = false) VerificationState state,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "25") int size) {
        return verificationService.listForSchool(schoolOrgId, state, page, size);
    }

    @GetMapping("/api/v1/school/{schoolOrgId}/verifications/{verificationId}")
    @PreAuthorize("@authorizationPolicy.hasOrganization(authentication, #schoolOrgId, "
            + "T(vn.utbridora.auth.user.Role).SCHOOL_STAFF, 'cooperation:school:review')")
    public VerificationView getForSchool(
            @PathVariable String schoolOrgId,
            @PathVariable String verificationId) {
        return verificationService.getForSchool(schoolOrgId, verificationId);
    }

    /**
     * School staff decides: APPROVE, REJECT, or REQUEST_INFO.
     * Only callable on SUBMITTED verifications.
     */
    @PatchMapping("/api/v1/school/{schoolOrgId}/verifications/{verificationId}/decision")
    @PreAuthorize("@authorizationPolicy.hasOrganization(authentication, #schoolOrgId, "
            + "T(vn.utbridora.auth.user.Role).SCHOOL_STAFF, 'cooperation:school:review')")
    public VerificationView decide(
            Authentication auth,
            @PathVariable String schoolOrgId,
            @PathVariable String verificationId,
            @Valid @RequestBody VerificationDecision body) {
        return verificationService.decide(actor(auth), schoolOrgId, verificationId, body);
    }

    // ── Guard read — any authenticated caller (T3, T5, etc.) ───────────────

    /**
     * Returns whether the enterprise has been approved by the given school.
     * schoolApproved=false when no record or state != APPROVED.
     * Used by T3 (confirm coordinator) and T5 (submit Cooperation).
     */
    @GetMapping("/api/v1/school-verification/{enterpriseOrgId}/{schoolOrgId}")
    public OrganizationSchoolVerificationPort.SchoolVerificationView guardCheck(
            @PathVariable String enterpriseOrgId,
            @PathVariable String schoolOrgId) {
        return verificationPort.get(enterpriseOrgId, schoolOrgId);
    }

    // ── Inner DTO for submit (only version needed) ──────────────────────────

    public record SubmitVerification(long expectedVersion) {}

    // ── Helper ──────────────────────────────────────────────────────────────

    private static String actor(Authentication auth) {
        return ((AppPrincipal) auth.getPrincipal()).userId();
    }
}
