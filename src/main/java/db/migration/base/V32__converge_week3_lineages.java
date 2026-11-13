package db.migration.base;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Forward-only convergence; original V1-V31 scripts and installed checksums stay intact. */
public class V32__converge_week3_lineages extends BaseJavaMigration {
    private static final List<String> SOURCES = List.of(
        "db/migration/main/V28__private_file_service.sql",
        "db/migration/main/V29__t3_submission_review_comment.sql",
        "db/migration/main/V31__t3_weekly_reports.sql",
        "db/legacy/t2/V28__student_preferences.sql",
        "db/legacy/t5/V28__placement_school_permission_and_active_key.sql",
        "db/legacy/t5/V29__placement_history.sql",
        "db/legacy/t5/V30__proposal_decisions_and_opportunity_publication.sql",
        "db/legacy/t4/V28__matching_protected_releases.sql",
        "db/legacy/t4/V29__matching_audit_statistics.sql");

    @Override public Integer getChecksum() {
        CRC32 crc = new CRC32();
        for (String path : SOURCES) crc.update(sql(path).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
        return (int) crc.getValue();
    }

    @Override public void migrate(Context context) throws Exception {
        Connection c = context.getConnection();
        // Each family is either installed by its immutable historical migration, or installed here.
        // A partial/unknown schema is an error; never repair history or ignore a failed DDL statement.
        apply(c, 0, !exists(c, "STATISTICS", "TABLE_NAME='file_bindings' AND INDEX_NAME='ix_file_binding_resource'"));
        apply(c, 1, !table(c, "internship_submissions"));
        apply(c, 2, !table(c, "internship_weekly_reports"));
        apply(c, 3, !table(c, "student_preferences"));
        apply(c, 4, !column(c, "placements", "active_student_term_key"));
        apply(c, 5, !table(c, "placement_history"));
        apply(c, 6, !column(c, "cooperations", "published_at"));
        apply(c, 7, !table(c, "matching_privacy_release_settings"));
        apply(c, 8, !column(c, "audit_logs", "demand_id"));
        for (String name : List.of("internship_submission_reviews", "internship_submission_files",
                "internship_task_comments", "internship_weekly_report_revisions", "internship_weekly_report_reviews",
                "internship_weekly_report_files", "student_preference_history", "matching_demo_batches",
                "matching_active_demo_batches", "matching_privacy_releases")) {
            if (!table(c, name)) throw new SQLException("Incomplete week 3 schema: " + name);
        }
        if (!column(c, "cooperations", "closed_at") || !column(c, "audit_logs", "ip_address"))
            throw new SQLException("Incomplete week 3 columns; stop and restore the pre-migration backup");
    }

    private static void apply(Connection c, int source, boolean missing) throws SQLException {
        if (!missing) return;
        // Source scripts contain plain DDL/DML only, no stored routines or semicolons inside literals.
        String text = sql(SOURCES.get(source)).replaceAll("(?m)^\\s*--[^\\r\\n]*", "");
        for (String statement : text.split(";")) {
            if (!statement.isBlank()) try (var s = c.createStatement()) { s.execute(statement); }
        }
    }
    private static String sql(String path) {
        try (var input = V32__converge_week3_lineages.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing immutable migration source: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    private static boolean table(Connection c, String table) throws SQLException {
        return exists(c, "TABLES", "TABLE_NAME='" + table + "'");
    }
    private static boolean column(Connection c, String table, String column) throws SQLException {
        return exists(c, "COLUMNS", "TABLE_NAME='" + table + "' AND COLUMN_NAME='" + column + "'");
    }
    private static boolean exists(Connection c, String catalog, String predicate) throws SQLException {
        try (var s = c.createStatement(); var rows = s.executeQuery("SELECT COUNT(*) FROM information_schema."
                + catalog + " WHERE TABLE_SCHEMA=DATABASE() AND " + predicate)) {
            rows.next(); return rows.getInt(1) > 0;
        }
    }
}
