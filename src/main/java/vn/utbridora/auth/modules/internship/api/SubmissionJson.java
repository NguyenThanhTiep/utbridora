package vn.utbridora.auth.modules.internship.api;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import static vn.utbridora.auth.modules.internship.api.SubmissionDtos.*;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** Strict request-only parser for CP-WS2, without modifying the shared Core parser. */
public final class SubmissionJson {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private SubmissionJson() {}

    public static SubmissionInput submission(String body) {
        var fields = object(body, Set.of("expectedAssignmentVersion", "fileIds", "url", "note"));
        try {
            return new SubmissionInput(version(fields.get("expectedAssignmentVersion")),
                    fileIds(fields.get("fileIds")), nullableText(fields.get("url")), nullableText(fields.get("note")));
        } catch (IllegalArgumentException invalid) { throw failure(400, "SCHEMA_VALIDATION"); }
    }

    public static ReviewInput review(String body) {
        var fields = object(body, Set.of("expectedAssignmentVersion", "verdict", "comment"));
        try {
            return new ReviewInput(version(fields.get("expectedAssignmentVersion")),
                    ReviewVerdict.valueOf(text(fields.get("verdict"))), text(fields.get("comment")));
        } catch (IllegalArgumentException invalid) { throw failure(400, "SCHEMA_VALIDATION"); }
    }

    public static CommentInput comment(String body) {
        var fields = object(body, Set.of("text"));
        try { return new CommentInput(text(fields.get("text"))); }
        catch (IllegalArgumentException invalid) { throw failure(400, "SCHEMA_VALIDATION"); }
    }

    private static Map<?, ?> object(String body, Set<String> expected) {
        try {
            // Match the existing Core request-size safeguard; no payload is logged.
            if (body == null || body.length() > 2_000_000) throw failure(400, "SCHEMA_VALIDATION");
            var tree = JSON.readTree(body);
            if (tree == null || !tree.isObject()) throw failure(400, "SCHEMA_VALIDATION");
            var fields = JSON.convertValue(tree, Map.class);
            if (!fields.keySet().equals(expected)) throw failure(400, "SCHEMA_VALIDATION");
            return fields;
        } catch (JacksonException invalid) { throw failure(400, "SCHEMA_VALIDATION"); }
    }

    private static Long version(Object value) {
        BigInteger number;
        if (value instanceof BigInteger integer) number = integer;
        else if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            number = BigInteger.valueOf(((Number) value).longValue());
        } else throw new IllegalArgumentException("version must be an integer");
        if (number.signum() < 0 || number.compareTo(BigInteger.valueOf(9_007_199_254_740_991L)) > 0) {
            throw new IllegalArgumentException("version is outside contract V range");
        }
        return number.longValueExact();
    }

    private static List<UUID> fileIds(Object value) {
        if (!(value instanceof List<?> values)) throw new IllegalArgumentException("fileIds must be an array");
        var ids = new ArrayList<UUID>();
        for (Object item : values) {
            String id = text(item);
            if (!id.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) {
                throw new IllegalArgumentException("fileIds must contain UUID strings");
            }
            ids.add(UUID.fromString(id));
        }
        return ids;
    }

    private static String nullableText(Object value) { return value == null ? null : text(value); }

    private static String text(Object value) {
        if (!(value instanceof String string)) throw new IllegalArgumentException("string required");
        return string;
    }
}
