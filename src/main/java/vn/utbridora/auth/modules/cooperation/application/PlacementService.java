package vn.utbridora.auth.modules.cooperation.application;

import java.time.*;
import java.sql.Date;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.authorization.SchoolDelegationAccess;
import vn.utbridora.auth.contracts.placement.PlacementReadPort;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page;
import vn.utbridora.auth.modules.student.port.StudentEligibilityPort;
import vn.utbridora.auth.modules.student.port.StudentConsentPort;
import vn.utbridora.auth.operations.OperationLedger;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/**
 * T5 Placement state machine: PENDING_APPROVAL → APPROVED → ACTIVE → (COMPLETED via T3 proof)
 *                                                             ↘ CANCELLED (any open state)
 *
 * Active-key uniqueness (student,term) is enforced by DB unique index (V28)
 * and double-checked here before commit.
 *
 * PlacementLifecyclePublisher is called inside the SAME transaction for every
 * state transition that must notify T3.
 */
@Service
public class PlacementService {

    // ── Wire types ───────────────────────────────────────────────────────────

    public record PlacementView(
            String placementId, String studentUserId, String schoolOrgId,
            String enterpriseOrgId, String termId, String demandId,
            String cooperationId, String proposalItemId,
            String state, long version,
            Instant effectiveAt, LocalDate startDate, LocalDate endDate,
            Instant approvedAt, Instant cancelledAt, Instant completedAt,
            String decisionRef) {}

    public record CreatePlacement(
            String proposalItemId, Long expectedProposalVersion,
            Long expectedEligibilityVersion,
            String effectiveAt,  // ISO-8601 UTC
            String startDate, String endDate) {}  // yyyy-MM-dd

    public record PlacementCommand(
            Long expectedVersion, String decisionRef, String reasonCode) {}

    public record PlacementSummary(
            String demandId, String cooperationId,
            int target, int placed, int pending, int remaining) {}

    public record InternshipLink(
            String placementId, String caseId, String state, String route) {}

    // ── Dependencies ─────────────────────────────────────────────────────────

    private final JdbcTemplate jdbc;
    private final CooperationService cooperations;
    private final ProposalService proposals;
    private final ProposalConsentSummary consentSummary;
    private final StudentEligibilityPort eligibility;
    private final StudentConsentPort consent;
    private final SchoolDelegationAccess scope;
    private final AuthorizationPolicy core;
    private final PlacementLifecyclePublisher publisher;
    private final PlacementReadPort source;
    private final OperationLedger ledger;
    private final AuditService audit;

    public PlacementService(JdbcTemplate jdbc,
                             CooperationService cooperations,
                             ProposalService proposals,
                             ProposalConsentSummary consentSummary,
                             StudentEligibilityPort eligibility,
                             StudentConsentPort consent,
                             SchoolDelegationAccess scope,
                             AuthorizationPolicy core,
                             PlacementLifecyclePublisher publisher,
                             PlacementReadPort source,
                             OperationLedger ledger,
                             AuditService audit) {
        this.jdbc = jdbc;
        this.cooperations = cooperations;
        this.proposals = proposals;
        this.consentSummary = consentSummary;
        this.eligibility = eligibility;
        this.consent = consent;
        this.scope = scope;
        this.core = core;
        this.publisher = publisher;
        this.source = source;
        this.ledger = ledger;
        this.audit = audit;
    }

    // ── Create: PENDING_APPROVAL ──────────────────────────────────────────────

    /**
     * School staff creates a placement from an ENTERPRISE_ACCEPTED proposal item.
     * Does NOT occupy the active-key; approved_at is null until approve().
     */
    @Transactional
    public PlacementView create(Authentication auth, String schoolOrgId,
                                 String idempotencyKey, CreatePlacement body) {
        scope.require(auth, uuid(schoolOrgId), null, "placement:school:write");
        uuid(body.proposalItemId());
        version(body.expectedProposalVersion());
        version(body.expectedEligibilityVersion());

        Instant effectiveAt = parseInstant(body.effectiveAt());
        LocalDate startDate = LocalDate.parse(body.startDate());
        LocalDate endDate   = LocalDate.parse(body.endDate());
        if (!startDate.isBefore(endDate) && !startDate.isEqual(endDate))
            throw failure(422, "INVALID_DATE_RANGE");

        return ledger.execute(
            new OperationLedger.Command(actor(auth), schoolOrgId,
                "PLACEMENT_CREATE", idempotencyKey, body, 201),
            PlacementView.class,
            () -> doCreate(auth, schoolOrgId, body, effectiveAt, startDate, endDate),
            PlacementView::placementId);
    }

