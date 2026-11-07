package vn.utbridora.auth.modules.cooperation.application;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.modules.cooperation.api.CooperationDtos.*;
import vn.utbridora.auth.modules.cooperation.domain.EnterpriseVerification;
import vn.utbridora.auth.modules.cooperation.domain.VerificationHistory;
import vn.utbridora.auth.modules.cooperation.domain.VerificationState;
import vn.utbridora.auth.modules.cooperation.persistence.VerificationHistoryRepository;
import vn.utbridora.auth.modules.cooperation.persistence.VerificationRepository;
import vn.utbridora.auth.modules.cooperation.port.OrganizationSchoolVerificationPort;
import vn.utbridora.auth.modules.cooperation.port.OrganizationSchoolVerificationPort.SchoolVerificationView;
import vn.utbridora.auth.organization.Organization;
import vn.utbridora.auth.organization.OrganizationRepository;

/**
 * Business logic for School-Enterprise Verification.
 * Owner: T5.
 *
 * State machine:
 *   DRAFT → PENDING_VERIFICATION (enterprise submits)
 *   NEEDS_INFO → PENDING_VERIFICATION (enterprise resubmits)
 *   PENDING_VERIFICATION → APPROVED  (school approves)
 *   PENDING_VERIFICATION → REJECTED  (school rejects)
 *   PENDING_VERIFICATION → NEEDS_INFO (school requests more info)
 *
 * Guard rule (BC42): downstream must call OrganizationSchoolVerificationPort.get()
 * and check schoolApproved before allowing cooperation.
 */
@Service
public class VerificationService implements OrganizationSchoolVerificationPort {

    static final List<VerificationState> OPEN_STATES =
            List.of(VerificationState.DRAFT,
                    VerificationState.PENDING_VERIFICATION,
                    VerificationState.NEEDS_INFO);

    private final VerificationRepository verifications;
    private final VerificationHistoryRepository history;
    private final OrganizationRepository organizations;
    private final AuditService audit;

    public VerificationService(VerificationRepository verifications,
                                VerificationHistoryRepository history,
                                OrganizationRepository organizations,
                                AuditService audit) {
        this.verifications = verifications;
        this.history = history;
        this.organizations = organizations;
        this.audit = audit;
    }

    // ── Port implementation (BC42 guard) ───────────────────────────────────

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
    public Page<VerificationView> listForEnterprise(String enterpriseOrgId, int page, int size) {
        validatePagination(page, size);
        PageRequest paging = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        return verifications.findByEnterpriseOrgId(enterpriseOrgId, paging).map(this::toFullView);
    }

    @Transactional(readOnly = true)
    public VerificationView getForEnterprise(String enterpriseOrgId, String verificationId) {
        EnterpriseVerification v = findAndCheckEnterprise(enterpriseOrgId, verificationId);
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
        // School staff chỉ thấy hồ sơ thuộc school_org của mình — đã filter qua query
        return result.map(this::toFullView);
    }

    @Transactional(readOnly = true)
    public VerificationView getForSchool(String schoolOrgId, String verificationId) {
        EnterpriseVerification v = findAndCheckSchool(schoolOrgId, verificationId);
        return toFullView(v);
    }

    // ── Enterprise mutations ────────────────────────────────────────────────

