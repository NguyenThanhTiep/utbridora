package vn.utbridora.auth.modules.internship.api;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.modules.internship.domain.WeeklyReport.State;
import static vn.utbridora.auth.modules.internship.api.ReportDtos.*;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** Request parser for CP-WS3 only. Unknown/missing/null fields, scalar coercion, duplicate keys and
 * trailing JSON are rejected without changing the shared Core or DEV05 parsers. No HTTP route. */
public final class ReportJson {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private ReportJson() {}

    public static ReportInput create(String body) {
        return parse(body, Set.of("caseId", "weekStart", "completedWork", "difficulties", "nextPlan", "evidenceFileIds"),
                f -> new ReportInput(uuid(f.get("caseId")), date(f.get("weekStart")), text(f.get("completedWork")),
                        text(f.get("difficulties")), text(f.get("nextPlan")), ids(f.get("evidenceFileIds"))));
    }
    public static UpdateReport update(String body) {
        return parse(body, Set.of("expectedVersion", "completedWork", "difficulties", "nextPlan", "evidenceFileIds"),
                f -> new UpdateReport(version(f.get("expectedVersion")), text(f.get("completedWork")),
                        text(f.get("difficulties")), text(f.get("nextPlan")), ids(f.get("evidenceFileIds"))));
    }
    public static SubmitReport submit(String body) {
        return parse(body, Set.of("expectedVersion"), f -> new SubmitReport(version(f.get("expectedVersion"))));
    }
    public static ReportReview review(String body) {
        return parse(body, Set.of("expectedVersion", "comment"),
                f -> new ReportReview(version(f.get("expectedVersion")), text(f.get("comment"))));
    }
    public static CreateReportRevision revision(String body) {
        return parse(body, Set.of("expectedVersion", "reasonCode"),
                f -> new CreateReportRevision(version(f.get("expectedVersion")), text(f.get("reasonCode"))));
    }

    /** Already-decoded query parameters; optional means omitted, not explicitly null. */
    public static ReportQuery query(Map<String, String> fields) {
        try {
            queryFields(fields, Set.of("caseId", "state", "page", "size"));
            return new ReportQuery(uuid(fields.get("caseId")),
                    fields.containsKey("state") ? State.valueOf(fields.get("state")) : null,
                    fields.containsKey("page") ? integer(fields.get("page")) : null,
                    fields.containsKey("size") ? integer(fields.get("size")) : null);
        } catch (IllegalArgumentException invalid) { throw failure(400, "SCHEMA_VALIDATION"); }
    }
    public static ReportReadQuery readQuery(Map<String, String> fields) {
        try {
            queryFields(fields, Set.of("revisionNo"));
            return new ReportReadQuery(fields.containsKey("revisionNo") ? integer(fields.get("revisionNo")) : null);
        } catch (IllegalArgumentException invalid) { throw failure(400, "SCHEMA_VALIDATION"); }
    }

    private static <T> T parse(String body, Set<String> keys, Function<Map<?, ?>, T> parse) {
        try {
            if (body == null || body.length() > 2_000_000) throw failure(400, "SCHEMA_VALIDATION");
            var tree = JSON.readTree(body);
            if (tree == null || !tree.isObject()) throw failure(400, "SCHEMA_VALIDATION");
            var fields = JSON.convertValue(tree, Map.class);
            if (!fields.keySet().equals(keys)) throw failure(400, "SCHEMA_VALIDATION");
            return parse.apply(fields);
        } catch (JacksonException | IllegalArgumentException invalid) { throw failure(400, "SCHEMA_VALIDATION"); }
    }
    private static String text(Object value) {
        if (!(value instanceof String string)) throw new IllegalArgumentException("String required");
        return string;
    }
    private static UUID uuid(Object value) {
        String string = text(value);
        if (!string.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException("UUID required");
        }
        return UUID.fromString(string);
    }
    private static LocalDate date(Object value) {
        String string = text(value);
        if (!string.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException("Date required");
        try { return LocalDate.parse(string); }
        catch (java.time.DateTimeException invalid) { throw new IllegalArgumentException("Invalid date"); }
    }
    private static List<UUID> ids(Object value) {
        if (!(value instanceof List<?> values)) throw new IllegalArgumentException("Array required");
        return values.stream().map(ReportJson::uuid).toList();
    }
    private static Long version(Object value) {
        BigInteger number;
        if (value instanceof BigInteger integer) number = integer;
        else if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            number = BigInteger.valueOf(((Number) value).longValue());
        } else throw new IllegalArgumentException("Integer version required");
        if (number.signum() < 0 || number.compareTo(BigInteger.valueOf(9_007_199_254_740_991L)) > 0) {
            throw new IllegalArgumentException("Version outside V range");
        }
        return number.longValueExact();
    }
    private static void queryFields(Map<String, String> fields, Set<String> allowed) {
        if (fields == null || !allowed.containsAll(fields.keySet()) || fields.values().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Invalid query fields");
        }
    }
    private static Integer integer(String value) {
        if (!value.matches("[0-9]+")) throw new IllegalArgumentException("Integer query value required");
        return Integer.valueOf(value);
    }
}
