package vn.utbridora.auth.modules.student.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.authorization.SchoolDelegationAccess;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page;
import vn.utbridora.auth.modules.student.port.StudentOpportunityPort;
import vn.utbridora.auth.organization.MembershipRepository;
import vn.utbridora.auth.operations.OperationLedger;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@Service
public class StudentPreferenceService {
    public record PreferenceView(String preferenceId, String studentUserId, String schoolOrgId, String termId,
            String opportunityId, int intentVersion, long version, String status, String message,
            Instant createdAt, Instant withdrawnAt, Instant reviewedAt, String reviewedByUserId) {}
    public record SubmitPreference(String action, String opportunityId, String message, Integer expectedIntentVersion) {}
    public record WithdrawPreference(String action, String preferenceId, Long expectedVersion, String reasonCode) {}
    public record ReviewPreference(Long expectedVersion, String reasonCode) {}

    private record Stored(String preferenceId, String studentUserId, String schoolOrgId, String termId,
            String opportunityId, int intentVersion, long version, String status, String message,
            Instant createdAt, Instant withdrawnAt, Instant reviewedAt, String reviewedByUserId) {
        PreferenceView view() {
            return new PreferenceView(preferenceId, studentUserId, schoolOrgId, termId, opportunityId,
                intentVersion, version, status, message, createdAt, withdrawnAt, reviewedAt, reviewedByUserId);
        }
    }

    private final JdbcTemplate jdbc;
    // T5 owns the Opportunity projection (BC22); T2 reads it only through its own consumer port.
    private final StudentOpportunityPort opportunities;
    private final MembershipRepository memberships;
    private final AuthorizationPolicy authorization;
    private final StudentTermScopePort studentTerms;
    private final SchoolDelegationAccess schoolAccess;
    private final OperationLedger operations;
    private final AuditService audit;

    public StudentPreferenceService(JdbcTemplate jdbc, StudentOpportunityPort opportunities,
            MembershipRepository memberships, AuthorizationPolicy authorization, StudentTermScopePort studentTerms,
            SchoolDelegationAccess schoolAccess, OperationLedger operations, AuditService audit) {
        this.jdbc = jdbc;
        this.opportunities = opportunities;
        this.memberships = memberships;
        this.authorization = authorization;
        this.studentTerms = studentTerms;
        this.schoolAccess = schoolAccess;
        this.operations = operations;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<PreferenceView> mine(Authentication auth, String requestedTerm, int page, int size) {
        page(page, size);
        String student = actor(auth);
        String term = requestedTerm == null ? null : uuid(requestedTerm);
        requireStudent(auth, student, term);
        String where = " WHERE student_user_id=? AND (? IS NULL OR term_id=?)";
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM student_preferences" + where, Long.class,
            student, term, term);
        List<PreferenceView> all = jdbc.query("SELECT * FROM student_preferences" + where +
                " ORDER BY updated_at DESC,preference_id ASC LIMIT ? OFFSET ?",
            (row, index) -> StudentPreferenceService.map(row, index).view(),
            student, term, term, size, (long) page * size);
        return new Page<>(all, page, size, total, (int) ((total + size - 1) / size));
    }

