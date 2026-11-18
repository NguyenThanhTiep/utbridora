package vn.utbridora.auth.modules.internship.api;

import java.math.*;
import java.util.*;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.operations.OperationFailure;
import static vn.utbridora.auth.modules.internship.api.EvaluationDtos.*;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** Strict transport parsing local to CP-WS4. Reject coercion, unknown/duplicate/missing fields. */
public final class EvaluationJson {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,
                    DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private EvaluationJson() {}
    public static EvaluationInput create(String body) {
        var n = object(body, Set.of("caseId", "templateId", "templateVersion", "scores"));
        return valid(() -> new EvaluationInput(id(n.get("caseId")), id(n.get("templateId")), number(n.get("templateVersion")), scores(n.get("scores"))));
    }
    public static UpdateEvaluation update(String body) {
        var n = object(body, Set.of("expectedVersion", "scores"));
        return valid(() -> new UpdateEvaluation(number(n.get("expectedVersion")), scores(n.get("scores"))));
    }
    public static SubmitEvaluation submit(String body) {
        var n = object(body, Set.of("expectedVersion"));
        return valid(() -> new SubmitEvaluation(number(n.get("expectedVersion"))));
    }
    private static List<Score> scores(JsonNode n) {
        if (n == null || !n.isArray() || n.isEmpty() || n.size() > 100) throw invalid();
        var result = new ArrayList<Score>();
        for (var item : n) {
            fields(item, Set.of("criterionCode", "score", "comment", "evidenceFileIds"));
            var value = item.get("score");
            if (!value.isNumber()) throw invalid();
            var files = item.get("evidenceFileIds");
            if (!files.isArray() || files.size() > 20) throw invalid();
            var ids = new ArrayList<UUID>(); for (var file : files) ids.add(id(file));
            result.add(new Score(string(item.get("criterionCode")), value.decimalValue(),
                    item.get("comment").isNull() ? null : string(item.get("comment")), ids));
        }
        return result;
    }
    private static JsonNode object(String body, Set<String> keys) {
        return valid(() -> {
            if (body == null || body.length() > 2_000_000) throw invalid();
            var n = JSON.readTree(body); fields(n, keys); return n;
        });
    }
    private static void fields(JsonNode n, Set<String> keys) {
        if (n == null || !n.isObject() || !new HashSet<>(n.propertyNames()).equals(keys)) throw invalid();
    }
    private static UUID id(JsonNode n) { return UUID.fromString(uuid(string(n))); }
    private static String string(JsonNode n) { if (n == null || !n.isString()) throw invalid(); return n.asString(); }
    private static long number(JsonNode n) {
        if (n == null || !n.isIntegralNumber() || !n.canConvertToLong()) throw invalid();
        return n.longValue();
    }
    private static <T> T valid(java.util.function.Supplier<T> parse) {
        try { return parse.get(); }
        catch (OperationFailure failure) { throw failure; }
        catch (IllegalArgumentException | tools.jackson.core.JacksonException invalid) { throw invalid(); }
    }
    private static OperationFailure invalid() { return failure(400, "SCHEMA_VALIDATION"); }
}
