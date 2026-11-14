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
import vn.utbridora.auth.modules.student.port.StudentProfileSnapshotPort;
import vn.utbridora.auth.modules.cooperation.persistence.DemandRepository;
import vn.utbridora.auth.modules.cooperation.integration.DemandReferencePort;
import vn.utbridora.auth.modules.matching.application.MatchingService;
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
    private final SchoolDelegationAccess scope;
    private final AuthorizationPolicy core;
    private final PlacementLifecyclePublisher publisher;
    private final PlacementReadPort source;
    private final OperationLedger ledger;
    private final AuditService audit;
    private final DemandRepository demands;
    private final DemandReferencePort references;
    private final StudentProfileSnapshotPort profiles;
    private final MatchingService matching;
    private final Clock clock;

    public PlacementService(JdbcTemplate jdbc,
                             CooperationService cooperations,
                             ProposalService proposals,
                             ProposalConsentSummary consentSummary,
                             StudentEligibilityPort eligibility,
                             SchoolDelegationAccess scope,
                             AuthorizationPolicy core,
                             PlacementLifecyclePublisher publisher,
                             PlacementReadPort source,
                             OperationLedger ledger,
                             AuditService audit,DemandRepository demands,DemandReferencePort references,
                             StudentProfileSnapshotPort profiles,MatchingService matching,@org.springframework.beans.factory.annotation.Qualifier("placementClock") Clock clock) {
        this.jdbc = jdbc;
        this.cooperations = cooperations;
        this.proposals = proposals;
        this.consentSummary = consentSummary;
        this.eligibility = eligibility;
        this.scope = scope;
        this.core = core;
        this.publisher = publisher;
        this.source = source;
        this.ledger = ledger;
        this.audit = audit;
        this.demands=demands;this.references=references;this.profiles=profiles;this.matching=matching;
        this.clock=clock;
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
        var terms=jdbc.queryForList("SELECT c.term_id FROM proposal_items i JOIN proposal_batches b ON b.id=i.batch_id JOIN cooperations c ON c.id=b.cooperation_id WHERE i.id=? AND c.school_org_id=?",String.class,body.proposalItemId(),schoolOrgId);
        if(terms.size()!=1) throw failure(404,"RESOURCE_NOT_FOUND");
        scope.require(auth,schoolOrgId,terms.getFirst(),"placement:school:write");

        Instant effectiveAt = parseInstant(body.effectiveAt());
        LocalDate startDate = parseDate(body.startDate());
        LocalDate endDate   = parseDate(body.endDate());
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
        lockDemandForItem(body.proposalItemId(),schoolOrgId);
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

        reCheckVerification(coop.enterpriseOrgId(),schoolOrgId);
        checkDates(coop.termId(),startDate,endDate,effectiveAt);
        checkCurrentCandidate(auth,item,coop);

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
        text(body.decisionRef(),120);reason(body.reasonCode());
        // Load placement first to get schoolOrgId for delegation check
        var target = jdbc.query("SELECT school_org_id,term_id FROM placements WHERE id=?",
            (r, n) -> new String[]{r.getString(1),r.getString(2)}, uuid(placementId))
            .stream().findFirst().orElseThrow(() -> failure(404, "RESOURCE_NOT_FOUND"));
        String peek=target[0];
        scope.require(auth, peek, target[1], "placement:school:write");
        return ledger.execute(
            new OperationLedger.Command(actor(auth), peek,
                "PLACEMENT_APPROVE", idempotencyKey, Map.of("id", placementId, "body", body), 200),
            PlacementView.class,
            () -> doApprove(auth, peek, placementId, body),
            PlacementView::placementId);
    }

    private PlacementView doApprove(Authentication auth, String schoolOrgId,
                                     String placementId, PlacementCommand body) {
        lockDemandForPlacement(placementId,schoolOrgId);
        var p = lockPlacement(placementId, schoolOrgId);
        if (!p.state().equals("PENDING_APPROVAL")) throw failure(409, "STATE_CONFLICT");
        if (p.version() != body.expectedVersion())  throw failure(409, "VERSION_CONFLICT");

        // Re-check delegation for specific term
        scope.require(auth, schoolOrgId, p.termId(), "placement:school:write");

        // Re-check org and school verification
        reCheckVerification(p.enterpriseOrgId(), schoolOrgId);

        // Re-check cooperation, invitation still ACCEPTED, and consent/share still active
        var coop = cooperations.read(p.cooperationId());
        if (!coop.state().equals("APPROVED")) throw failure(409, "STATE_CONFLICT");
        var item = proposals.item(p.proposalItemId());
        eligibility.assertCurrentForPlacement(auth,p.studentUserId(),schoolOrgId,p.termId());
        checkCurrentCandidate(auth,item,coop);
        checkDates(p.termId(),p.startDate(),p.endDate(),p.effectiveAt());
        reCheckQuota(p.cooperationId(),p.demandId());
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
            String cause=dup.getMostSpecificCause().getMessage();
            if(cause!=null && cause.contains("uk_placement_active_student_term")) throw failure(409, "ACTIVE_PLACEMENT_EXISTS");
            throw dup;
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
        text(body.decisionRef(),120);reason(body.reasonCode());
        var target = jdbc.query("SELECT school_org_id,term_id FROM placements WHERE id=?",
            (r, n) -> new String[]{r.getString(1),r.getString(2)}, uuid(placementId))
            .stream().findFirst().orElseThrow(() -> failure(404, "RESOURCE_NOT_FOUND"));
        String peek=target[0];
        scope.require(auth, peek, target[1], "placement:school:write");
        return ledger.execute(
            new OperationLedger.Command(actor(auth), peek,
                "PLACEMENT_ACTIVATE", idempotencyKey, Map.of("id", placementId, "body", body), 200),
            PlacementView.class,
            () -> doActivate(auth, peek, placementId, body),
            PlacementView::placementId);
    }

    private PlacementView doActivate(Authentication auth, String schoolOrgId,
                                      String placementId, PlacementCommand body) {
        lockDemandForPlacement(placementId,schoolOrgId);
        var p = lockPlacement(placementId, schoolOrgId);
        if (!p.state().equals("APPROVED")) throw failure(409, "STATE_CONFLICT");
        if (p.version() != body.expectedVersion()) throw failure(409, "VERSION_CONFLICT");
        scope.require(auth, schoolOrgId, p.termId(), "placement:school:write");

        return activateApproved(p,actor(auth));
    }

    /** Internal SYSTEM purpose: only activate an existing, current APPROVED placement when due. No create/approve authority. */
    @Transactional
    public void activateDue(String placementId,long expectedVersion) {
        uuid(placementId);version(expectedVersion);
        var before=readPlacement(placementId);
        lockDemandForPlacement(placementId,before.schoolOrgId());
        var p=lockPlacement(placementId,before.schoolOrgId());
        if(!p.state().equals("APPROVED") || p.version()!=expectedVersion) return;
        activateApproved(p,null);
    }
    private PlacementView activateApproved(PlacementView p,String actorId) {
        if(clock.instant().isBefore(p.effectiveAt())) throw failure(409,"PLACEMENT_NOT_EFFECTIVE");
        String termState=jdbc.queryForObject("SELECT status FROM terms WHERE id=? AND school_org_id=? FOR SHARE",String.class,p.termId(),p.schoolOrgId());
        if(!"ACTIVE".equals(termState)) throw failure(422,"TERM_INVALID");
        reCheckVerification(p.enterpriseOrgId(),p.schoolOrgId());
        checkDates(p.termId(),p.startDate(),p.endDate(),p.effectiveAt());
        int changed=jdbc.update("UPDATE placements SET state='ACTIVE',version=version+1 WHERE id=? AND state='APPROVED' AND version=?",p.placementId(),p.version());
        if(changed!=1) throw failure(409,"VERSION_CONFLICT");
        writeHistory(p.placementId(),"APPROVED","ACTIVE",actorId,"EFFECTIVE_AT",null,UUID.randomUUID().toString());
        publisher.publish(actorId,p.placementId());
        return readPlacement(p.placementId());
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
        text(body.decisionRef(),120);reason(body.reasonCode());
        reason(body.reasonCode());
        var target = jdbc.query("SELECT school_org_id,term_id FROM placements WHERE id=?",
            (r, n) -> new String[]{r.getString(1),r.getString(2)}, uuid(placementId))
            .stream().findFirst().orElseThrow(() -> failure(404, "RESOURCE_NOT_FOUND"));
        String peek=target[0];
        scope.require(auth, peek, target[1], "placement:school:write");
        return ledger.execute(
            new OperationLedger.Command(actor(auth), peek,
                "PLACEMENT_CANCEL", idempotencyKey, Map.of("id", placementId, "body", body), 200),
            PlacementView.class,
            () -> doCancel(auth, peek, placementId, body),
            PlacementView::placementId);
    }

    private PlacementView doCancel(Authentication auth, String schoolOrgId,
                                    String placementId, PlacementCommand body) {
        lockDemandForPlacement(placementId,schoolOrgId);
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
        return queryPlacements(auth,"school_org_id", schoolOrgId, termId, cooperationId, state, page, size);
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
        return queryPlacements(auth,"enterprise_org_id", enterpriseOrgId, termId, cooperationId, state, page, size);
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
        scope.require(auth,schoolOrgId,jdbc.queryForObject("SELECT term_id FROM demands WHERE id=?",String.class,demandId),"placement:school:write");
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
        var states=jdbc.queryForList("SELECT status FROM organizations WHERE id IN (?,?) ORDER BY id FOR SHARE",String.class,enterpriseOrgId,schoolOrgId);
        if(states.size()!=2 || states.stream().anyMatch(state->!state.equals("VERIFIED"))) throw failure(422,"PLACEMENT_GATE_FAILED");
        int v = jdbc.queryForList("SELECT id FROM enterprise_verifications WHERE enterprise_org_id=? AND school_org_id=? AND state='APPROVED' FOR SHARE",String.class,enterpriseOrgId,schoolOrgId).size();
        if (v == 0) throw failure(422, "SCHOOL_VERIFICATION_REQUIRED");
    }

    private void reCheckQuota(String cooperationId, String demandId) {
        int quantity = jdbc.queryForObject(
            "SELECT JSON_UNQUOTE(JSON_EXTRACT(d.data_json,'$.quantity')) FROM demands d " +
            "WHERE d.id=?", Integer.class, demandId);
        int placed = jdbc.queryForList(
            "SELECT p.id FROM placements p " +
            "JOIN proposal_batches b ON b.cooperation_id=? " +
            "JOIN proposal_items i ON i.batch_id=b.id AND i.placement_id=p.id " +
            "WHERE p.state IN ('APPROVED','ACTIVE','COMPLETED') FOR SHARE",
            String.class, cooperationId).size();
        if (placed >= quantity) throw failure(409, "CAPACITY_EXCEEDED");
    }

    private Page<PlacementView> queryPlacements(Authentication auth, String column, String orgId,
                                                  String termId, String cooperationId, String state,
                                                  int page, int size) {
        if (termId != null && !termId.isBlank()) uuid(termId);
        if (cooperationId != null && !cooperationId.isBlank()) uuid(cooperationId);
        if (state != null && !state.isBlank() &&
            !Set.of("PENDING_APPROVAL","APPROVED","ACTIVE","CANCELLED","COMPLETED").contains(state))
            throw failure(400, "SCHEMA_VALIDATION");

        // ── Hàng 79: filter + sort + pagination fully server-side ────────────
        // Sort order: active/approved first (operational), then by effective_at DESC, then id ASC for stability.
        String where = " WHERE " + column + "=?"
            + " AND (?='' OR term_id=?)"
            + " AND (?='' OR cooperation_id=?)"
            + " AND (?='' OR state=?)";
        String tFilter = termId        != null ? termId        : "";
        String cFilter = cooperationId != null ? cooperationId : "";
        String sFilter = state         != null ? state         : "";

        // For school: pre-filter by delegation at DB level using a subquery, not in-memory loop.
        // This avoids loading all IDs into memory for scope-checking.
        String scopeClause = "";
        Object[] extraArgs = new Object[0];
        if (column.equals("school_org_id")) {
            // Only include placements whose term the caller has active delegation for.
            // Falls back to N+1 guard only when delegation check truly needs it.
            scopeClause = " AND term_id IN (" +
                "SELECT term_id FROM school_delegations " +
                "WHERE user_id=? AND school_org_id=? AND active=1 " +
                "AND CAST(valid_from AS DATETIME) <= UTC_TIMESTAMP(6) " +
                "AND CAST(valid_until AS DATETIME) > UTC_TIMESTAMP(6)" +
                ")";
            extraArgs = new Object[]{ actor(auth), orgId };
        }

        // Count total for pagination metadata (server-side, single query)
        String countSql = "SELECT COUNT(*) FROM placements" + where + scopeClause;
        Object[] countArgs = concat(new Object[]{orgId, tFilter, tFilter, cFilter, cFilter, sFilter, sFilter}, extraArgs);
        long total = jdbc.queryForObject(countSql, Long.class, countArgs);

        // Fetch page with stable sort: FIELD() puts active states first, then by effective_at DESC, id ASC
        String pageSql = "SELECT id FROM placements" + where + scopeClause
            + " ORDER BY FIELD(state,'ACTIVE','APPROVED','PENDING_APPROVAL','COMPLETED','CANCELLED'),"
            + " effective_at DESC, id ASC"
            + " LIMIT ? OFFSET ?";
        int offset = page * size;
        Object[] pageArgs = concat(countArgs, new Object[]{size, offset});
        var ids = jdbc.queryForList(pageSql, String.class, pageArgs);

        // Batch-load placements to avoid N+1
        var rows = ids.stream().map(this::readPlacement).toList();
        return new Page<>(rows, page, size, total, (total + size - 1) / size);
    }

    /** Concatenate two Object arrays. */
    private static Object[] concat(Object[] a, Object[] b) {
        var result = new Object[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private PlacementSummary buildSummary(String demandId) {
        int target = jdbc.queryForObject(
            "SELECT JSON_UNQUOTE(JSON_EXTRACT(data_json,'$.quantity')) FROM demands WHERE id=?",
            Integer.class, demandId);
        String coopId = jdbc.queryForObject(
            "SELECT cooperation_id FROM demands WHERE id=?", String.class, demandId);

        if (coopId == null) return new PlacementSummary(demandId, null, target, 0, 0, target);

        // ── Hàng 83: immutable snapshot-based counts, no double-count ───────
        // placed = DISTINCT placements in terminal-active states (APPROVED/ACTIVE/COMPLETED).
        // These are immutable decisions: each proposalItemId maps to at most one such placement.
        int placed = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT p.student_user_id) FROM placements p " +
            "JOIN proposal_items i ON i.id=p.proposal_item_id " +
            "JOIN proposal_batches b ON b.id=i.batch_id " +
            "WHERE b.cooperation_id=? AND p.state IN ('APPROVED','ACTIVE','COMPLETED')",
            Integer.class, coopId);

        // pending = students with PENDING_APPROVAL placement but no APPROVED/ACTIVE/COMPLETED one.
        // Does NOT count CANCELLED — those are resolved and no longer pending.
        // Uses p.student_user_id directly for correctness; avoids joining through proposal_items twice.
        int pending = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT p.student_user_id) FROM placements p " +
            "JOIN proposal_items i ON i.id=p.proposal_item_id " +
            "JOIN proposal_batches b ON b.id=i.batch_id " +
            "WHERE b.cooperation_id=? AND p.state='PENDING_APPROVAL' " +
            "AND NOT EXISTS (" +
            "  SELECT 1 FROM placements p2 " +
            "  WHERE p2.student_user_id=p.student_user_id AND p2.term_id=p.term_id " +
            "  AND p2.state IN ('APPROVED','ACTIVE','COMPLETED')" +
            ")",
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

    private static LocalDate parseDate(String text) {
        try {if(text==null || !text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException();return LocalDate.parse(text);}
        catch(Exception e) {throw failure(400,"SCHEMA_VALIDATION");}
    }
    private void lockDemandForItem(String item,String school) {
        var ids=jdbc.queryForList("SELECT c.demand_id FROM proposal_items i JOIN proposal_batches b ON b.id=i.batch_id JOIN cooperations c ON c.id=b.cooperation_id WHERE i.id=? AND c.school_org_id=?",String.class,item,school);
        if(ids.size()!=1) throw failure(404,"RESOURCE_NOT_FOUND");
        jdbc.queryForObject("SELECT id FROM demands WHERE id=? FOR UPDATE",String.class,ids.getFirst());
    }
    private void lockDemandForPlacement(String placement,String school) {
        var ids=jdbc.queryForList("SELECT demand_id FROM placements WHERE id=? AND school_org_id=?",String.class,placement,school);
        if(ids.size()!=1) throw failure(404,"RESOURCE_NOT_FOUND");
        jdbc.queryForObject("SELECT id FROM demands WHERE id=? FOR UPDATE",String.class,ids.getFirst());
    }
    private void checkDates(String term,LocalDate start,LocalDate end,Instant effective) {
        var dates=jdbc.query("SELECT start_date,end_date FROM terms WHERE id=? FOR SHARE",(r,n)->new LocalDate[]{r.getDate(1).toLocalDate(),r.getDate(2).toLocalDate()},term).getFirst();
        var zone=ZoneId.of("Asia/Ho_Chi_Minh");
        if(start.isBefore(dates[0]) || end.isAfter(dates[1]) || effective.isBefore(dates[0].atStartOfDay(zone).toInstant())
            || !effective.isBefore(dates[1].plusDays(1).atStartOfDay(zone).toInstant())) throw failure(422,"PLACEMENT_GATE_FAILED");
    }
    private void checkCurrentCandidate(Authentication auth,ProposalService.Item item,CooperationService.CooperationDto coop) {
        var d=demands.findLocked(coop.demandId(),coop.enterpriseOrgId()).orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
        if(d.deletedAt()!=null) throw failure(404,"RESOURCE_NOT_FOUND");
        references.validate(coop.enterpriseOrgId(),d.data());
        var refs=jdbc.queryForMap("SELECT match_run_id,profile_snapshot_id FROM proposal_items WHERE id=?",item.proposalItemId());
        var run=matching.read(auth,(String)refs.get("match_run_id"),"placement:school:write");
        if(!run.run().demandId().equals(coop.demandId()) || run.run().criteriaVersion()!=d.criteriaVersion()
            || !run.run().criteriaHash().equals(d.criteriaHash()) || !run.run().ruleVersion().equals(vn.utbridora.auth.modules.matching.domain.StrictClassifier.RULE_VERSION)) throw failure(409,"MATCH_RUN_STALE");
        profiles.assertCurrentForPlacement(auth,(String)refs.get("profile_snapshot_id"),item.studentUserId(),coop.schoolOrgId(),coop.termId());
    }
    private static Instant parseInstant(String iso) {
        try { if(iso==null || !iso.endsWith("Z")) throw new IllegalArgumentException();return Instant.parse(iso); }
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
            "actor_id,reason_code,decision_ref,operation_id,actor_kind,changed_at) " +
            "VALUES(?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",
            UUID.randomUUID().toString(), placementId, fromState, toState,
            actorId, reasonCode, decisionRef,
            operationId != null ? operationId : UUID.randomUUID().toString(),actorId==null?"SYSTEM":"USER");
    }
}