    /**
     * Enterprise creates a DRAFT verification targeting a specific school.
     * Server validates membership, org type. Does NOT auto-verify.
     */
    @Transactional
    public VerificationView createDraft(String actorId, String enterpriseOrgId,
                                         VerificationInput body) {
        // Validate orgs — server xác nhận, không tin body
        validateOrgType(enterpriseOrgId, Organization.Type.ENTERPRISE);
        validateOrgType(body.schoolOrgId(), Organization.Type.UNIVERSITY);

        // Only one open attempt per enterprise+school
        if (verifications.existsByEnterpriseOrgIdAndSchoolOrgIdAndStateIn(
                enterpriseOrgId, body.schoolOrgId(), OPEN_STATES))
            throw new IllegalStateException(
                    "An open verification attempt already exists for this school");

        // previousVerificationId phải là REJECTED của cùng enterprise+school
        if (body.previousVerificationId() != null && !body.previousVerificationId().isBlank()) {
            EnterpriseVerification prev = verifications.findById(body.previousVerificationId())
                    .orElseThrow(() -> new IllegalArgumentException("Previous verification not found"));
            if (!prev.enterpriseOrgId.equals(enterpriseOrgId)
                    || !prev.schoolOrgId.equals(body.schoolOrgId()))
                throw new IllegalArgumentException(
                        "previousVerificationId belongs to a different enterprise or school");
            if (prev.state != VerificationState.REJECTED)
                throw new IllegalArgumentException(
                        "Previous verification must be REJECTED to link as previous");
        }

        EnterpriseVerification ev = new EnterpriseVerification();
        ev.enterpriseOrgId = enterpriseOrgId;
        ev.schoolOrgId = body.schoolOrgId();
        ev.previousVerificationId = blankToNull(body.previousVerificationId());
        ev.state = VerificationState.DRAFT;
        ev.submissionNo = 0;
        applyContactData(ev, body.registrationCode(), body.registeredName(),
                body.address(), body.contactName(), body.contactEmail(), body.contactPhone());

        EnterpriseVerification saved = verifications.save(ev);
        recordHistory(saved.id, null, VerificationState.DRAFT, actorId, null, null, null);
        audit.writeScoped(actorId, "ENTERPRISE_VERIFICATION_DRAFT_SAVED",
                "enterprise_verification", saved.id, "SUCCESS", body.schoolOrgId(), "DRAFT_CREATED");
        return toFullView(saved);
    }

    /**
     * Enterprise updates contact data while in DRAFT or NEEDS_INFO.
     * Cannot change enterpriseOrgId or schoolOrgId.
     */
    @Transactional
    public VerificationView updateDraft(String actorId, String enterpriseOrgId,
                                         String verificationId, UpdateVerificationData body) {
        EnterpriseVerification ev = verifications.findByIdForUpdate(verificationId)
                .orElseThrow(() -> new IllegalArgumentException("Verification not found"));
        if (!ev.enterpriseOrgId.equals(enterpriseOrgId))
            throw new IllegalArgumentException("Verification not found");
        if (ev.state != VerificationState.DRAFT && ev.state != VerificationState.NEEDS_INFO)
            throw new IllegalStateException(
                    "Verification cannot be edited in state: " + ev.state);
        if (ev.version != body.expectedVersion())
            throw new IllegalStateException("Verification has been modified; reload and retry");

        applyContactData(ev, body.registrationCode(), body.registeredName(),
                body.address(), body.contactName(), body.contactEmail(), body.contactPhone());

        EnterpriseVerification saved = verifications.save(ev);
        audit.writeScoped(actorId, "ENTERPRISE_VERIFICATION_DRAFT_SAVED",
                "enterprise_verification", saved.id, "SUCCESS", ev.schoolOrgId, "DRAFT_UPDATED");
        return toFullView(saved);
    }

