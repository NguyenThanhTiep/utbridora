package vn.utbridora.auth.modules.cooperation.application;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.modules.cooperation.api.CooperationDtos.*;
import vn.utbridora.auth.modules.cooperation.domain.EnterpriseVerification;
import vn.utbridora.auth.modules.cooperation.domain.VerificationState;
import vn.utbridora.auth.modules.cooperation.persistence.VerificationRepository;
import vn.utbridora.auth.modules.cooperation.port.OrganizationSchoolVerificationPort;
import vn.utbridora.auth.modules.cooperation.port.OrganizationSchoolVerificationPort.SchoolVerificationView;
import vn.utbridora.auth.organization.Organization;
import vn.utbridora.auth.organization.OrganizationRepository;

/**
 * Business logic for School-Enterprise Verification.
 * Owner: T5.
 *
 * Guard rule (BC42): any downstream flow that requires school approval
 * must call {@link OrganizationSchoolVerificationPort#get} and check {@code schoolApproved}.
 */
@Service
public class VerificationService implements OrganizationSchoolVerificationPort {

    static final List<VerificationState> OPEN_STATES =
            List.of(VerificationState.DRAFT, VerificationState.SUBMITTED, VerificationState.NEEDS_CHANGES);

    private final VerificationRepository verifications;
    private final OrganizationRepository organizations;
    private final AuditService audit;

    public VerificationService(VerificationRepository verifications,
                                OrganizationRepository organizations,
                                AuditService audit) {
        this.verifications = verifications;
        this.organizations = organizations;
        this.audit = audit;
    }

    // ── Port implementation (BC42 guard) ───────────────────────────────────

    /**
     * Returns verification status without throwing.
     * schoolApproved=false when no record or state != APPROVED.
     */
    @Override
    @Transactional(readOnly = true)
    public SchoolVerificationView get(String enterpriseOrgId, String schoolOrgId) {
        return verifications
                .findByEnterpriseOrgIdAndSchoolOrgIdAndState(
                        enterpriseOrgId, schoolOrgId, VerificationState.APPROVED)
                .map(v -> new SchoolVerificationView(
                        v.enterpriseOrgId, v.schoolOrgId, v.id,
                        v.state.name(), v.version, true))
                .orElseGet(() -> new SchoolVerificationView(
                        enterpriseOrgId, schoolOrgId, null, null, null, false));
    }

