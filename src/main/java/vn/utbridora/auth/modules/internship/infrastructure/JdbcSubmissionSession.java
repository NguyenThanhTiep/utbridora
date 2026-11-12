package vn.utbridora.auth.modules.internship.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.audit.AuditAction;
import vn.utbridora.auth.modules.internship.api.SubmissionDtos.*;
import vn.utbridora.auth.modules.internship.api.TaskDtos.*;
import vn.utbridora.auth.modules.internship.application.SubmissionPorts.*;
import vn.utbridora.auth.modules.internship.domain.*;
import vn.utbridora.auth.operations.OperationLedger;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** URL submission/review adapter for V29. No artifact UPDATE/DELETE.
 * No File Service stand-in: file-mode rows cannot be reconstructed by inventing empty file refs.
 */
public final class JdbcSubmissionSession implements Session {
    private static final String CONTEXT = """
        SELECT a.id assignment_id,a.case_id,a.student_user_id,a.state,a.version assignment_version,a.current_submission_id,
               t.id task_id,t.title,t.description,t.due_at,t.priority,t.late_allowed,t.version task_version,
               g.id group_id,g.code group_code,g.version group_version,
               c.id cohort_id,c.enterprise_org_id,c.school_org_id,c.term_id,c.code cohort_code,c.name cohort_name,c.version cohort_version
        FROM internship_task_assignments a JOIN internship_tasks t ON t.id=a.task_id
        JOIN internship_groups g ON g.id=t.group_id JOIN internship_cohorts c ON c.id=g.cohort_id
        WHERE a.id=?
        """;
    private static final String ARTIFACT = "SELECT id,assignment_id,version_no,url,note,submitted_at,late_flag FROM internship_submissions";
    private final JdbcTemplate jdbc;
    private final OperationLedger ledger;
    private final AuditService audit;