    private PlacementView doCreate(Authentication auth, String schoolOrgId,
                                    CreatePlacement body, Instant effectiveAt,
                                    LocalDate startDate, LocalDate endDate) {
        // Load and lock proposal item
        var item = proposals.item(body.proposalItemId());
        if (!item.state().equals("ENTERPRISE_ACCEPTED"))
            throw failure(409, "STATE_CONFLICT");
        if (item.version() != body.expectedProposalVersion())
            throw failure(409, "VERSION_CONFLICT");

        // Load cooperation to get scope
        var coop = cooperations.read(item.cooperationId());
        if (!coop.schoolOrgId().equals(schoolOrgId))
            throw failure(404, "RESOURCE_NOT_FOUND");
        if (!coop.state().equals("APPROVED"))
            throw failure(409, "STATE_CONFLICT");

        // Delegation check for the specific term
        scope.require(auth, schoolOrgId, coop.termId(), "placement:school:write");

        // No duplicate placement per proposal item
        int existing = jdbc.queryForObject(
            "SELECT COUNT(*) FROM placements WHERE proposal_item_id=?",
            Integer.class, body.proposalItemId());
        if (existing > 0) throw failure(409, "PLACEMENT_ALREADY_EXISTS");

        // Invitation must be ACCEPTED (T5 owns invitation)
        reCheckInvitationAccepted(item);

        // School verification still APPROVED
        int verified = jdbc.queryForObject(
            "SELECT COUNT(*) FROM enterprise_verifications " +
            "WHERE enterprise_org_id=? AND school_org_id=? AND state='APPROVED'",
            Integer.class, coop.enterpriseOrgId(), schoolOrgId);
        if (verified == 0) throw failure(422, "SCHOOL_VERIFICATION_REQUIRED");

        // Eligibility check (T2 port, locks record until commit)
        eligibility.assertEligibleForPlacement(
            auth, item.studentUserId(), coop.schoolOrgId(), coop.termId(),
            body.expectedEligibilityVersion());

        // Consent/share still valid (T2 port + T5 consent/share summary)
        consent.assertValidForPlacement(auth, item);
        if (!consentSummary.read(item, coop).shareIsActive())
            throw failure(409, "CONSENT_INVALID");

        // Insert PENDING_APPROVAL (no approved_at yet — does not activate active-key)
        String id = UUID.randomUUID().toString();
        jdbc.update(
            "INSERT INTO placements(id,student_user_id,school_org_id,enterprise_org_id," +
            "term_id,demand_id,cooperation_id,proposal_item_id,state,version," +
            "effective_at,start_date,end_date) " +
            "VALUES(?,?,?,?,?,?,?,?,'PENDING_APPROVAL',1,?,?,?)",
            id, item.studentUserId(), coop.schoolOrgId(), coop.enterpriseOrgId(),
            coop.termId(), coop.demandId(), coop.cooperationId(), body.proposalItemId(),
            utc(effectiveAt), Date.valueOf(startDate), Date.valueOf(endDate));

        // Link proposal item → placement
        jdbc.update("UPDATE proposal_items SET placement_id=?,state='PLACED',version=version+1 WHERE id=?",
            id, body.proposalItemId());

        // History: initial transition
        writeHistory(id, null, "PENDING_APPROVAL", actor(auth), null, null,
            UUID.randomUUID().toString());

        audit.workflow(actor(auth), "PLACEMENT_CREATED", id, schoolOrgId,
            Map.of("termId", coop.termId(), "placementVersion", 1));
        return readPlacement(id);
    }

