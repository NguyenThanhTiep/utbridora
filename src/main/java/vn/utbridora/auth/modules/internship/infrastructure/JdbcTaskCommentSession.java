package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.utbridora.auth.modules.internship.api.SubmissionDtos.CommentDto;
import vn.utbridora.auth.modules.internship.application.TaskCommentPorts.*;
import vn.utbridora.auth.modules.internship.domain.TaskComment;
import static vn.utbridora.auth.modules.internship.infrastructure.WeeklyReportRuntimeChecks.*;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** FR-17 JDBC append/list. Physical V29 ownership/names and assignment version semantics are
 * supplied explicitly by the owners; this class does not allocate a table, migration or policy.
 * No bean/default layout/version updater is installed. No Assignment repository is written here.
 */
public final class JdbcTaskCommentSession implements Session {
    public record Layout(String table, String id, String assignment, String author, String text, String createdAt,
                         String submicroRemainder) {
        public Layout {
            for (String name : List.of(table, id, assignment, author, text, createdAt)) Dev06Jdbc.identifier(name);
            if (submicroRemainder != null) Dev06Jdbc.identifier(submicroRemainder);
            var names = new HashSet<>(List.of(id, assignment, author, text, createdAt));
            if (names.size() != 5 || submicroRemainder != null && !names.add(submicroRemainder)) throw new IllegalArgumentException("Duplicate comment columns");
        }
    }
    /** Owner implements either preserve or increment under its real assignment lock and the
     * SAME transaction. No default picks a policy, and this adapter never issues Assignment SQL. */
    public interface AssignmentVersions { long afterAppend(Target locked); }
    private final JdbcTemplate jdbc;
    private final Layout layout;
    private final AssignmentVersions versions;
    private final Runnable schema;
    public JdbcTaskCommentSession(JdbcTemplate jdbc, Layout approvedLayout, AssignmentVersions ownerVersions, Runnable requireApprovedSchema) {
        this.jdbc = available(jdbc); this.layout = available(approvedLayout); this.versions = available(ownerVersions);
        this.schema = available(requireApprovedSchema);
    }
    private String q(String value) { return Dev06Jdbc.identifier(value); }
    @Override public AppendReceipt append(Target locked, TaskComment comment) {
        Dev06Jdbc.ready(schema, true); available(locked); available(comment);
        var value = comment.value();
        if (!locked.assignment().assignmentId().equals(value.assignmentId())) throw unavailable();
        if (layout.submicroRemainder() == null && value.createdAt().getNano() % 1000 != 0) throw unavailable();
        try {
            var columns = String.join(",", q(layout.id()), q(layout.assignment()), q(layout.author()), q(layout.text()), q(layout.createdAt()));
            var parameters = new ArrayList<Object>(List.of(value.commentId().toString(), value.assignmentId().toString(),
                    value.authorUserId().toString(), value.text(), Dev06Jdbc.micros(value.createdAt())));
            String placeholders = "?,?,?,?,?";
            if (layout.submicroRemainder() != null) {
                columns += "," + q(layout.submicroRemainder()); placeholders += ",?"; parameters.add(Dev06Jdbc.remainder(value.createdAt()));
            }
            if (jdbc.update("INSERT INTO " + q(layout.table()) + "(" + columns + ") VALUES (" + placeholders + ")", parameters.toArray()) != 1) throw unavailable();
            long version = versions.afterAppend(locked);
            if (version < locked.assignment().version()) throw unavailable();
            return new AppendReceipt(value, version);
        } catch (DuplicateKeyException race) { throw failure(409, "VERSION_CONFLICT"); }
        catch (DataAccessException invalidStorage) { throw unavailable(); }
    }
    private record PageRow(CommentDto comment, long total) {}
    @Override public CommentPage comments(UUID assignmentId, int page, int size) {
        Dev06Jdbc.ready(schema, false); available(assignmentId); vn.utbridora.auth.operations.WorkflowValues.page(page, size);
        String nanos = layout.submicroRemainder() == null ? "0" : q(layout.submicroRemainder());
        String projection = q(layout.id()) + " id," + q(layout.assignment()) + " assignment_id," + q(layout.author())
                + " author_user_id," + q(layout.text()) + " comment_text," + q(layout.createdAt()) + " created_at," + nanos + " submicro_nanos";
        String table = q(layout.table()), assigned = q(layout.assignment());
        String order = q(layout.createdAt()) + " ASC," + (layout.submicroRemainder() == null ? "" : nanos + " ASC,") + q(layout.id()) + " ASC";
        try {
            var rows = jdbc.query("SELECT p.*,totals.total_elements FROM (SELECT COUNT(*) total_elements FROM " + table + " WHERE " + assigned
                    + "=?) totals LEFT JOIN (SELECT " + projection + " FROM " + table + " WHERE " + assigned + "=? ORDER BY "
                    + order + " LIMIT ? OFFSET ?) p ON TRUE"
                    + " ORDER BY p.created_at ASC,p.submicro_nanos ASC,p.id ASC", (r, n) -> new PageRow(r.getString("id") == null ? null : new CommentDto(
                    UUID.fromString(r.getString("id")), UUID.fromString(r.getString("assignment_id")), UUID.fromString(r.getString("author_user_id")),
                    r.getString("comment_text"), Dev06Jdbc.instant(r.getObject("created_at", LocalDateTime.class), r.getObject("submicro_nanos", Integer.class))),
                    r.getLong("total_elements")), assignmentId.toString(), assignmentId.toString(), size, (long) page * size);
            if (rows.isEmpty()) throw unavailable();
            long total = rows.getFirst().total(); var values = new ArrayList<CommentDto>();
            for (var row : rows) {
                if (row.total() != total) throw unavailable();
                if (row.comment() != null) {
                    if (!assignmentId.equals(row.comment().assignmentId())) throw unavailable(); values.add(row.comment());
                } else if (rows.size() != 1) throw unavailable();
            }
            long pages = total / size + (total % size == 0 ? 0 : 1);
            if (total < values.size() || pages > Integer.MAX_VALUE) throw unavailable();
            return new CommentPage(values, page, size, total, (int) pages);
        } catch (DataAccessException | IllegalArgumentException | NullPointerException invalidStorage) { throw unavailable(); }
    }
}
