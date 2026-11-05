package vn.utbridora.auth.modules.cooperation.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.cooperation.api.CooperationDtos.*;
import vn.utbridora.auth.modules.cooperation.application.TermService;
import vn.utbridora.auth.modules.cooperation.domain.TermStatus;
import vn.utbridora.auth.security.AppPrincipal;

/**
 * REST API for TermReference (owned by T5).
 *
 * Routes (registered in SecurityConfig):
 *   GET  /api/v1/terms                          — authenticated, filter by schoolOrgId
 *   GET  /api/v1/terms/{termId}                 — authenticated
 *   POST /api/v1/school/{schoolOrgId}/terms     — SCHOOL_STAFF + placement:school:write
 *   PATCH /api/v1/school/{schoolOrgId}/terms/{termId}/status — same permission
 */
@RestController
public class TermController {

    private final TermService termService;

    public TermController(TermService termService) {
        this.termService = termService;
    }

    // ── Public reads (session required) ────────────────────────────────────

    /**
     * List terms for a school. Any authenticated user may call this.
     * schoolOrgId is a required query parameter to scope the response.
     */
    @GetMapping("/api/v1/terms")
    public Page<TermView> listTerms(
            @RequestParam String schoolOrgId,
            @RequestParam(required = false) TermStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "25") int size) {
        if (schoolOrgId == null || schoolOrgId.isBlank())
            throw new IllegalArgumentException("schoolOrgId is required");
        return termService.listBySchool(schoolOrgId, status, page, size);
    }

    @GetMapping("/api/v1/terms/{termId}")
    public TermView getTerm(@PathVariable String termId) {
        return termService.getById(termId);
    }

    // ── School-staff mutations ──────────────────────────────────────────────

    /**
     * School staff creates a term for their own school.
     * schoolOrgId in path must match an active SCHOOL_STAFF membership of the caller.
     */
    @PostMapping("/api/v1/school/{schoolOrgId}/terms")
    @PreAuthorize("@authorizationPolicy.hasOrganization(authentication, #schoolOrgId, "
            + "T(vn.utbridora.auth.user.Role).SCHOOL_STAFF, 'placement:school:write')")
    public ResponseEntity<TermView> createTerm(
            Authentication auth,
            @PathVariable String schoolOrgId,
            @Valid @RequestBody CreateTerm body) {
        String actorId = actor(auth);
        TermView created = termService.create(actorId, schoolOrgId, body);
        return ResponseEntity.status(201).body(created);
    }

    /**
     * School staff transitions a term status (DRAFT→ACTIVE, DRAFT→CLOSED, ACTIVE→CLOSED).
     */
    @PatchMapping("/api/v1/school/{schoolOrgId}/terms/{termId}/status")
    @PreAuthorize("@authorizationPolicy.hasOrganization(authentication, #schoolOrgId, "
            + "T(vn.utbridora.auth.user.Role).SCHOOL_STAFF, 'placement:school:write')")
    public TermView updateTermStatus(
            Authentication auth,
            @PathVariable String schoolOrgId,
            @PathVariable String termId,
            @Valid @RequestBody UpdateTermStatus body) {
        return termService.updateStatus(actor(auth), schoolOrgId, termId, body);
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    private static String actor(Authentication auth) {
        return ((AppPrincipal) auth.getPrincipal()).userId();
    }
}
