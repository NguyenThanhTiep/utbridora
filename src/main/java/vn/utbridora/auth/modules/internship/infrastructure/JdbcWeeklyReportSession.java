package vn.utbridora.auth.modules.internship.infrastructure;

import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.utbridora.auth.modules.internship.application.WeeklyReportPorts;
import vn.utbridora.auth.modules.internship.domain.WeeklyReport;
import vn.utbridora.auth.modules.internship.domain.WeeklyReport.*;
import static vn.utbridora.auth.modules.internship.infrastructure.WeeklyReportRuntimeChecks.*;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** V31 Report persistence. Joins the shared transaction; schema callback checks readiness without DDL. */
public final class JdbcWeeklyReportSession implements WeeklyReportPorts.Session {
    private static final String ROOT = "SELECT id,case_id,week_start,due_at,current_revision_no,version FROM internship_weekly_reports";
    private static final String FILTER = " FROM internship_weekly_reports r JOIN internship_weekly_report_revisions c"
            + " ON c.report_id=r.id AND c.revision_no=r.current_revision_no WHERE r.case_id=? AND (? IS NULL OR c.state=?)";
    private final JdbcTemplate jdbc;
    private final Runnable schema;
    public JdbcWeeklyReportSession(JdbcTemplate jdbc, Runnable requireApprovedSchema) {
        this.jdbc = available(jdbc); this.schema = available(requireApprovedSchema);
    }
    private record Header(UUID id, UUID caseId, LocalDate week, Instant due, int current, long version) {}
    private record RevisionRow(int number, long version, State state, String work, String difficulties, String plan,
                               String reason, Instant submitted, boolean late) {}
    private record FileRow(int revision, int ordinal, UUID id) {}
    private record ReviewRow(int revision, Review value) {}
    private record PageRow(Header header, long total) {}
    private Header header(ResultSet row, int ignored) throws SQLException {
        var result = new Header(UUID.fromString(row.getString("id")), UUID.fromString(row.getString("case_id")),
                row.getObject("week_start", LocalDate.class), row.getObject("due_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                row.getInt("current_revision_no"), row.getLong("version"));
        if (result.current() < 1 || result.version() < 1) throw unavailable();
        return result;
    }
    private Header root(UUID id, boolean lock) {
        var rows = jdbc.query(ROOT + " WHERE id=?" + (lock ? " FOR UPDATE" : ""), this::header, id.toString());
        if (rows.size() > 1 || !rows.isEmpty() && !id.equals(rows.getFirst().id())) throw unavailable();
        return rows.isEmpty() ? null : rows.getFirst();
    }
    @Override public WeeklyReport find(UUID id) {
        Dev06Jdbc.ready(schema, false); available(id);
        try { var value = root(id, false); return value == null ? null : load(value); }
        catch (DataAccessException | IllegalArgumentException | NullPointerException invalidStorage) { throw unavailable(); }
    }
    private WeeklyReport load(Header root) {
        String id = root.id().toString();
        var rows = jdbc.query("SELECT revision_no,report_version,state,completed_work,difficulties,next_plan,reason_code,submitted_at,submitted_submicro_nanos,late_flag"
                + " FROM internship_weekly_report_revisions WHERE report_id=? ORDER BY revision_no", (r, n) -> new RevisionRow(
                r.getInt("revision_no"), r.getLong("report_version"), State.valueOf(r.getString("state")), r.getString("completed_work"),
                r.getString("difficulties"), r.getString("next_plan"), r.getString("reason_code"),
                Dev06Jdbc.instant(r.getObject("submitted_at", LocalDateTime.class), r.getObject("submitted_submicro_nanos", Integer.class)), r.getBoolean("late_flag")), id);
        var reviews = jdbc.query("SELECT revision_no,reviewer_user_id,comment,reviewed_at,reviewed_submicro_nanos"
                + " FROM internship_weekly_report_reviews WHERE report_id=? ORDER BY revision_no", (r, n) -> new ReviewRow(r.getInt("revision_no"),
                new Review(UUID.fromString(r.getString("reviewer_user_id")), r.getString("comment"), Dev06Jdbc.instant(
                        r.getObject("reviewed_at", LocalDateTime.class), r.getObject("reviewed_submicro_nanos", Integer.class)))), id);
        var files = jdbc.query("SELECT revision_no,ordinal_no,file_id FROM internship_weekly_report_files WHERE report_id=? ORDER BY revision_no,ordinal_no",
                (r, n) -> new FileRow(r.getInt("revision_no"), r.getInt("ordinal_no"), UUID.fromString(r.getString("file_id"))), id);
        var reviewMap = new HashMap<Integer, Review>(); var fileMap = new HashMap<Integer, List<UUID>>();
        for (var review : reviews) {
            if (review.revision() < 1 || review.revision() > root.current() || reviewMap.put(review.revision(), review.value()) != null) throw unavailable();
        }
        for (var file : files) {
            if (file.revision() < 1 || file.revision() > root.current()) throw unavailable();
            var refs = fileMap.computeIfAbsent(file.revision(), ignored -> new ArrayList<>());
            if (file.ordinal() != refs.size() + 1 || file.ordinal() > 20) throw unavailable();
            refs.add(file.id());
        }
        var history = new ArrayList<Revision>();
        for (var row : rows) history.add(new Revision(row.number(), row.version(), row.state(),
                new Content(row.work(), row.difficulties(), row.plan(), fileMap.getOrDefault(row.number(), List.of())),
                row.reason(), row.submitted(), reviewMap.get(row.number()), row.late()));
        if (history.size() != root.current()) throw unavailable();
        var value = WeeklyReport.restore(root.id(), new Key(root.caseId(), root.week()), root.due(), root.version(), history);
        // Under RC another writer may have committed between the root/child reads. Never return
        // mixed generations. Mutations already own the root lock; reads fail safely for retry.
        if (!root.equals(root(root.id(), false))) throw failure(409, "VERSION_CONFLICT");
        return value;
    }
    @Override public boolean insertIfAbsent(WeeklyReport value) {
        Dev06Jdbc.ready(schema, true); available(value);
        if (value.version() != 1 || value.revisions().size() != 1 || value.current().state() != State.DRAFT
                || !value.current().content().evidenceFileIds().isEmpty()) throw unavailable();
        try {
            int changed;
            try {
                changed = jdbc.update("INSERT INTO internship_weekly_reports(id,case_id,week_start,due_at,current_revision_no,version) VALUES (?,?,?,?,?,?)",
                        value.reportId().toString(), value.key().caseId().toString(), value.key().weekStart(), Dev06Jdbc.micros(value.dueAt()), 1, 1L);
            } catch (DuplicateKeyException duplicateRoot) { return false; }
            one(changed); insertRevision(value.reportId(), value.current()); return true;
        } catch (DataAccessException invalidStorage) { throw unavailable(); }
    }
    @Override public boolean replace(WeeklyReport value, long expectedVersion) {
        Dev06Jdbc.ready(schema, true); available(value);
        try {
            var header = root(value.reportId(), true);
            if (header == null || header.version() != expectedVersion) return false;
            var before = load(header); validateChange(before, value, expectedVersion);
            int changed = jdbc.update("UPDATE internship_weekly_reports SET current_revision_no=?,version=? WHERE id=? AND version=?",
                    value.current().revisionNo(), value.version(), value.reportId().toString(), expectedVersion);
            if (changed == 0) return false;
            one(changed);
            if (value.current().revisionNo() > before.current().revisionNo()) insertRevision(value.reportId(), value.current());
            else {
                updateRevision(value.reportId(), before.current(), value.current());
                if (!before.current().content().evidenceFileIds().equals(value.current().content().evidenceFileIds())) {
                    jdbc.update("DELETE FROM internship_weekly_report_files WHERE report_id=? AND revision_no=?", value.reportId().toString(), value.current().revisionNo());
                    insertFiles(value.reportId(), value.current());
                }
                if (value.current().review() != null) insertReview(value.reportId(), value.current());
            }
            return true;
        } catch (DuplicateKeyException race) { throw failure(409, "VERSION_CONFLICT"); }
        catch (DataAccessException | IllegalArgumentException | NullPointerException invalidStorage) { throw unavailable(); }
    }
    private void validateChange(WeeklyReport before, WeeklyReport after, long expected) {
        if (!before.key().equals(after.key()) || !before.dueAt().equals(after.dueAt())) throw unavailable();
        var current = after.current(); WeeklyReport canonical;
        if (current.revisionNo() == before.current().revisionNo() + 1) canonical = before.revise(expected, current.reasonCode());
        else if (before.current().state() == State.DRAFT && current.state() == State.DRAFT) canonical = before.update(expected, current.content());
        else if (before.current().state() == State.DRAFT && current.state() == State.SUBMITTED) canonical = before.submit(expected, current.submittedAt());
        else if (before.current().state() == State.SUBMITTED && current.state() == State.REVIEWED && current.review() != null) {
            var review = current.review(); canonical = before.review(expected, review.reviewerUserId(), review.comment(), review.createdAt());
        } else throw unavailable();
        if (!canonical.revisions().equals(after.revisions())) throw unavailable();
    }
    private void insertRevision(UUID id, Revision value) {
        var c = value.content();
        one(jdbc.update("INSERT INTO internship_weekly_report_revisions(report_id,revision_no,report_version,state,completed_work,difficulties,next_plan,reason_code,submitted_at,submitted_submicro_nanos,late_flag) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id.toString(), value.revisionNo(), value.version(), value.state().name(), c.completedWork(), c.difficulties(), c.nextPlan(), value.reasonCode(),
                Dev06Jdbc.micros(value.submittedAt()), Dev06Jdbc.remainder(value.submittedAt()), value.lateFlag()));
        insertFiles(id, value);
    }
    private void updateRevision(UUID id, Revision before, Revision value) {
        var c = value.content();
        int changed = jdbc.update("UPDATE internship_weekly_report_revisions SET report_version=?,state=?,completed_work=?,difficulties=?,next_plan=?,submitted_at=?,submitted_submicro_nanos=?,late_flag=? WHERE report_id=? AND revision_no=? AND report_version=? AND state=?",
                value.version(), value.state().name(), c.completedWork(), c.difficulties(), c.nextPlan(), Dev06Jdbc.micros(value.submittedAt()),
                Dev06Jdbc.remainder(value.submittedAt()), value.lateFlag(), id.toString(), value.revisionNo(), before.version(), before.state().name());
        if (changed == 0) throw failure(409, "VERSION_CONFLICT"); one(changed);
    }
    private void insertFiles(UUID id, Revision value) {
        int ordinal = 0;
        for (var file : value.content().evidenceFileIds()) one(jdbc.update(
                "INSERT INTO internship_weekly_report_files(report_id,revision_no,ordinal_no,file_id) VALUES (?,?,?,?)",
                id.toString(), value.revisionNo(), ++ordinal, file.toString()));
    }
    private void insertReview(UUID id, Revision revision) {
        var value = revision.review();
        one(jdbc.update("INSERT INTO internship_weekly_report_reviews(report_id,revision_no,reviewer_user_id,comment,reviewed_at,reviewed_submicro_nanos) VALUES (?,?,?,?,?,?)",
                id.toString(), revision.revisionNo(), value.reviewerUserId().toString(), value.comment(), Dev06Jdbc.micros(value.createdAt()), Dev06Jdbc.remainder(value.createdAt())));
    }
    @Override public WeeklyReportPorts.PageSlice list(UUID caseId, State state, int page, int size) {
        Dev06Jdbc.ready(schema, false); available(caseId); vn.utbridora.auth.operations.WorkflowValues.page(page, size);
        String filter = state == null ? null : state.name();
        try {
            // Count and page share one SQL snapshot, including an empty/out-of-range page.
            var rows = jdbc.query("SELECT p.*,totals.total_elements FROM (SELECT COUNT(*) total_elements" + FILTER + ") totals LEFT JOIN ("
                    + "SELECT r.id,r.case_id,r.week_start,r.due_at,r.current_revision_no,r.version" + FILTER
                    + " ORDER BY r.week_start DESC,r.id ASC LIMIT ? OFFSET ?) p ON TRUE ORDER BY p.week_start DESC,p.id ASC",
                    (r, n) -> new PageRow(r.getString("id") == null ? null : header(r, n), r.getLong("total_elements")),
                    caseId.toString(), filter, filter, caseId.toString(), filter, filter, size, (long) page * size);
            if (rows.isEmpty()) throw unavailable();
            long total = rows.getFirst().total(); var values = new ArrayList<WeeklyReport>();
            for (var row : rows) {
                if (row.total() != total) throw unavailable();
                if (row.header() != null) {
                    var value = load(row.header());
                    if (!caseId.equals(value.key().caseId()) || state != null && value.current().state() != state) throw unavailable();
                    values.add(value);
                } else if (rows.size() != 1) throw unavailable();
            }
            return new WeeklyReportPorts.PageSlice(values, total);
        } catch (DataAccessException | IllegalArgumentException | NullPointerException invalidStorage) { throw unavailable(); }
    }
    private static void one(int changed) { if (changed != 1) throw unavailable(); }
}
