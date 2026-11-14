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
import vn.utbridora.auth.operations.OperationFailure;
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

    public sealed interface ProgressCounters permits OperationalCounters, SchoolProgressCounters {
        int target(); int placed(); int pending(); int remaining();
    }
    public record OperationalCounters(int target, int placed, int pending, int remaining) implements ProgressCounters {
        static OperationalCounters empty() {return new OperationalCounters(0,0,0,0);}
        OperationalCounters plus(ProgressCounters other) {
            return new OperationalCounters(target+other.target(),placed+other.placed(),pending+other.pending(),remaining+other.remaining());
        }
    }
    public record SchoolProgressCounters(int target,int placed,int pending,int remaining,int reservations,int declined,
                                        int enterpriseAccepted,int pendingCreation,int pendingApproval,int openForNomination) implements ProgressCounters {
        static SchoolProgressCounters empty() {return new SchoolProgressCounters(0,0,0,0,0,0,0,0,0,0);}
        SchoolProgressCounters plus(SchoolProgressCounters other) {
            return new SchoolProgressCounters(target+other.target,placed+other.placed,pending+other.pending,remaining+other.remaining,
                reservations+other.reservations,declined+other.declined,enterpriseAccepted+other.enterpriseAccepted,
                pendingCreation+other.pendingCreation,pendingApproval+other.pendingApproval,openForNomination+other.openForNomination);
        }
    }
    public record ProgressChoice(String id,String name) {}
    public record Progress<C extends ProgressCounters>(String demandId,String title,String schoolOrgId,String schoolName,
            String enterpriseOrgId,String enterpriseName,String termId,String termCode,String cooperationId,
            String cooperationState,String status,C counters) {}
    public record ProgressPage<C extends ProgressCounters>(List<Progress<C>> content,int page,int size,long totalElements,
            int totalPages,C totals,List<ProgressChoice> schools,List<ProgressChoice> terms) {}
    private record ProgressDemand(String id,String title,String school,String schoolName,String enterprise,
                                  String enterpriseName,String term,String termCode,String cooperation,String state) {}

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
        return listForSchool(auth, schoolOrgId, termId, null, cooperationId, state, page, size);
    }

    @Transactional(readOnly = true)
    public Page<PlacementView> listForSchool(Authentication auth, String schoolOrgId,
                                              String termId, String demandId, String cooperationId, String state,
                                              int page, int size) {
        return listForSchool(auth,schoolOrgId,termId,demandId,cooperationId,state,null,null,page,size);
    }

    @Transactional(readOnly = true)
    public Page<PlacementView> listForSchool(Authentication auth,String schoolOrgId,String termId,String demandId,
                                              String cooperationId,String state,String filterSchoolOrgId,String query,int page,int size) {
        page(page, size);
        schoolOrgId=uuid(schoolOrgId);termId=optionalId(termId);demandId=optionalId(demandId);cooperationId=optionalId(cooperationId);
        requireSchoolRead(auth, schoolOrgId, termId);
        filterSchoolOrgId=optionalId(filterSchoolOrgId);
        if(filterSchoolOrgId!=null && !schoolOrgId.equals(filterSchoolOrgId)) throw failure(404,"RESOURCE_NOT_FOUND");
        String resourceTerm=filterTerm("school_org_id",schoolOrgId,demandId,cooperationId);
        if(resourceTerm!=null) requireSchoolRead(auth,schoolOrgId,resourceTerm);
        return queryPlacements(auth,"school_org_id", schoolOrgId, termId!=null?termId:resourceTerm, demandId, cooperationId, state, filterSchoolOrgId, query, page, size);
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
        return listForEnterprise(auth, enterpriseOrgId, termId, null, cooperationId, state, page, size);
    }

    @Transactional(readOnly = true)
    public Page<PlacementView> listForEnterprise(Authentication auth, String enterpriseOrgId,
                                                  String termId, String demandId, String cooperationId, String state,
                                                  int page, int size) {
        return listForEnterprise(auth,enterpriseOrgId,termId,demandId,cooperationId,state,null,null,page,size);
    }

    @Transactional(readOnly = true)
    public Page<PlacementView> listForEnterprise(Authentication auth,String enterpriseOrgId,String termId,String demandId,
                                                  String cooperationId,String state,String schoolOrgId,String query,int page,int size) {
        page(page, size);
        enterpriseOrgId=uuid(enterpriseOrgId);termId=optionalId(termId);demandId=optionalId(demandId);cooperationId=optionalId(cooperationId);
        requireEnterpriseRead(auth, enterpriseOrgId);
        schoolOrgId=optionalId(schoolOrgId);
        if(schoolOrgId!=null && jdbc.queryForObject("SELECT COUNT(*) FROM demands WHERE enterprise_org_id=? AND school_org_id=? AND soft_deleted_at IS NULL",Integer.class,enterpriseOrgId,schoolOrgId)==0)
            throw failure(404,"RESOURCE_NOT_FOUND");
        filterTerm("enterprise_org_id",enterpriseOrgId,demandId,cooperationId);
        return queryPlacements(auth,"enterprise_org_id", enterpriseOrgId, termId, demandId, cooperationId, state, schoolOrgId, query, page, size);
    }

    @Transactional(readOnly = true)
    public PlacementSummary summaryForSchool(Authentication auth, String schoolOrgId,
                                              String demandId) {
        demandId=uuid(demandId);schoolOrgId=uuid(schoolOrgId);
        requireSchoolRead(auth, schoolOrgId, null);
        String termId=filterTerm("school_org_id",schoolOrgId,demandId,null);
        requireSchoolRead(auth,schoolOrgId,termId);
        return buildSummary(demandId);
    }

    @Transactional(readOnly = true)
    public PlacementSummary summaryForEnterprise(Authentication auth, String enterpriseOrgId,
                                                  String demandId) {
        demandId=uuid(demandId);enterpriseOrgId=uuid(enterpriseOrgId);
        requireEnterpriseRead(auth, enterpriseOrgId);
        filterTerm("enterprise_org_id",enterpriseOrgId,demandId,null);
        return buildSummary(demandId);
    }

    @Transactional(readOnly = true)
    public InternshipLink internshipLinkForSchool(Authentication auth, String schoolOrgId,
                                                   String placementId) {
        var p = readPlacement(placementId);
        if (!p.schoolOrgId().equals(uuid(schoolOrgId))) throw failure(404, "RESOURCE_NOT_FOUND");
        requireSchoolRead(auth, uuid(schoolOrgId), p.termId());
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

    @Transactional(readOnly=true)
    public ProgressPage<ProgressCounters> progressForSchool(Authentication actor,String org,String schoolFilter,String termFilter,
            String demandFilter,String cooperationFilter,String status,String query,int page,int size) {
        return readProgress(actor,org,true,schoolFilter,termFilter,demandFilter,cooperationFilter,status,query,page,size);
    }

    @Transactional(readOnly=true)
    public ProgressPage<ProgressCounters> progressForEnterprise(Authentication actor,String org,String schoolFilter,String termFilter,
            String demandFilter,String cooperationFilter,String status,String query,int page,int size) {
        return readProgress(actor,org,false,schoolFilter,termFilter,demandFilter,cooperationFilter,status,query,page,size);
    }

    private ProgressPage<ProgressCounters> readProgress(Authentication actor,String org,boolean school,String schoolFilter,String termFilter,
            String demandFilter,String cooperationFilter,String status,String query,int page,int size) {
        org=uuid(org);page(page,size);schoolFilter=optionalId(schoolFilter);termFilter=optionalId(termFilter);
        demandFilter=optionalId(demandFilter);cooperationFilter=optionalId(cooperationFilter);
        if(status!=null && !status.isBlank() && !Set.of("FULL","SHORT","WAITING","REJECTED","NO_COOP").contains(status)) throw failure(400,"SCHEMA_VALIDATION");
        String search=query==null || query.isBlank()?"":text(query,150).toLowerCase(Locale.ROOT);
        if(school) {requireSchoolRead(actor,org,termFilter);if(schoolFilter!=null && !schoolFilter.equals(org)) throw failure(404,"RESOURCE_NOT_FOUND");}
        else requireEnterpriseRead(actor,org);
        String resourceTerm=filterTerm(school?"school_org_id":"enterprise_org_id",org,demandFilter,cooperationFilter);
        if(school && resourceTerm!=null) requireSchoolRead(actor,org,resourceTerm);
        var rows=jdbc.query("SELECT d.id,JSON_UNQUOTE(JSON_EXTRACT(d.data_json,'$.title')) title,d.school_org_id,s.name school_name,d.enterprise_org_id,e.name enterprise_name,d.term_id,t.term_code,c.id cooperation_id,c.state " +
            "FROM demands d JOIN organizations s ON s.id=d.school_org_id JOIN organizations e ON e.id=d.enterprise_org_id " +
            "JOIN terms t ON t.id=d.term_id AND t.school_org_id=d.school_org_id LEFT JOIN cooperations c ON c.demand_id=d.id AND c.school_org_id=d.school_org_id AND c.enterprise_org_id=d.enterprise_org_id AND c.term_id=d.term_id " +
            "WHERE d."+(school?"school_org_id":"enterprise_org_id")+"=? AND d.soft_deleted_at IS NULL ORDER BY d.created_at DESC,d.id ASC",
            (r,n)->new ProgressDemand(r.getString("id"),r.getString("title"),r.getString("school_org_id"),r.getString("school_name"),r.getString("enterprise_org_id"),r.getString("enterprise_name"),r.getString("term_id"),r.getString("term_code"),r.getString("cooperation_id"),r.getString("state")),org);
        List<ProgressDemand> visible=new ArrayList<>();
        for(var row:rows) {
            if(school) try {requireSchoolRead(actor,org,row.term());}
            catch(OperationFailure denied) {if(denied.status==403 || denied.status==404) continue;throw denied;}
            visible.add(row);
        }
        if(schoolFilter!=null) {
            boolean found=false;for(var row:visible) if(row.school().equals(schoolFilter)) {found=true;break;}
            if(!found) throw failure(404,"RESOURCE_NOT_FOUND");
        }
        List<Progress<ProgressCounters>> result=new ArrayList<>();
        OperationalCounters operationalTotals=OperationalCounters.empty();SchoolProgressCounters schoolTotals=SchoolProgressCounters.empty();
        for(var row:visible) {
            if(schoolFilter!=null && !schoolFilter.equals(row.school()) || termFilter!=null && !termFilter.equals(row.term())
                || demandFilter!=null && !demandFilter.equals(row.id()) || cooperationFilter!=null && !cooperationFilter.equals(row.cooperation())) continue;
            if(!search.isEmpty() && !(row.id()+" "+row.title()+" "+Objects.toString(row.cooperation(),"")).toLowerCase(Locale.ROOT).contains(search)
                && jdbc.queryForObject("SELECT COUNT(*) FROM placements WHERE demand_id=? AND cooperation_id=? AND school_org_id=? AND enterprise_org_id=? AND term_id=? " +
                    "AND LOCATE(LOWER(?),LOWER(CONCAT_WS(' ',id,proposal_item_id,student_user_id)))>0",Integer.class,row.id(),row.cooperation(),row.school(),row.enterprise(),row.term(),search)==0) continue;
            // The enterprise branch never computes or serializes the school's nomination/consent pipeline.
            ProgressCounters counters;
            if(!"APPROVED".equals(row.state())) counters=school?SchoolProgressCounters.empty():OperationalCounters.empty();
            else if(school) counters=schoolProgressCounters(row);
            else {
                var summary=buildSummary(row.id());
                counters=new OperationalCounters(summary.target(),summary.placed(),summary.pending(),summary.remaining());
            }
            String aggregate=row.cooperation()==null?"NO_COOP":"REJECTED".equals(row.state())?"REJECTED":!"APPROVED".equals(row.state())?"WAITING"
                :counters.target()>0 && counters.placed()>=counters.target()?"FULL":counters.pending()>0?"WAITING":"SHORT";
            if(status!=null && !status.isBlank() && !status.equals(aggregate)) continue;
            result.add(new Progress<>(row.id(),row.title(),row.school(),row.schoolName(),row.enterprise(),row.enterpriseName(),row.term(),row.termCode(),row.cooperation(),row.state(),aggregate,counters));
            if(school) schoolTotals=schoolTotals.plus((SchoolProgressCounters)counters);
            else operationalTotals=operationalTotals.plus(counters);
        }
        int from=Math.min(page*size,result.size());
        return new ProgressPage<>(List.copyOf(result.subList(from,Math.min(from+size,result.size()))),page,size,result.size(),(result.size()+size-1)/size,
            school?schoolTotals:operationalTotals,progressChoices(visible,true),progressChoices(visible,false));
    }

    /** Called only after the exact demand-term delegation guard; never used for enterprise reads. */
    private SchoolProgressCounters schoolProgressCounters(ProgressDemand row) {
        var summary=buildSummary(row.id());
        var counts=jdbc.queryForMap("SELECT " +
            "COUNT(DISTINCT CASE WHEN i.state='DECLINED' THEN i.student_user_id END) declined," +
            "COUNT(DISTINCT CASE WHEN i.state IN ('ENTERPRISE_ACCEPTED','PLACED') THEN i.student_user_id END) accepted " +
            "FROM proposal_items i JOIN proposal_batches b ON b.id=i.batch_id WHERE b.cooperation_id=?",row.cooperation());
        int declined=((Number)counts.get("declined")).intValue(),accepted=((Number)counts.get("accepted")).intValue();
        int pendingApproval=jdbc.queryForObject("SELECT COUNT(DISTINCT student_user_id) FROM placements WHERE cooperation_id=? AND demand_id=? AND school_org_id=? AND enterprise_org_id=? AND term_id=? AND state='PENDING_APPROVAL'",
            Integer.class,row.cooperation(),row.id(),row.school(),row.enterprise(),row.term());
        var candidates=jdbc.query("SELECT i.* FROM proposal_items i JOIN proposal_batches b ON b.id=i.batch_id WHERE b.cooperation_id=? AND i.state='ENTERPRISE_ACCEPTED' AND i.reservation_until>? " +
            "AND EXISTS(SELECT 1 FROM internship_invitations v WHERE v.id=i.invitation_id AND v.student_user_id=i.student_user_id AND v.state='ACCEPTED') " +
            "AND NOT EXISTS(SELECT 1 FROM placements p WHERE p.proposal_item_id=i.id)",
            (r,n)->new ProposalService.Item(r.getString("id"),r.getString("batch_id"),r.getString("student_user_id"),row.cooperation(),r.getString("state"),r.getLong("version"),instant(r,"reservation_until"),r.getString("invitation_id"),r.getString("share_id"),r.getString("placement_id")),row.cooperation(),utc(clock.instant()));
        int pendingCreation=0;if(!candidates.isEmpty()) {
            var cooperation=cooperations.read(row.cooperation());
            for(var candidate:candidates) {var consent=consentSummary.read(candidate,cooperation);if(consent.activeConsentId()!=null && consent.shareIsActive()) pendingCreation++;}
        }
        return new SchoolProgressCounters(summary.target(),summary.placed(),summary.pending(),summary.remaining(),summary.pending(),declined,
            accepted,pendingCreation,pendingApproval,Math.max(0,summary.target()-summary.placed()-summary.pending()));
    }

    private static List<ProgressChoice> progressChoices(List<ProgressDemand> rows,boolean school) {
        Map<String,String> names=new TreeMap<>();for(var row:rows) names.put(school?row.school():row.term(),school?row.schoolName():row.termCode());
        return names.entrySet().stream().map(entry->new ProgressChoice(entry.getKey(),entry.getValue())).sorted(Comparator.comparing(ProgressChoice::name).thenComparing(ProgressChoice::id)).toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void requireSchoolRead(Authentication auth,String schoolOrgId,String termId) {
        try {scope.require(auth,uuid(schoolOrgId),termId,"placement:school:write");}
        catch(vn.utbridora.auth.operations.OperationFailure denied) {
            if(denied.status!=403 && denied.status!=404) throw denied;
            scope.require(auth,uuid(schoolOrgId),termId,"t3:workspace:read");
        }
    }

    private static String optionalId(String value) {
        return value==null || value.isBlank()?null:uuid(value);
    }

    /** Validate each reference against the selected owner before returning even an empty page. */
    private String filterTerm(String column,String orgId,String demandId,String cooperationId) {
        String term=null;
        if(cooperationId!=null) {
            var rows=jdbc.queryForList("SELECT term_id FROM cooperations WHERE id=? AND "+column+"=?",String.class,cooperationId,orgId);
            if(rows.size()!=1) throw failure(404,"RESOURCE_NOT_FOUND");
            term=rows.getFirst();
        }
        if(demandId!=null) {
            var rows=jdbc.queryForList("SELECT JSON_UNQUOTE(JSON_EXTRACT(data_json,'$.termId')) FROM demands WHERE id=? AND "+column+"=? AND soft_deleted_at IS NULL",String.class,demandId,orgId);
            if(rows.size()!=1 || rows.getFirst()==null || term!=null && !term.equals(rows.getFirst())) throw failure(404,"RESOURCE_NOT_FOUND");
            term=rows.getFirst();
        }
        return term;
    }

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

    private Page<PlacementView> queryPlacements(Authentication auth,String column,String orgId,
            String termId,String demandId,String cooperationId,String state,String schoolOrgId,String query,int page,int size) {
        if(termId!=null && !termId.isBlank()) uuid(termId);
        if(demandId!=null && !demandId.isBlank()) uuid(demandId);
        if(cooperationId!=null && !cooperationId.isBlank()) uuid(cooperationId);
        if(state!=null && !state.isBlank() && !Set.of("PENDING_APPROVAL","APPROVED","ACTIVE","CANCELLED","COMPLETED").contains(state))
            throw failure(400,"SCHEMA_VALIDATION");
        String from=" FROM placements p JOIN demands d ON d.id=p.demand_id AND d.enterprise_org_id=p.enterprise_org_id AND d.school_org_id=p.school_org_id AND d.term_id=p.term_id " +
            "JOIN cooperations c ON c.id=p.cooperation_id AND c.demand_id=d.id AND c.school_org_id=p.school_org_id AND c.enterprise_org_id=p.enterprise_org_id AND c.term_id=p.term_id";
        String where=" WHERE p."+column+"=? AND (?='' OR p.term_id=?) AND (?='' OR p.demand_id=?) AND (?='' OR p.cooperation_id=?) AND (?='' OR p.state=?)" +
            " AND (?='' OR p.school_org_id=?) AND (?='' OR LOCATE(LOWER(?),LOWER(CONCAT_WS(' ',p.id,p.demand_id,p.cooperation_id,p.proposal_item_id,p.student_user_id,p.state,p.start_date,p.end_date,JSON_UNQUOTE(JSON_EXTRACT(d.data_json,'$.title')))))>0)";
        String t=termId==null?"":termId,d=demandId==null?"":demandId,c=cooperationId==null?"":cooperationId;
        String s=state==null || state.isBlank()?"":state,school=schoolOrgId==null?"":schoolOrgId,q=query==null || query.isBlank()?"":text(query,150);
        List<Object> args=new ArrayList<>(List.of(orgId,t,t,d,d,c,c,s,s,school,school,q,q));
        if(column.equals("school_org_id")) {
            // Use the core guard, including permission and delegation ancestry, before SQL count/page.
            List<String> allowedTerms=new ArrayList<>();
            for(String term:jdbc.queryForList("SELECT DISTINCT p.term_id"+from+where,String.class,args.toArray())) {
                try {requireSchoolRead(auth,orgId,term);allowedTerms.add(term);}
                catch(OperationFailure denied) {if(denied.status!=403 && denied.status!=404) throw denied;}
            }
            if(allowedTerms.isEmpty()) return new Page<>(List.of(),page,size,0,0);
            where+=" AND p.term_id IN ("+String.join(",",Collections.nCopies(allowedTerms.size(),"?"))+")";
            args.addAll(allowedTerms);
        }
        long total=jdbc.queryForObject("SELECT COUNT(*)"+from+where,Long.class,args.toArray());
        args.add(size);args.add(page*size);
        var ids=jdbc.queryForList("SELECT p.id"+from+where+
            " ORDER BY FIELD(p.state,'ACTIVE','APPROVED','PENDING_APPROVAL','COMPLETED','CANCELLED'),p.effective_at DESC,p.id ASC LIMIT ? OFFSET ?",
            String.class,args.toArray());
        return new Page<>(ids.stream().map(this::readPlacement).toList(),page,size,total,Math.toIntExact((total+size-1)/size));
    }

    private PlacementSummary buildSummary(String demandId) {
        String coopId = jdbc.queryForObject(
            "SELECT cooperation_id FROM demands WHERE id=?", String.class, demandId);
        var approvedTargets = coopId==null?List.<Integer>of():jdbc.queryForList(
            // Submission snapshots are stored in cooperation_history in this checkout.
            "SELECT JSON_UNQUOTE(JSON_EXTRACT(s.data_json,'$.demand.data.quantity')) FROM cooperation_history s " +
            "WHERE s.cooperation_id=? AND s.operation='SUBMIT' AND EXISTS (" +
            "SELECT 1 FROM cooperation_history d WHERE d.cooperation_id=s.cooperation_id AND d.submission_no=s.submission_no AND d.operation='APPROVE') " +
            "ORDER BY s.submission_no DESC,s.created_at DESC,s.id ASC LIMIT 1",
            Integer.class, coopId);
        int target = approvedTargets.isEmpty()?jdbc.queryForObject(
            "SELECT JSON_UNQUOTE(JSON_EXTRACT(data_json,'$.quantity')) FROM demands WHERE id=?",
            Integer.class, demandId):approvedTargets.getFirst();
        int placed = coopId == null ? 0 : jdbc.queryForObject(
            "SELECT COUNT(DISTINCT p.id) FROM placements p JOIN demands d ON d.id=p.demand_id AND d.school_org_id=p.school_org_id AND d.enterprise_org_id=p.enterprise_org_id AND d.term_id=p.term_id " +
            "WHERE p.cooperation_id=? AND p.demand_id=? AND p.state IN ('APPROVED','ACTIVE','COMPLETED')",
            Integer.class, coopId,demandId);
        int pending = coopId == null ? 0 : jdbc.queryForObject(
            "SELECT COUNT(DISTINCT i.student_user_id) " +
            "FROM proposal_items i JOIN proposal_batches b ON b.id=i.batch_id " +
            "WHERE b.cooperation_id=? " +
            "AND ((i.state IN ('RESERVED','INVITED','ACCEPTED','SHARED','ENTERPRISE_ACCEPTED') AND i.reservation_until>?) " +
            "  OR EXISTS (SELECT 1 FROM placements p WHERE p.proposal_item_id=i.id AND p.state='PENDING_APPROVAL')) " +
            "AND NOT EXISTS (SELECT 1 FROM placements p WHERE p.proposal_item_id=i.id AND p.state IN ('APPROVED','ACTIVE','COMPLETED','CANCELLED'))",
            Integer.class, coopId, utc(clock.instant()));
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