    // ── Approve: PENDING_APPROVAL → APPROVED ─────────────────────────────────

    /**
     * Re-checks all gates. Sets approved_at which activates the active-key unique constraint.
     * Publishes PlacementApproved → T3 creates WAITING_START case.
     */
    @Transactional
    public PlacementView approve(Authentication auth, String placementId,
                                  String idempotencyKey, PlacementCommand body) {
        version(body.expectedVersion());
        // Load placement first to get schoolOrgId for delegation check
        var peek = jdbc.query("SELECT school_org_id FROM placements WHERE id=?",
            (r, n) -> r.getString("school_org_id"), uuid(placementId))
            .stream().findFirst().orElseThrow(() -> failure(404, "RESOURCE_NOT_FOUND"));
        scope.require(auth, peek, null, "placement:school:write");
        return ledger.execute(
            new OperationLedger.Command(actor(auth), peek,
                "PLACEMENT_APPROVE", idempotencyKey, Map.of("id", placementId, "body", body), 200),
            PlacementView.class,
            () -> doApprove(auth, peek, placementId, body),
            PlacementView::placementId);
    }

    private PlacementView doApprove(Authentication auth, String schoolOrgId,
                                     String placementId, PlacementCommand body) {
        var p = lockPlacement(placementId, schoolOrgId);
        if (!p.state().equals("PENDING_APPROVAL")) throw failure(409, "STATE_CONFLICT");
        if (p.version() != body.expectedVersion())  throw failure(409, "VERSION_CONFLICT");

        // Re-check delegation for specific term
        scope.require(auth, schoolOrgId, p.termId(), "placement:school:write");

        // Re-check org and school verification
        reCheckVerification(p.enterpriseOrgId(), schoolOrgId);
        reCheckQuota(p.cooperationId(), p.demandId());

        // Re-check cooperation, invitation still ACCEPTED, and consent/share still active
        var coop = cooperations.read(p.cooperationId());
        if (!coop.state().equals("APPROVED")) throw failure(409, "STATE_CONFLICT");
        var item = proposals.item(p.proposalItemId());
        reCheckInvitationAccepted(item);
        if (!consentSummary.read(item, coop).shareIsActive())
            throw failure(409, "CONSENT_INVALID");

        Instant now = Instant.now();
        try {
            jdbc.update(
                "UPDATE placements SET state='APPROVED',version=version+1," +
                "approved_at=?,decision_ref=? WHERE id=?",
                utc(now), body.decisionRef(), placementId);
        } catch (org.springframework.dao.DataIntegrityViolationException dup) {
            throw failure(409, "ACTIVE_PLACEMENT_EXISTS");
        }

        writeHistory(placementId, "PENDING_APPROVAL", "APPROVED", actor(auth),
            null, body.decisionRef(), UUID.randomUUID().toString());

        // Publish event MANDATORY in same TX → T3 inbox creates WAITING_START (also writes PLACEMENT_APPROVED audit)
        publisher.publish(actor(auth), placementId);

        return readPlacement(placementId);
    }

    // ── Activate: APPROVED → ACTIVE ───────────────────────────────────────────

    /**
     * Only allowed when serverNow >= effectiveAt.
     * Publishes PlacementActivated → T3 inbox transitions WAITING_START → IN_PROGRESS.
     */
    @Transactional
    public PlacementView activate(Authentication auth, String placementId,
                                   String idempotencyKey, PlacementCommand body) {
        version(body.expectedVersion());
        var peek = jdbc.query("SELECT school_org_id FROM placements WHERE id=?",
            (r, n) -> r.getString("school_org_id"), uuid(placementId))
            .stream().findFirst().orElseThrow(() -> failure(404, "RESOURCE_NOT_FOUND"));
        scope.require(auth, peek, null, "placement:school:write");
        return ledger.execute(
            new OperationLedger.Command(actor(auth), peek,
                "PLACEMENT_ACTIVATE", idempotencyKey, Map.of("id", placementId, "body", body), 200),
            PlacementView.class,
            () -> doActivate(auth, peek, placementId, body),
            PlacementView::placementId);
    }