    /**
     * Enterprise submits → PENDING_VERIFICATION.
     * Works from DRAFT or NEEDS_INFO (resubmit after school requested changes).
     */
    @Transactional
    public VerificationView submit(String actorId, String enterpriseOrgId,
                                    String verificationId, long expectedVersion) {
        EnterpriseVerification ev = verifications.findByIdForUpdate(verificationId)
                .orElseThrow(() -> new IllegalArgumentException("Verification not found"));
        if (!ev.enterpriseOrgId.equals(enterpriseOrgId))
            throw new IllegalArgumentException("Verification not found");
        if (ev.state != VerificationState.DRAFT && ev.state != VerificationState.NEEDS_INFO)
            throw new IllegalStateException(
                    "Only DRAFT or NEEDS_INFO verifications can be submitted");
        if (ev.version != expectedVersion)
            throw new IllegalStateException("Verification has been modified; reload and retry");
        assertRequiredContactFields(ev);

        VerificationState fromState = ev.state;
        ev.state = VerificationState.PENDING_VERIFICATION;
        ev.submissionNo = ev.submissionNo + 1;
        ev.submitterId = actorId;
        ev.submittedAt = Instant.now();

        EnterpriseVerification saved = verifications.save(ev);
        recordHistory(saved.id, fromState, VerificationState.PENDING_VERIFICATION,
                actorId, null, null, null);
        audit.writeScoped(actorId, "ENTERPRISE_VERIFICATION_SUBMITTED",
                "enterprise_verification", saved.id, "SUCCESS", ev.schoolOrgId, "SUBMITTED");
        return toFullView(saved);
    }

    // ── School mutations ────────────────────────────────────────────────────

    /**
     * School staff decides on a PENDING_VERIFICATION.
     * APPROVE → schoolApproved=true via port.
     * REJECT → terminal, enterprise must create new attempt.
     * REQUEST_INFO → NEEDS_INFO, enterprise sửa và submit lại, không tự coi là REJECTED.
     */
    @Transactional
    public VerificationView decide(String actorId, String schoolOrgId,
                                    String verificationId, VerificationDecision body) {
        EnterpriseVerification ev = verifications.findByIdForUpdate(verificationId)
                .orElseThrow(() -> new IllegalArgumentException("Verification not found"));
        // School staff chỉ thấy hồ sơ thuộc school_org của mình
        if (!ev.schoolOrgId.equals(schoolOrgId))
            throw new IllegalArgumentException("Verification not found");
        // Chỉ xử lý PENDING_VERIFICATION
        if (ev.state != VerificationState.PENDING_VERIFICATION)
            throw new IllegalStateException(
                    "Only PENDING_VERIFICATION can be decided; current state: " + ev.state);
        if (ev.version != body.expectedVersion())
            throw new IllegalStateException("Verification has been modified; reload and retry");

        return switch (body.decision()) {
            case APPROVE -> {
                ev.state = VerificationState.APPROVED;
                ev.decisionRef = body.decisionRef();
                ev.reviewerId = actorId;
                ev.reviewedAt = Instant.now();
                ev.missingFields = null;
                EnterpriseVerification saved = verifications.save(ev);
                recordHistory(saved.id, VerificationState.PENDING_VERIFICATION,
                        VerificationState.APPROVED, actorId, null, null, body.decisionRef());
                audit.writeScoped(actorId, "ENTERPRISE_VERIFICATION_APPROVED",
                        "enterprise_verification", saved.id, "SUCCESS", schoolOrgId, "APPROVED");
                yield toFullView(saved);
            }
            case REJECT -> {
                if (body.reasonCode() == null || body.reasonCode().isBlank())
                    throw new IllegalArgumentException("reasonCode is required for REJECT");
                ev.state = VerificationState.REJECTED;
                ev.decisionRef = body.decisionRef();
                ev.reviewerId = actorId;
                ev.reviewedAt = Instant.now();
                ev.missingFields = null;
                EnterpriseVerification saved = verifications.save(ev);
                recordHistory(saved.id, VerificationState.PENDING_VERIFICATION,
                        VerificationState.REJECTED, actorId, body.reasonCode(), null, body.decisionRef());
                audit.writeScoped(actorId, "ENTERPRISE_VERIFICATION_REJECTED",
                        "enterprise_verification", saved.id, "SUCCESS", schoolOrgId, body.reasonCode());
                yield toFullView(saved);
            }
            case REQUEST_INFO -> {
                // NEEDS_INFO — danh sách nội dung thiếu/lý do không được rỗng
                // Không tự coi là REJECTED
                if (body.missingFields() == null || body.missingFields().isBlank())
                    throw new IllegalArgumentException(
                            "missingFields is required when requesting more information");
                ev.state = VerificationState.NEEDS_INFO;
                ev.missingFields = body.missingFields().trim();
                EnterpriseVerification saved = verifications.save(ev);
                recordHistory(saved.id, VerificationState.PENDING_VERIFICATION,
                        VerificationState.NEEDS_INFO, actorId, body.reasonCode(),
                        body.missingFields(), body.decisionRef());
                audit.writeScoped(actorId, "ENTERPRISE_VERIFICATION_CHANGES_REQUESTED",
                        "enterprise_verification", saved.id, "SUCCESS", schoolOrgId, "CHANGES_REQUESTED");
                yield toFullView(saved);
            }
        };
    }

