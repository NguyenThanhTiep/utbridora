package vn.utbridora.auth.modules.internship.infrastructure;

import java.sql.ResultSet;
import java.time.*;
import java.util.*;
import java.util.function.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.*;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.utbridora.auth.modules.internship.domain.WeeklyReport;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises actual SQL adapter row mappers/bindings against a recording JDBC boundary.
 * SQL is NOT interpreted, no H2/MySQL/schema/constraint/locking evidence is fabricated. */
abstract class Dev06JdbcTestSupport extends WeeklyReportAdapterTestSupport {
    record Statement(String sql, List<Object> parameters) {}
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final Runnable schema = mock(Runnable.class);
    final List<Statement> reads = new ArrayList<>(), writes = new ArrayList<>();
    List<Map<String, Object>> roots = new ArrayList<>(), revisions = new ArrayList<>(), reviews = new ArrayList<>(), files = new ArrayList<>(), pageRows = new ArrayList<>();
    ToIntFunction<Statement> write = statement -> 1;
    BiFunction<String, Object[], List<Map<String, Object>>> query = (sql, parameters) -> {
        if (sql.contains("LEFT JOIN (")) return pageRows;
        if (sql.startsWith("SELECT id,case_id")) return roots;
        if (sql.contains("FROM internship_weekly_report_revisions")) return revisions;
        if (sql.contains("FROM internship_weekly_report_reviews")) return reviews;
        if (sql.contains("FROM internship_weekly_report_files")) return files;
        throw new AssertionError("Unexpected SQL: " + sql);
    };
    @SuppressWarnings({"unchecked", "rawtypes"})
    @BeforeEach void jdbcBoundary() {
        when(jdbc.update(anyString(), any(Object[].class))).thenAnswer(call -> {
            var statement = statement(call.getArgument(0), call.getArguments()); writes.add(statement); return write.applyAsInt(statement);
        });
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(call -> {
            String sql = call.getArgument(0); RowMapper mapper = call.getArgument(1);
            Object[] parameters = Arrays.copyOfRange(call.getArguments(), 2, call.getArguments().length);
            reads.add(new Statement(sql, Arrays.asList(parameters)));
            var rows = query.apply(sql, parameters); var result = new ArrayList<>(); int number = 0;
            for (var value : rows) result.add(mapper.mapRow(resultSet(value), number++));
            return result;
        });
    }
    private Statement statement(String sql, Object[] arguments) {
        return new Statement(sql, Arrays.asList(Arrays.copyOfRange(arguments, 1, arguments.length)));
    }
    private ResultSet resultSet(Map<String, Object> row) throws Exception {
        var result = mock(ResultSet.class);
        when(result.getString(anyString())).thenAnswer(call -> { var value = row.get(call.getArgument(0)); return value == null ? null : value.toString(); });
        when(result.getObject(anyString(), eq(LocalDate.class))).thenAnswer(call -> row.get(call.getArgument(0)));
        when(result.getObject(anyString(), eq(LocalDateTime.class))).thenAnswer(call -> row.get(call.getArgument(0)));
        when(result.getObject(anyString(), eq(Integer.class))).thenAnswer(call -> row.get(call.getArgument(0)));
        when(result.getInt(anyString())).thenAnswer(call -> { var value = (Number) row.get(call.getArgument(0)); return value == null ? 0 : value.intValue(); });
        when(result.getLong(anyString())).thenAnswer(call -> { var value = (Number) row.get(call.getArgument(0)); return value == null ? 0L : value.longValue(); });
        when(result.getBoolean(anyString())).thenAnswer(call -> Boolean.TRUE.equals(row.get(call.getArgument(0))));
        return result;
    }
    static Map<String, Object> row(Object... pairs) {
        var value = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) value.put((String) pairs[i], pairs[i + 1]);
        return value;
    }
    <T> T rc(Supplier<T> call) {
        var tx = new TransactionTemplate(manager); tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return tx.execute(status -> call.get());
    }
    WeeklyReport draft(UUID id) {
        return WeeklyReport.create(id, caseId, LocalDate.parse("2026-11-02"), scope.startDate(), scope.endDate(),
                new WeeklyReport.Content("Work", "", "Next", List.of()));
    }
    void stored(WeeklyReport value) {
        roots = new ArrayList<>(List.of(row("id", value.reportId(), "case_id", value.key().caseId(), "week_start", value.key().weekStart(),
                "due_at", Dev06Jdbc.micros(value.dueAt()), "current_revision_no", value.current().revisionNo(), "version", value.version())));
        revisions.clear(); reviews.clear(); files.clear();
        for (var revision : value.revisions()) {
            var content = revision.content();
            revisions.add(row("revision_no", revision.revisionNo(), "report_version", revision.version(), "state", revision.state().name(),
                    "completed_work", content.completedWork(), "difficulties", content.difficulties(), "next_plan", content.nextPlan(),
                    "reason_code", revision.reasonCode(), "submitted_at", Dev06Jdbc.micros(revision.submittedAt()),
                    "submitted_submicro_nanos", Dev06Jdbc.remainder(revision.submittedAt()), "late_flag", revision.lateFlag()));
            if (revision.review() != null) {
                var feedback = revision.review();
                reviews.add(row("revision_no", revision.revisionNo(), "reviewer_user_id", feedback.reviewerUserId(), "comment", feedback.comment(),
                        "reviewed_at", Dev06Jdbc.micros(feedback.createdAt()), "reviewed_submicro_nanos", Dev06Jdbc.remainder(feedback.createdAt())));
            }
            int ordinal = 0;
            for (var file : content.evidenceFileIds()) files.add(row("revision_no", revision.revisionNo(), "ordinal_no", ++ordinal, "file_id", file));
        }
    }
}