    private PlacementView doActivate(Authentication auth, String schoolOrgId,
                                      String placementId, PlacementCommand body) {
        var p = lockPlacement(placementId, schoolOrgId);
        if (!p.state().equals("APPROVED")) throw failure(409, "STATE_CONFLICT");
        if (p.version() != body.expectedVersion()) throw failure(409, "VERSION_CONFLICT");
        scope.require(auth, schoolOrgId, p.termId(), "placement:school:write");

        if (Instant.now().isBefore(p.effectiveAt()))
            throw failure(409, "PLACEMENT_NOT_EFFECTIVE");

        reCheckVerification(p.enterpriseOrgId(), schoolOrgId);

        jdbc.update("UPDATE placements SET state='ACTIVE',version=version+1 WHERE id=?", placementId);

        writeHistory(placementId, "APPROVED", "ACTIVE", actor(auth),
            null, null, UUID.randomUUID().toString());

        // Publish PlacementActivated — T3 inbox: WAITING_START → IN_PROGRESS (also writes PLACEMENT_ACTIVATED audit)
        publisher.publish(actor(auth), placementId);

        return readPlacement(placementId);
    }

    // ── Cancel: any open state → CANCELLED ───────────────────────────────────

    /**
     * Cancels a placement in PENDING_APPROVAL, APPROVED or ACTIVE.
     * ACTIVE → CANCELLED publishes PlacementCancelled → T3 inbox: IN_PROGRESS → TERMINATED.
     */
    @Transactional
    public PlacementView cancel(Authentication auth, String placementId,
                                 String idempotencyKey, PlacementCommand body) {
        version(body.expectedVersion());
        reason(body.reasonCode());
        var peek = jdbc.query("SELECT school_org_id FROM placements WHERE id=?",
            (r, n) -> r.getString("school_org_id"), uuid(placementId))
            .stream().findFirst().orElseThrow(() -> failure(404, "RESOURCE_NOT_FOUND"));
        scope.require(auth, peek, null, "placement:school:write");
        return ledger.execute(
            new OperationLedger.Command(actor(auth), peek,
                "PLACEMENT_CANCEL", idempotencyKey, Map.of("id", placementId, "body", body), 200),
            PlacementView.class,
            () -> doCancel(auth, peek, placementId, body),
            PlacementView::placementId);
    }

    private PlacementView doCancel(Authentication auth, String schoolOrgId,
                                    String placementId, PlacementCommand body) {
        var p = lockPlacement(placementId, schoolOrgId);
        if (!Set.of("PENDING_APPROVAL", "APPROVED", "ACTIVE").contains(p.state()))
            throw failure(409, "STATE_CONFLICT");
        if (p.version() != body.expectedVersion()) throw failure(409, "VERSION_CONFLICT");
        scope.require(auth, schoolOrgId, p.termId(), "placement:school:write");

        boolean wasActive = p.state().equals("APPROVED") || p.state().equals("ACTIVE");
        Instant now = Instant.now();
        jdbc.update(
            "UPDATE placements SET state='CANCELLED',version=version+1,cancelled_at=? WHERE id=?",
            utc(now), placementId);

        writeHistory(placementId, p.state(), "CANCELLED", actor(auth),
            body.reasonCode(), body.decisionRef(), UUID.randomUUID().toString());

        // Publish PlacementCancelled only when it was visible to T3 (also writes PLACEMENT_CANCELLED audit)
        if (wasActive) {
            publisher.publish(actor(auth), placementId, body.reasonCode());
        } else {
            audit.workflow(actor(auth), "PLACEMENT_CANCELLED", placementId, schoolOrgId,
                Map.of("termId", p.termId(), "placementVersion", p.version() + 1,
                    "reasonCode", body.reasonCode(),
                    "decisionRef", body.decisionRef() != null ? body.decisionRef() : ""));
        }
        return readPlacement(placementId);
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<PlacementView> listForSchool(Authentication auth, String schoolOrgId,
                                              String termId, String state,
                                              int page, int size) {
        return listForSchool(auth, schoolOrgId, termId, null, state, page, size);
    }

    @Transactional(readOnly = true)
    public Page<PlacementView> listForSchool(Authentication auth, String schoolOrgId,
                                              String termId, String cooperationId, String state,
                                              int page, int size) {
        page(page, size);
        scope.require(auth, uuid(schoolOrgId), termId, "placement:school:write");
        return queryPlacements("school_org_id", schoolOrgId, termId, cooperationId, state, page, size);
    }

    @Transactional(readOnly = true)
    public Page<PlacementView> listForEnterprise(Authentication auth, String enterpriseOrgId,
                                                  String termId, String state,
                                                  int page, int size) {
        return listForEnterprise(auth, enterpriseOrgId, termId, null, state, page, size);
    }

    @Transactional(readOnly = true)
    public Page<PlacementView> listForEnterprise(Authentication auth, String enterpriseOrgId,
                                                  String termId, String cooperationId, String state,
                                                  int page, int size) {
        page(page, size);
        requireEnterpriseRead(auth, enterpriseOrgId);
        return queryPlacements("enterprise_org_id", enterpriseOrgId, termId, cooperationId, state, page, size);
    }

    @Transactional(readOnly = true)
    public PlacementSummary summaryForSchool(Authentication auth, String schoolOrgId,
                                              String demandId) {
        uuid(demandId);
        scope.require(auth, uuid(schoolOrgId), null, "placement:school:write");
        int exists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM demands WHERE id=? AND school_org_id=? AND soft_deleted_at IS NULL",
            Integer.class, demandId, schoolOrgId);
        if (exists == 0) throw failure(404, "RESOURCE_NOT_FOUND");
        return buildSummary(demandId);
    }