    // ── Enterprise reads ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<VerificationView> listForEnterprise(String enterpriseOrgId,
                                                     int page, int size) {
        validatePagination(page, size);
        PageRequest paging = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        return verifications.findByEnterpriseOrgId(enterpriseOrgId, paging)
                .map(this::toFullView);
    }

    @Transactional(readOnly = true)
    public VerificationView getForEnterprise(String enterpriseOrgId, String verificationId) {
        EnterpriseVerification v = verifications.findById(verificationId)
                .orElseThrow(() -> new IllegalArgumentException("Verification not found"));
        if (!v.enterpriseOrgId.equals(enterpriseOrgId))
            throw new IllegalArgumentException("Verification not found");
        return toFullView(v);
    }

    // ── School reads ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<VerificationView> listForSchool(String schoolOrgId,
                                                 VerificationState stateFilter,
                                                 int page, int size) {
        validatePagination(page, size);
        PageRequest paging = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        Page<EnterpriseVerification> result = stateFilter != null
                ? verifications.findBySchoolOrgIdAndState(schoolOrgId, stateFilter, paging)
                : verifications.findBySchoolOrgId(schoolOrgId, paging);
        return result.map(this::toFullView);
    }

    @Transactional(readOnly = true)
    public VerificationView getForSchool(String schoolOrgId, String verificationId) {
        EnterpriseVerification v = verifications.findById(verificationId)
                .orElseThrow(() -> new IllegalArgumentException("Verification not found"));
        if (!v.schoolOrgId.equals(schoolOrgId))
            throw new IllegalArgumentException("Verification not found");
        return toFullView(v);
    }

    // ── Enterprise mutations ────────────────────────────────────────────────

    /**
     * Enterprise creates a DRAFT verification for a specific school.
     * Rejects if an open attempt already exists — enterprise must finish or the school must close it.
     */
    @Transactional
    public VerificationView createDraft(String actorId, String enterpriseOrgId,
                                         VerificationInput body) {
        validateOrg(enterpriseOrgId, Organization.Type.ENTERPRISE);
        String schoolOrgId = body.schoolOrgId();
        validateOrg(schoolOrgId, Organization.Type.UNIVERSITY);

        if (verifications.existsByEnterpriseOrgIdAndSchoolOrgIdAndStateIn(
                enterpriseOrgId, schoolOrgId, OPEN_STATES))
            throw new IllegalStateException("An open verification attempt already exists for this school");

        // previousVerificationId: must reference a REJECTED attempt by the same enterprise+school
        String previousId = body.previousVerificationId();
        if (previousId != null && !previousId.isBlank()) {
            EnterpriseVerification prev = verifications.findById(previousId)
                    .orElseThrow(() -> new IllegalArgumentException("Previous verification not found"));
            if (!prev.enterpriseOrgId.equals(enterpriseOrgId) || !prev.schoolOrgId.equals(schoolOrgId))
                throw new IllegalArgumentException("previousVerificationId belongs to a different enterprise or school");
            if (prev.state != VerificationState.REJECTED)
                throw new IllegalArgumentException("Previous verification must be REJECTED to link as previous");
        }

        EnterpriseVerification ev = new EnterpriseVerification();
        ev.enterpriseOrgId = enterpriseOrgId;
        ev.schoolOrgId = schoolOrgId;
        ev.previousVerificationId = (previousId != null && !previousId.isBlank()) ? previousId : null;
        applyContactData(ev, body);
        ev.state = VerificationState.DRAFT;
        ev.submissionNo = 0;

        EnterpriseVerification saved = verifications.save(ev);
        audit.writeScoped(actorId, "ENTERPRISE_VERIFICATION_DRAFT_SAVED",
                "enterprise_verification", saved.id,
                "SUCCESS", schoolOrgId, "DRAFT_CREATED");
        return toFullView(saved);
    }

    /**
     * Enterprise updates contact/registration data while in DRAFT or NEEDS_CHANGES state.
     */
    @Transactional
    public VerificationView updateDraft(String actorId, String enterpriseOrgId,
                                         String verificationId, UpdateVerificationData body) {
        EnterpriseVerification ev = verifications.findByIdForUpdate(verificationId)
                .orElseThrow(() -> new IllegalArgumentException("Verification not found"));
        if (!ev.enterpriseOrgId.equals(enterpriseOrgId))
            throw new IllegalArgumentException("Verification not found");
        if (ev.state != VerificationState.DRAFT && ev.state != VerificationState.NEEDS_CHANGES)
            throw new IllegalStateException("Verification cannot be edited in state: " + ev.state);
        if (ev.version != body.expectedVersion())
            throw new IllegalStateException("Verification has been modified; reload and retry");

        ev.registrationCode = body.registrationCode().trim();
        ev.registeredName   = body.registeredName().trim();
        ev.address          = body.address().trim();
        ev.contactName      = body.contactName().trim();
        ev.contactEmail     = body.contactEmail().trim().toLowerCase(java.util.Locale.ROOT);
        ev.contactPhone     = body.contactPhone().trim();

        EnterpriseVerification saved = verifications.save(ev);
        audit.writeScoped(actorId, "ENTERPRISE_VERIFICATION_DRAFT_SAVED",
                "enterprise_verification", saved.id,
                "SUCCESS", ev.schoolOrgId, "DRAFT_UPDATED");
        return toFullView(saved);
    }

    /**
     * Enterprise submits the verification to the school for review.
     * Requires at minimum: registrationCode, registeredName, address, contactName, contactEmail.
     */
    @Transactional
    public VerificationView submit(String actorId, String enterpriseOrgId,
                                    String verificationId, long expectedVersion) {
        EnterpriseVerification ev = verifications.findByIdForUpdate(verificationId)
                .orElseThrow(() -> new IllegalArgumentException("Verification not found"));
        if (!ev.enterpriseOrgId.equals(enterpriseOrgId))
            throw new IllegalArgumentException("Verification not found");
        if (ev.state != VerificationState.DRAFT && ev.state != VerificationState.NEEDS_CHANGES)
            throw new IllegalStateException("Only DRAFT or NEEDS_CHANGES verifications can be submitted");
        if (ev.version != expectedVersion)
            throw new IllegalStateException("Verification has been modified; reload and retry");
        assertRequiredContactFields(ev);

        ev.state = VerificationState.SUBMITTED;
        ev.submissionNo = ev.submissionNo + 1;
        ev.submittedAt = Instant.now();

        EnterpriseVerification saved = verifications.save(ev);
        audit.writeScoped(actorId, "ENTERPRISE_VERIFICATION_SUBMITTED",
                "enterprise_verification", saved.id,
                "SUCCESS", ev.schoolOrgId, "SUBMITTED");
        return toFullView(saved);
    }

    // ── School mutations ────────────────────────────────────────────────────

    /**
     * School staff decides on a SUBMITTED verification.
     * APPROVE → schoolApproved becomes true via port.
     * REJECT / REQUEST_INFO → stays open for enterprise to address.
     */
    @Transactional
    public VerificationView decide(String actorId, String schoolOrgId,
                                    String verificationId, VerificationDecision body) {
        EnterpriseVerification ev = verifications.findByIdForUpdate(verificationId)
                .orElseThrow(() -> new IllegalArgumentException("Verification not found"));
        if (!ev.schoolOrgId.equals(schoolOrgId))
            throw new IllegalArgumentException("Verification not found");
        if (ev.state != VerificationState.SUBMITTED)
            throw new IllegalStateException("Only SUBMITTED verifications can be decided; current state: " + ev.state);
        if (ev.version != body.expectedVersion())
            throw new IllegalStateException("Verification has been modified; reload and retry");

        switch (body.decision()) {
            case APPROVE -> {
                ev.state = VerificationState.APPROVED;
                ev.decisionRef = body.decisionRef();
                ev.decidedAt = Instant.now();
                ev.missingFields = null;
                EnterpriseVerification saved = verifications.save(ev);
                audit.writeScoped(actorId, "ENTERPRISE_VERIFICATION_APPROVED",
                        "enterprise_verification", saved.id,
                        "SUCCESS", schoolOrgId, "APPROVED");
                return toFullView(saved);
            }
            case REJECT -> {
                if (body.reasonCode() == null || body.reasonCode().isBlank())
                    throw new IllegalArgumentException("reasonCode is required for REJECT decision");
                ev.state = VerificationState.REJECTED;
                ev.decisionRef = body.decisionRef();
                ev.decidedAt = Instant.now();
                ev.missingFields = null;
                EnterpriseVerification saved = verifications.save(ev);
                audit.writeScoped(actorId, "ENTERPRISE_VERIFICATION_REJECTED",
                        "enterprise_verification", saved.id,
                        "SUCCESS", schoolOrgId, body.reasonCode());
                return toFullView(saved);
            }
            case REQUEST_INFO -> {
                if (body.missingFields() == null || body.missingFields().isBlank())
                    throw new IllegalArgumentException("missingFields is required when requesting more information");
                ev.state = VerificationState.NEEDS_CHANGES;
                ev.missingFields = body.missingFields().trim();
                EnterpriseVerification saved = verifications.save(ev);
                audit.writeScoped(actorId, "ENTERPRISE_VERIFICATION_CHANGES_REQUESTED",
                        "enterprise_verification", saved.id,
                        "SUCCESS", schoolOrgId, "CHANGES_REQUESTED");
                return toFullView(saved);
            }
        }
        throw new IllegalArgumentException("Unknown decision type");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void validateOrg(String orgId, Organization.Type expectedType) {
        Organization org = organizations.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        if (org.type != expectedType)
            throw new IllegalArgumentException("Expected " + expectedType + " organization");
    }

    private void applyContactData(EnterpriseVerification ev, VerificationInput body) {
        ev.registrationCode = body.registrationCode() != null ? body.registrationCode().trim() : null;
        ev.registeredName   = body.registeredName()   != null ? body.registeredName().trim()   : null;
        ev.address          = body.address()          != null ? body.address().trim()          : null;
        ev.contactName      = body.contactName()      != null ? body.contactName().trim()       : null;
        ev.contactEmail     = body.contactEmail()     != null
                ? body.contactEmail().trim().toLowerCase(java.util.Locale.ROOT) : null;
        ev.contactPhone     = body.contactPhone()     != null ? body.contactPhone().trim()      : null;
    }

    private void assertRequiredContactFields(EnterpriseVerification ev) {
        if (isBlank(ev.registrationCode)) throw new IllegalArgumentException("registrationCode is required before submission");
        if (isBlank(ev.registeredName))   throw new IllegalArgumentException("registeredName is required before submission");
        if (isBlank(ev.address))          throw new IllegalArgumentException("address is required before submission");
        if (isBlank(ev.contactName))      throw new IllegalArgumentException("contactName is required before submission");
        if (isBlank(ev.contactEmail))     throw new IllegalArgumentException("contactEmail is required before submission");
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private VerificationView toFullView(EnterpriseVerification v) {
        return new VerificationView(
                v.id, v.enterpriseOrgId, v.schoolOrgId, v.state,
                v.submissionNo, v.version,
                v.registrationCode, v.registeredName, v.address,
                v.contactName, v.contactEmail, v.contactPhone,
                v.missingFields, v.decisionRef,
                v.submittedAt, v.decidedAt, v.createdAt, v.updatedAt);
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 100)
            throw new IllegalArgumentException("Invalid pagination parameters");
    }
}