    // ── History read ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<VerificationHistory> getHistory(String verificationId) {
        return history.findByVerificationIdOrderByChangedAtAsc(verificationId);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void recordHistory(String verificationId,
                                VerificationState from, VerificationState to,
                                String actorId, String reasonCode,
                                String missingFields, String decisionRef) {
        VerificationHistory h = new VerificationHistory();
        h.verificationId = verificationId;
        h.fromState = from != null ? from.name() : null;
        h.toState = to.name();
        h.actorId = actorId;
        h.reasonCode = reasonCode;
        h.missingFields = missingFields;
        h.decisionRef = decisionRef;
        h.changedAt = Instant.now();
        history.save(h);
    }

    private void validateOrgType(String orgId, Organization.Type expectedType) {
        Organization org = organizations.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        if (org.type != expectedType)
            throw new IllegalArgumentException("Expected " + expectedType + " organization");
    }

    private void applyContactData(EnterpriseVerification ev,
                                   String registrationCode, String registeredName,
                                   String address, String contactName,
                                   String contactEmail, String contactPhone) {
        ev.registrationCode = trim(registrationCode);
        ev.registeredName   = trim(registeredName);
        ev.address          = trim(address);
        ev.contactName      = trim(contactName);
        ev.contactEmail     = contactEmail != null
                ? contactEmail.trim().toLowerCase(java.util.Locale.ROOT) : null;
        ev.contactPhone     = trim(contactPhone);
    }

    private void assertRequiredContactFields(EnterpriseVerification ev) {
        if (isBlank(ev.registrationCode)) throw new IllegalArgumentException("registrationCode is required");
        if (isBlank(ev.registeredName))   throw new IllegalArgumentException("registeredName is required");
        if (isBlank(ev.address))          throw new IllegalArgumentException("address is required");
        if (isBlank(ev.contactName))      throw new IllegalArgumentException("contactName is required");
        if (isBlank(ev.contactEmail))     throw new IllegalArgumentException("contactEmail is required");
    }

    private EnterpriseVerification findAndCheckEnterprise(String enterpriseOrgId, String id) {
        EnterpriseVerification v = verifications.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Verification not found"));
        if (!v.enterpriseOrgId.equals(enterpriseOrgId))
            throw new IllegalArgumentException("Verification not found");
        return v;
    }

    private EnterpriseVerification findAndCheckSchool(String schoolOrgId, String id) {
        EnterpriseVerification v = verifications.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Verification not found"));
        if (!v.schoolOrgId.equals(schoolOrgId))
            throw new IllegalArgumentException("Verification not found");
        return v;
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
    private String trim(String s) { return s != null ? s.trim() : null; }
    private String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }

    private VerificationView toFullView(EnterpriseVerification v) {
        return new VerificationView(
                v.id, v.enterpriseOrgId, v.schoolOrgId, v.state,
                v.submissionNo, v.version,
                v.registrationCode, v.registeredName, v.address,
                v.contactName, v.contactEmail, v.contactPhone,
                v.missingFields, v.decisionRef,
                v.submittedAt, v.reviewedAt, v.createdAt, v.updatedAt);
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 100)
            throw new IllegalArgumentException("Invalid pagination parameters");
    }
}
