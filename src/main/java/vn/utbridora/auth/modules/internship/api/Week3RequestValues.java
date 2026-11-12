package vn.utbridora.auth.modules.internship.api;

import java.util.*;
import org.springframework.util.MultiValueMap;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** DEV06 HTTP validation only; does not grant any permission or install runtime providers. */
final class Week3RequestValues {
    static final String HTTP_ENABLED = "app.t3.week3.http.enabled";
    private Week3RequestValues() {}
    static UUID identifier(String value) { return UUID.fromString(uuid(value)); }
    static UUID key(String value) {
        try { return identifier(value); }
        catch (vn.utbridora.auth.operations.OperationFailure invalid) { throw failure(400, "INVALID_REQUEST"); }
    }
    static Map<String, String> unique(MultiValueMap<String, String> query) {
        var result = new LinkedHashMap<String, String>();
        query.forEach((name, values) -> {
            if (values == null || values.size() != 1 || values.getFirst() == null) throw failure(400, "SCHEMA_VALIDATION");
            result.put(name, values.getFirst());
        });
        return Map.copyOf(result);
    }
    static int integer(Map<String, String> query, String name, int fallback) {
        if (!query.containsKey(name)) return fallback;
        String value = query.get(name);
        try {
            if (!value.matches("[0-9]+")) throw new IllegalArgumentException();
            return Integer.parseInt(value);
        } catch (IllegalArgumentException invalid) { throw failure(400, "SCHEMA_VALIDATION"); }
    }
}
