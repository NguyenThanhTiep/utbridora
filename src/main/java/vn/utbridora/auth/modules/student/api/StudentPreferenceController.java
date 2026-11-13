package vn.utbridora.auth.modules.student.api;

import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page;
import vn.utbridora.auth.modules.student.application.StudentPreferenceService;
import vn.utbridora.auth.modules.student.application.StudentPreferenceService.PreferenceView;
import vn.utbridora.auth.modules.student.application.StudentPreferenceService.ReviewPreference;
import vn.utbridora.auth.modules.student.application.StudentPreferenceService.SubmitPreference;
import vn.utbridora.auth.modules.student.application.StudentPreferenceService.WithdrawPreference;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@RestController
@RequestMapping("/api/v1")
public class StudentPreferenceController {
    private static final Set<String> SUBMIT_FIELDS = Set.of("action", "opportunityId", "message", "expectedIntentVersion");
    private static final Set<String> WITHDRAW_FIELDS = Set.of("action", "preferenceId", "expectedVersion", "reasonCode");
    private final StudentPreferenceService preferences;

    public StudentPreferenceController(StudentPreferenceService preferences) {
        this.preferences = preferences;
    }

    @GetMapping("/students/me/preferences")
    public Page<PreferenceView> mine(Authentication actor, @RequestParam(required = false) String termId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return preferences.mine(actor, termId, page, size);
    }

    @PostMapping("/students/me/preferences")
    public ResponseEntity<PreferenceView> submitOrWithdraw(Authentication actor,
            @RequestHeader("Idempotency-Key") String key, @RequestBody String data) {
        JsonNode node = request(data);
        String action = node.path("action").asText();
        if ("SUBMIT".equals(action)) {
            exactFields(node, SUBMIT_FIELDS);
            JsonNode message = node.get("message");
            if (message == null || !message.isNull() && !message.isTextual()) throw failure(400, "SCHEMA_VALIDATION");
            PreferenceView saved = preferences.submit(actor, key, read(data, SubmitPreference.class));
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        }
        if ("WITHDRAW".equals(action)) {
            exactFields(node, WITHDRAW_FIELDS);
            PreferenceView saved = preferences.withdraw(actor, key, read(data, WithdrawPreference.class));
            return ResponseEntity.ok(saved);
        }
        throw failure(400, "SCHEMA_VALIDATION");
    }

    @PostMapping("/school/preferences/{id}/review")
    public PreferenceView review(Authentication actor, @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key, @RequestBody String data) {
        return preferences.review(actor, key, id, body(data, ReviewPreference.class));
    }

    private static JsonNode request(String data) {
        try {
            if (data == null || data.length() > 2_000_000) throw failure(400, "SCHEMA_VALIDATION");
            JsonNode node = JSON.readTree(data);
            if (node == null || !node.isObject()) throw failure(400, "SCHEMA_VALIDATION");
            return node;
        } catch (tools.jackson.core.JacksonException invalid) {
            throw failure(400, "SCHEMA_VALIDATION");
        }
    }

    private static void exactFields(JsonNode node, Set<String> expected) {
        java.util.Set<String> actual = new java.util.HashSet<>();
        node.propertyNames().forEach(actual::add);
        if (!actual.equals(expected)) throw failure(400, "SCHEMA_VALIDATION");
    }

    private static <T> T read(String data, Class<T> type) {
        try {
            return JSON.readValue(data, type);
        } catch (tools.jackson.core.JacksonException invalid) {
            throw failure(400, "SCHEMA_VALIDATION");
        }
    }
}