    @Transactional
    public PreferenceView submit(Authentication auth, String idempotencyKey, SubmitPreference request) {
        String student = actor(auth), opportunityId = uuid(request.opportunityId()), key = uuid(idempotencyKey);
        if (!"SUBMIT".equals(request.action()) || request.expectedIntentVersion() == null
                || request.expectedIntentVersion() < 0 || request.expectedIntentVersion() > 2_147_483_646) {
            throw failure(400, "SCHEMA_VALIDATION");
        }
        String message = normalizedMessage(request.message());
        var opportunity = opportunities.publishedForStudent(auth, opportunityId)
            .orElseThrow(() -> failure(404, "RESOURCE_NOT_FOUND"));
        // School/term come from the opportunity; the student must be linked to that term in that school.
        if (!opportunity.schoolOrgId().equals(requireStudent(auth, student, opportunity.termId()))) {
            throw failure(404, "RESOURCE_NOT_FOUND");
        }
        return operations.execute(new OperationLedger.Command(student, opportunity.schoolOrgId(),
                "STUDENT_PREFERENCE_SUBMIT", key, request, 201), PreferenceView.class, () -> {
            Instant now = Instant.now();
            if (now.isBefore(opportunity.publishedAt()) || !now.isBefore(opportunity.closedAt())) {
                throw failure(409, "OPPORTUNITY_CLOSED");
            }
            String preferenceId = jdbc.queryForList(
                "SELECT preference_id FROM student_preferences WHERE student_user_id=? AND opportunity_id=? AND term_id=? FOR UPDATE",
                String.class, student, opportunityId, opportunity.termId()).stream().findFirst().orElse(null);
            Stored current = preferenceId == null ? null : locked(preferenceId);
            int currentIntentVersion = current == null ? 0 : current.intentVersion();
            if (request.expectedIntentVersion() != currentIntentVersion) throw failure(409, "VERSION_CONFLICT");
            // BC22 only allows SUBMITTED -> WITHDRAWN/REVIEWED; a new intent opens once the current one is closed (C.0: wrong state -> STATE_CONFLICT).
            if (current != null && current.status().equals("SUBMITTED")) throw failure(409, "STATE_CONFLICT");
            Instant changedAt = Instant.now();
            if (current == null) {
                preferenceId = UUID.randomUUID().toString();
                jdbc.update("INSERT INTO student_preferences(preference_id,student_user_id,school_org_id,term_id,opportunity_id," +
                        "intent_version,version,status,message,intent_created_at,created_at,updated_at) " +
                        "VALUES(?,?,?,?,?,1,1,'SUBMITTED',?,?,?,?)", preferenceId, student, opportunity.schoolOrgId(),
                    opportunity.termId(), opportunityId, message, utc(changedAt), utc(changedAt), utc(changedAt));
            } else {
                version(current.version() + 1);
                jdbc.update("UPDATE student_preferences SET intent_version=?,version=?,status='SUBMITTED',message=?," +
                        "intent_created_at=?,withdrawn_at=NULL,reviewed_at=NULL,reviewed_by_user_id=NULL,updated_at=? " +
                        "WHERE preference_id=? AND version=?", current.intentVersion() + 1, current.version() + 1,
                    message, utc(changedAt), utc(changedAt), preferenceId, current.version());
            }
            Stored saved = locked(preferenceId);
            history(saved, "SUBMIT", student, null, changedAt);
            audit.workflow(student, "PREFERENCE_SUBMITTED", preferenceId, opportunity.schoolOrgId(),
                Map.of("opportunityId", opportunityId, "termId", opportunity.termId(), "version", saved.version()));
            return saved.view();
        }, PreferenceView::preferenceId);
    }

    @Transactional
    public PreferenceView withdraw(Authentication auth, String idempotencyKey, WithdrawPreference request) {
        String student = actor(auth), key = uuid(idempotencyKey), id = uuid(request.preferenceId());
        if (!"WITHDRAW".equals(request.action())) throw failure(400, "SCHEMA_VALIDATION");
        version(request.expectedVersion());
        reason(request.reasonCode());
        Stored before = locked(id);
        if (!student.equals(before.studentUserId())) throw failure(404, "RESOURCE_NOT_FOUND");
        requireStudent(auth, student, before.termId());
        return operations.execute(new OperationLedger.Command(student, before.schoolOrgId(),
                "STUDENT_PREFERENCE_WITHDRAW", key, request, 200), PreferenceView.class, () -> {
            Stored current = locked(id);
            if (!student.equals(current.studentUserId())) throw failure(404, "RESOURCE_NOT_FOUND");
            // AC-BC22: withdrawing a reviewed intent answers PREFERENCE_REVIEWED even with a stale version.
            if (current.status().equals("REVIEWED")) throw failure(409, "PREFERENCE_REVIEWED");
            if (current.version() != request.expectedVersion()) throw failure(409, "VERSION_CONFLICT");
            if (!current.status().equals("SUBMITTED")) throw failure(409, "STATE_CONFLICT");
            Instant changedAt = Instant.now();
            jdbc.update("UPDATE student_preferences SET status='WITHDRAWN',version=version+1,withdrawn_at=?,updated_at=? " +
                    "WHERE preference_id=? AND version=?", utc(changedAt), utc(changedAt), id, current.version());
            Stored saved = locked(id);
            history(saved, "WITHDRAW", student, request.reasonCode(), changedAt);
            audit.workflow(student, "PREFERENCE_WITHDRAWN", id, saved.schoolOrgId(),
                Map.of("version", saved.version(), "reasonCode", request.reasonCode()));
            return saved.view();
        }, PreferenceView::preferenceId);
    }