    @Transactional(readOnly = true)
    public PlacementSummary summaryForEnterprise(Authentication auth, String enterpriseOrgId,
                                                  String demandId) {
        uuid(demandId);
        requireEnterpriseRead(auth, enterpriseOrgId);
        int exists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM demands WHERE id=? AND enterprise_org_id=? AND soft_deleted_at IS NULL",
            Integer.class, demandId, enterpriseOrgId);
        if (exists == 0) throw failure(404, "RESOURCE_NOT_FOUND");
        return buildSummary(demandId);
    }

    @Transactional(readOnly = true)
    public InternshipLink internshipLinkForSchool(Authentication auth, String schoolOrgId,
                                                   String placementId) {
        var p = readPlacement(placementId);
        if (!p.schoolOrgId().equals(uuid(schoolOrgId))) throw failure(404, "RESOURCE_NOT_FOUND");
        scope.require(auth, schoolOrgId, p.termId(), "placement:school:write");
        return buildLink(p);
    }

    @Transactional(readOnly = true)
    public InternshipLink internshipLinkForEnterprise(Authentication auth, String enterpriseOrgId,
                                                       String placementId) {
        requireEnterpriseRead(auth, enterpriseOrgId);
        var p = readPlacement(placementId);
        if (!p.enterpriseOrgId().equals(enterpriseOrgId)) throw failure(404, "RESOURCE_NOT_FOUND");
        return buildLink(p);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void requireEnterpriseRead(Authentication auth, String enterpriseOrgId) {
        uuid(enterpriseOrgId);
        boolean allowed = core.holdOrganization(auth, enterpriseOrgId, Role.ENTERPRISE, "t5:proposal:read")
            || core.holdOrganization(auth, enterpriseOrgId, Role.ENTERPRISE, "cooperation:enterprise:read");
        if (!allowed) throw failure(403, "ACCESS_DENIED");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PlacementView readPlacement(String id) {
        return jdbc.query(
            "SELECT * FROM placements WHERE id=?",
            (r, n) -> new PlacementView(
                r.getString("id"), r.getString("student_user_id"),
                r.getString("school_org_id"), r.getString("enterprise_org_id"),
                r.getString("term_id"), r.getString("demand_id"),
                r.getString("cooperation_id"), r.getString("proposal_item_id"),
                r.getString("state"), r.getLong("version"),
                instant(r, "effective_at"),
                r.getDate("start_date").toLocalDate(),
                r.getDate("end_date").toLocalDate(),
                r.getTimestamp("approved_at")  == null ? null : instant(r, "approved_at"),
                r.getTimestamp("cancelled_at") == null ? null : instant(r, "cancelled_at"),
                r.getTimestamp("completed_at") == null ? null : instant(r, "completed_at"),
                r.getString("decision_ref")),
            uuid(id))
            .stream().findFirst()
            .orElseThrow(() -> failure(404, "RESOURCE_NOT_FOUND"));
    }

    private PlacementView lockPlacement(String placementId, String schoolOrgId) {
        var p = jdbc.query(
            "SELECT * FROM placements WHERE id=? FOR UPDATE",
            (r, n) -> new PlacementView(
                r.getString("id"), r.getString("student_user_id"),
                r.getString("school_org_id"), r.getString("enterprise_org_id"),
                r.getString("term_id"), r.getString("demand_id"),
                r.getString("cooperation_id"), r.getString("proposal_item_id"),
                r.getString("state"), r.getLong("version"),
                instant(r, "effective_at"),
                r.getDate("start_date").toLocalDate(),
                r.getDate("end_date").toLocalDate(),
                r.getTimestamp("approved_at")  == null ? null : instant(r, "approved_at"),
                r.getTimestamp("cancelled_at") == null ? null : instant(r, "cancelled_at"),
                r.getTimestamp("completed_at") == null ? null : instant(r, "completed_at"),
                r.getString("decision_ref")),
            uuid(placementId))
            .stream().findFirst()
            .orElseThrow(() -> failure(404, "RESOURCE_NOT_FOUND"));
        if (!p.schoolOrgId().equals(schoolOrgId)) throw failure(404, "RESOURCE_NOT_FOUND");
        return p;
    }

    private void reCheckVerification(String enterpriseOrgId, String schoolOrgId) {
        int v = jdbc.queryForObject(
            "SELECT COUNT(*) FROM enterprise_verifications " +
            "WHERE enterprise_org_id=? AND school_org_id=? AND state='APPROVED'",
            Integer.class, enterpriseOrgId, schoolOrgId);
        if (v == 0) throw failure(422, "SCHOOL_VERIFICATION_REQUIRED");
    }

    private void reCheckQuota(String cooperationId, String demandId) {
        int quantity = jdbc.queryForObject(
            "SELECT JSON_UNQUOTE(JSON_EXTRACT(d.data_json,'$.quantity')) FROM demands d " +
            "WHERE d.id=?", Integer.class, demandId);
        int placed = jdbc.queryForObject(
            "SELECT COUNT(*) FROM placements p " +
            "JOIN proposal_batches b ON b.cooperation_id=? " +
            "JOIN proposal_items i ON i.batch_id=b.id AND i.placement_id=p.id " +
            "WHERE p.state IN ('APPROVED','ACTIVE','COMPLETED')",
            Integer.class, cooperationId);
        if (placed >= quantity) throw failure(409, "CAPACITY_EXCEEDED");
    }

    private Page<PlacementView> queryPlacements(String column, String orgId,
                                                  String termId, String cooperationId, String state,
                                                  int page, int size) {
        if (termId != null && !termId.isBlank()) uuid(termId);
        if (cooperationId != null && !cooperationId.isBlank()) uuid(cooperationId);
        if (state != null && !state.isBlank() &&
            !Set.of("PENDING_APPROVAL","APPROVED","ACTIVE","CANCELLED","COMPLETED").contains(state))
            throw failure(400, "SCHEMA_VALIDATION");
        String where = " WHERE " + column + "=? AND (?='' OR term_id=?) AND (?='' OR cooperation_id=?) AND (?='' OR state=?)";
        String tFilter = termId        != null ? termId        : "";
        String cFilter = cooperationId != null ? cooperationId : "";
        String sFilter = state         != null ? state         : "";
        Object[] args  = {orgId, tFilter, tFilter, cFilter, cFilter, sFilter, sFilter};
        var ids = jdbc.queryForList(
            "SELECT id FROM placements" + where + " ORDER BY state,id", String.class, args);
        int from = Math.min(page * size, ids.size());
        var rows = ids.subList(from, Math.min(from + size, ids.size()))
            .stream().map(this::readPlacement).toList();
        return new Page<>(rows, page, size, ids.size(), (ids.size() + size - 1) / size);
    }

    private PlacementSummary buildSummary(String demandId) {
        int target = jdbc.queryForObject(
            "SELECT JSON_UNQUOTE(JSON_EXTRACT(data_json,'$.quantity')) FROM demands WHERE id=?",
            Integer.class, demandId);
        String coopId = jdbc.queryForObject(
            "SELECT cooperation_id FROM demands WHERE id=?", String.class, demandId);
        int placed = coopId == null ? 0 : jdbc.queryForObject(
            "SELECT COUNT(DISTINCT p.id) FROM placements p " +
            "JOIN proposal_batches b ON b.cooperation_id=? " +
            "JOIN proposal_items i ON i.batch_id=b.id AND i.placement_id=p.id " +
            "WHERE p.state IN ('APPROVED','ACTIVE','COMPLETED')",
            Integer.class, coopId);
        int pending = coopId == null ? 0 : jdbc.queryForObject(
            "SELECT COUNT(DISTINCT i.student_user_id) " +
            "FROM proposal_items i JOIN proposal_batches b ON b.id=i.batch_id " +
            "WHERE b.cooperation_id=? " +
            "AND (i.state NOT IN ('PLACED','DECLINED','REVOKED','EXPIRED') " +
            "  OR EXISTS (SELECT 1 FROM placements p WHERE p.proposal_item_id=i.id AND p.state='PENDING_APPROVAL')) " +
            "AND NOT EXISTS (SELECT 1 FROM placements p WHERE p.proposal_item_id=i.id AND p.state IN ('APPROVED','ACTIVE','COMPLETED','CANCELLED'))",
            Integer.class, coopId);
        int remaining = Math.max(0, target - placed);
        return new PlacementSummary(demandId, coopId, target, placed, pending, remaining);
    }

    private InternshipLink buildLink(PlacementView p) {
        var rows = jdbc.queryForList(
            "SELECT id,state FROM internship_cases WHERE placement_id=?", p.placementId());
        if (rows.isEmpty())
            return new InternshipLink(p.placementId(), null, "WAITING_HANDOFF", null);
        var row = rows.getFirst();
        String caseId = (String) row.get("id");
        String route = "/workspace/cases/" + caseId;
        return new InternshipLink(p.placementId(), caseId, "AVAILABLE", route);
    }

    private static Instant parseInstant(String iso) {
        try { return Instant.parse(iso); }
        catch (Exception e) { throw failure(400, "SCHEMA_VALIDATION"); }
    }

    /**
     * Invitation must be ACCEPTED (T5 owns invitation).
     * Yêu cầu: "invitation T5 trạng thái ACCEPTED" khi tạo/duyệt Placement.
     */
    private void reCheckInvitationAccepted(ProposalService.Item item) {
        if (item.invitationId() == null) throw failure(409, "INVITATION_NOT_ACCEPTED");
        String invState = jdbc.query(
            "SELECT state FROM internship_invitations WHERE id=?",
            (r, n) -> r.getString("state"), item.invitationId())
            .stream().findFirst().orElse(null);
        if (!"ACCEPTED".equals(invState)) throw failure(409, "INVITATION_NOT_ACCEPTED");
    }

    /**
     * Append-only placement history. Called inside the business transaction
     * so rollback on any failure keeps history consistent.
     */
    private void writeHistory(String placementId, String fromState, String toState,
                               String actorId, String reasonCode, String decisionRef,
                               String operationId) {
        jdbc.update(
            "INSERT INTO placement_history(id,placement_id,from_state,to_state," +
            "actor_id,reason_code,decision_ref,operation_id,changed_at) " +
            "VALUES(?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",
            UUID.randomUUID().toString(), placementId, fromState, toState,
            actorId, reasonCode, decisionRef,
            operationId != null ? operationId : UUID.randomUUID().toString());
    }
}