    public JdbcSubmissionSession(JdbcTemplate jdbc, OperationLedger ledger, AuditService audit) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.ledger = Objects.requireNonNull(ledger);
        this.audit = Objects.requireNonNull(audit);
    }

    /** Every request waits for the real, ordered V28/V29 baseline; never applies migrations itself.
     * Flyway remains responsible for checksum validation at application startup.
     */
    public void requireBaseline() {
        transaction();
        try {
            var rows = jdbc.query("SELECT version,script,installed_rank FROM flyway_schema_history WHERE success=true AND version IN ('28','29') ORDER BY installed_rank",
                    (r, n) -> List.of(r.getString("version"), r.getString("script")));
            if (!rows.equals(List.of(List.of("28", "V28__private_file_service.sql"),
                    List.of("29", "V29__t3_submission_review_comment.sql")))) throw unavailable();
            // Fail closed when a numbered migration does not provide the expected columns.
            jdbc.query(ARTIFACT + " WHERE 1=0", (r, n) -> r.getString("id"));
        } catch (DataAccessException missingSchema) {
            throw unavailable();
        }
    }

    @Override public AssignmentContext assignment(UUID id) {
        transaction();
        return context(id);
    }

    @Override public AssignmentContext lockAssignment(UUID id) {
        transaction();
        // Called only after CaseChecks has locked Placement then Case. Avoid the week-2
        // group/cohort FOR UPDATE helpers before those source locks.
        var tasks = jdbc.queryForList("SELECT task_id FROM internship_task_assignments WHERE id=?", String.class, id.toString());
        if (tasks.isEmpty()) return null;
        var lockedTasks = jdbc.queryForList("SELECT id FROM internship_tasks WHERE id=? FOR SHARE", String.class, tasks.getFirst());
        if (lockedTasks.isEmpty()) throw unavailable();
        var locked = jdbc.queryForList("SELECT task_id FROM internship_task_assignments WHERE id=? FOR UPDATE", String.class, id.toString());
        if (locked.isEmpty()) return null;
        if (!tasks.equals(locked)) throw unavailable();
        // Task and assignment are already locked. The UnitOfWork enforces READ_COMMITTED,
        // so this read sees their current values. Do not lock group/cohort in this join:
        // week-2 Start locks those before Case, reversing our source -> Case lock order.
        // Original Task group/cohort scope is immutable in the existing owner APIs.
        return context(id);
    }

    private AssignmentContext context(UUID id) {
        return jdbc.query(CONTEXT, (r, n) -> {
            var assignment = new AssignmentDto(id, uuid(r, "case_id"), uuid(r, "student_user_id"),
                    AssignmentState.valueOf(r.getString("state")), r.getLong("assignment_version"), nullableUuid(r, "current_submission_id"));
            var cohort = new Cohort(uuid(r, "cohort_id"), new WorkspaceScope(uuid(r, "enterprise_org_id"),
                    uuid(r, "school_org_id"), uuid(r, "term_id")), r.getString("cohort_code"), r.getString("cohort_name"), r.getLong("cohort_version"));
            // Scope-only projection; access delegates real membership/handover to WorkspaceAuthority.
            var group = new InternshipGroup(uuid(r, "group_id"), cohort.id(), r.getString("group_code"), List.of(), List.of(), r.getLong("group_version"));
            var task = new InternshipTask(new TaskDto(uuid(r, "task_id"), group.id(), r.getString("title"), r.getString("description"),
                    r.getObject("due_at", LocalDateTime.class).toInstant(ZoneOffset.UTC), TaskPriority.valueOf(r.getString("priority")),
                    r.getBoolean("late_allowed"), r.getLong("task_version"), List.of(assignment)));
            return new AssignmentContext(cohort, group, task, assignment);
        }, id.toString()).stream().findFirst().orElse(null);
    }

    @Override public SubmissionDto submission(UUID id) {
        transaction();
        return jdbc.query(ARTIFACT + " WHERE id=?", this::artifact, id.toString()).stream().findFirst().orElse(null);
    }

    private SubmissionDto artifact(ResultSet r, int index) throws SQLException {
        if (r.getString("url") == null) throw unavailable(); // File refs require the real CP-FILE integration.
        return new SubmissionDto(uuid(r, "id"), uuid(r, "assignment_id"), r.getInt("version_no"), List.of(),
                r.getString("url"), r.getString("note"), r.getObject("submitted_at", LocalDateTime.class).toInstant(ZoneOffset.UTC), r.getBoolean("late_flag"));
    }

    @Override public <T> T idempotent(OperationLedger.Command command, Class<T> type, Supplier<T> mutation, Function<T, String> effect) {
        transaction();
        return ledger.execute(command, type, mutation, effect);
    }

    @Override public boolean hasHistory(UUID assignmentId) {
        transaction();
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM internship_submissions WHERE assignment_id=?)", Boolean.class, assignmentId.toString()));
    }

    @Override public void appendFirst(AssignmentDto expected, SubmissionDto value) {
        transaction();
        if (!value.assignmentId().equals(expected.assignmentId()) || value.versionNo() != 1
                || value.url() == null || !value.fileIds().isEmpty()) throw unavailable();
        SubmissionPolicy.requireSubmission(expected.state(), expected.version(), expected.version());
        if (expected.currentSubmissionId() != null || hasHistory(expected.assignmentId())) throw failure(409, "STATE_CONFLICT");
        try {
            jdbc.update("INSERT INTO internship_submissions(id,assignment_id,version_no,url,note,submitted_at,late_flag) VALUES (?,?,?,?,?,?,?)",
                    value.submissionId().toString(), value.assignmentId().toString(), value.versionNo(), value.url(), value.note(),
                    LocalDateTime.ofInstant(value.submittedAt(), ZoneOffset.UTC), value.lateFlag());
        } catch (DuplicateKeyException duplicate) {
            throw failure(409, "STATE_CONFLICT");
        }
        int changed = jdbc.update("UPDATE internship_task_assignments SET state='SUBMITTED',version=version+1,current_submission_id=? WHERE id=? AND version=? AND state='IN_PROGRESS' AND current_submission_id IS NULL AND version<9007199254740991",
                value.submissionId().toString(), expected.assignmentId().toString(), expected.version());
        if (changed != 1) throw failure(409, "VERSION_CONFLICT");
    }

    @Override public void audit(String action, UUID actor, UUID school, UUID submission, Map<String, Object> metadata) {
        transaction();
        if (!Set.of("SUBMISSION_CREATED", "SUBMISSION_REVISED", "SUBMISSION_REVIEWED").contains(action)) throw unavailable();
        try {
            AuditAction.valueOf(action);
            audit.workflow(actor.toString(), action, submission.toString(), school.toString(), metadata);
        } catch (IllegalArgumentException unsupportedMapping) {
            // Reject unknown mappings/metadata if the owner registry no longer matches.
            // Never alias the action or skip the mandatory audit; outer transaction rolls back.
            throw unavailable();
        }
    }

    @Override public HistoryPage history(UUID assignmentId, int page, int size) {
        transaction();
        page(page, size);
        // A shared assignment lock serializes count/page with writers under either RC or RR.
        // Count and page are consistent reads taken after this lock in the same transaction.
        var assignments = jdbc.queryForList("SELECT id FROM internship_task_assignments WHERE id=? FOR SHARE", String.class, assignmentId.toString());
        if (assignments.isEmpty()) throw failure(404, "RESOURCE_NOT_FOUND");
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM internship_submissions WHERE assignment_id=?", Long.class, assignmentId.toString());
        if (count == null) throw unavailable();
        var content = jdbc.query(ARTIFACT + " WHERE assignment_id=? ORDER BY version_no DESC,id ASC LIMIT ? OFFSET ?",
                this::artifact, assignmentId.toString(), size, (long) page * size);
        return new HistoryPage(content, count);
    }

    @Override public boolean hasReview(UUID submissionId) {
        transaction();
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM internship_submission_reviews WHERE submission_id=?)", Boolean.class, submissionId.toString()));
    }

    @Override public ReviewDto reviewForSubmission(UUID submissionId) {
        transaction();
        return jdbc.query("SELECT id,submission_id,reviewed_version_no,verdict,comment,reviewer_user_id,created_at,assignment_version FROM internship_submission_reviews WHERE submission_id=?",
                (r,n)->new ReviewDto(uuid(r,"id"),uuid(r,"submission_id"),r.getInt("reviewed_version_no"),ReviewVerdict.valueOf(r.getString("verdict")),
                        r.getString("comment"),uuid(r,"reviewer_user_id"),r.getObject("created_at",LocalDateTime.class).toInstant(ZoneOffset.UTC),r.getLong("assignment_version")),
                submissionId.toString()).stream().findFirst().orElse(null);
    }

    @Override public void appendReview(AssignmentDto expected, AssignmentDto updated, ReviewDto review) {
        transaction();
        SubmissionPolicy.requireReview(expected.state(),expected.version(),expected.version(),review.submissionId(),expected.currentSubmissionId());
        if (!expected.assignmentId().equals(updated.assignmentId()) || !expected.caseId().equals(updated.caseId())
                || !expected.studentUserId().equals(updated.studentUserId()) || !review.submissionId().equals(updated.currentSubmissionId())
                || updated.state()!=SubmissionPolicy.reviewedState(review.verdict()) || updated.version()!=expected.version()+1
                || !review.assignmentVersion().equals(updated.version())) throw unavailable();
        try {
            jdbc.update("INSERT INTO internship_submission_reviews(id,submission_id,reviewed_version_no,verdict,comment,reviewer_user_id,created_at,assignment_version) VALUES (?,?,?,?,?,?,?,?)",
                    review.reviewId().toString(),review.submissionId().toString(),review.reviewedVersionNo(),review.verdict().name(),review.comment(),
                    review.reviewerUserId().toString(),LocalDateTime.ofInstant(review.createdAt(),ZoneOffset.UTC),review.assignmentVersion());
        } catch (DuplicateKeyException duplicate) { throw failure(409,"STATE_CONFLICT"); }
        if (jdbc.update("UPDATE internship_task_assignments SET state=?,version=version+1 WHERE id=? AND version=? AND state='SUBMITTED' AND current_submission_id=? AND version<9007199254740991",
                updated.state().name(),expected.assignmentId().toString(),expected.version(),review.submissionId().toString())!=1) throw failure(409,"VERSION_CONFLICT");
    }

    @Override public void appendRevision(AssignmentDto expected, SubmissionDto previous, SubmissionDto next) {
        transaction();
        SubmissionPolicy.requireSubmission(expected.state(),expected.version(),expected.version());
        if (!previous.assignmentId().equals(expected.assignmentId()) || !previous.submissionId().equals(expected.currentSubmissionId())
                || !next.assignmentId().equals(expected.assignmentId()) || previous.versionNo()==Integer.MAX_VALUE
                || next.versionNo()!=previous.versionNo()+1 || next.url()==null || !next.fileIds().isEmpty()) throw unavailable();
        try {
            jdbc.update("INSERT INTO internship_submissions(id,assignment_id,version_no,url,note,submitted_at,late_flag) VALUES (?,?,?,?,?,?,?)",
                    next.submissionId().toString(),next.assignmentId().toString(),next.versionNo(),next.url(),next.note(),LocalDateTime.ofInstant(next.submittedAt(),ZoneOffset.UTC),next.lateFlag());
        } catch (DuplicateKeyException duplicate) { throw failure(409,"VERSION_CONFLICT"); }
        if (jdbc.update("UPDATE internship_task_assignments SET state='SUBMITTED',version=version+1,current_submission_id=? WHERE id=? AND version=? AND state='IN_PROGRESS' AND current_submission_id=? AND version<9007199254740991",
                next.submissionId().toString(),expected.assignmentId().toString(),expected.version(),previous.submissionId().toString())!=1) throw failure(409,"VERSION_CONFLICT");
    }

    private static UUID uuid(ResultSet r, String field) throws SQLException { return UUID.fromString(r.getString(field)); }
    private static UUID nullableUuid(ResultSet r, String field) throws SQLException {
        String value = r.getString(field);return value == null ? null : UUID.fromString(value);
    }
    private static void transaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Submission requires the domain transaction");
    }
    private static RuntimeException unavailable() { return failure(503, "DEPENDENCY_UNAVAILABLE"); }
}