    @Transactional
    public PreferenceView review(Authentication auth, String idempotencyKey, String preferenceId,
                                 ReviewPreference request) {
        String reviewer = actor(auth), id = uuid(preferenceId), key = uuid(idempotencyKey);
        version(request.expectedVersion());
        reason(request.reasonCode());
        Stored before = locked(id);
        schoolAccess.require(auth, before.schoolOrgId(), before.termId(), "student:preference:school:review");
        return operations.execute(new OperationLedger.Command(reviewer, before.schoolOrgId(),
                "STUDENT_PREFERENCE_REVIEW", key, Map.of("preferenceId", id, "request", request), 200),
            PreferenceView.class, () -> {
                Stored current = locked(id);
                schoolAccess.require(auth, current.schoolOrgId(), current.termId(), "student:preference:school:review");
                if (current.version() != request.expectedVersion()) throw failure(409, "VERSION_CONFLICT");
                if (!current.status().equals("SUBMITTED")) throw failure(409, "STATE_CONFLICT");
                Instant changedAt = Instant.now();
                jdbc.update("UPDATE student_preferences SET status='REVIEWED',version=version+1,reviewed_at=?," +
                        "reviewed_by_user_id=?,updated_at=? WHERE preference_id=? AND version=?",
                    utc(changedAt), reviewer, utc(changedAt), id, current.version());
                Stored saved = locked(id);
                history(saved, "REVIEW", reviewer, request.reasonCode(), changedAt);
                audit.workflow(reviewer, "PREFERENCE_REVIEWED", id, saved.schoolOrgId(),
                    Map.of("version", saved.version(), "decision", "REVIEW", "decisionRef", request.reasonCode()));
                return saved.view();
            }, PreferenceView::preferenceId);
    }

    private void history(Stored value, String operation, String actorId, String reasonCode, Instant at) {
        jdbc.update("INSERT INTO student_preference_history(id,preference_id,intent_version,version,operation,actor_id," +
                "reason_code,snapshot_json,created_at) VALUES(?,?,?,?,?,?,?,?,?)", UUID.randomUUID().toString(),
            value.preferenceId(), value.intentVersion(), value.version(), operation, actorId, reasonCode,
            JSON.writeValueAsString(value.view()), utc(at));
    }

    private Stored locked(String id) {
        return jdbc.query("SELECT * FROM student_preferences WHERE preference_id=? FOR UPDATE",
            StudentPreferenceService::map, uuid(id)).stream().findFirst()
            .orElseThrow(() -> failure(404, "RESOURCE_NOT_FOUND"));
    }

    /** Returns the linked school when a term is given (null otherwise); denies when the student scope is missing. */
    private String requireStudent(Authentication auth, String student, String termId) {
        if (termId != null) {
            String school = studentTerms.schoolOfLinkedTerm(student, termId)
                .orElseThrow(() -> failure(404, "RESOURCE_NOT_FOUND"));
            if (authorization.hasOrganization(auth, school, Role.STUDENT, "student:preference:self:manage")) return school;
        } else if (memberships.findByUserIdAndActiveTrue(student).stream()
                .anyMatch(membership -> membership.role == Role.STUDENT && membership.organizationId != null
                    && authorization.hasOrganization(auth, membership.organizationId, Role.STUDENT,
                        "student:preference:self:manage"))) {
            return null;
        }
        audit.securityDenied(student, "AUTHORIZATION_DENIED");
        throw failure(403, "ACCESS_DENIED");
    }

    private static Stored map(ResultSet row, int index) throws SQLException {
        Instant withdrawn = instant(row, "withdrawn_at"), reviewed = instant(row, "reviewed_at");
        return new Stored(row.getString("preference_id"), row.getString("student_user_id"),
            row.getString("school_org_id"), row.getString("term_id"), row.getString("opportunity_id"),
            row.getInt("intent_version"), row.getLong("version"), row.getString("status"), row.getString("message"),
            instant(row, "intent_created_at"), withdrawn, reviewed, row.getString("reviewed_by_user_id"));
    }

    private static String normalizedMessage(String value) {
        if (value == null) return null;
        String normalized = value.strip();
        if (normalized.codePointCount(0, normalized.length()) > 1000) throw failure(400, "SCHEMA_VALIDATION");
        return normalized;
    }
}
