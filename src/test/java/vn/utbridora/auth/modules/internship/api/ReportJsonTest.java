package vn.utbridora.auth.modules.internship.api;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.operations.OperationFailure;
import static org.junit.jupiter.api.Assertions.*;

class ReportJsonTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ID = "00000000-0000-4000-8000-000000000081";
    private Map<String, Object> create() {
        return new LinkedHashMap<>(Map.of("caseId", ID, "weekStart", "2026-11-02", "completedWork", "Work",
                "difficulties", "", "nextPlan", "Next", "evidenceFileIds", List.of()));
    }
    @Test void parsesEveryRequestShapeAndQueryDefaults() {
        assertEquals(UUID.fromString(ID), ReportJson.create(JSON.writeValueAsString(create())).caseId());
        var update = create(); update.remove("caseId"); update.remove("weekStart"); update.put("expectedVersion", 1);
        assertEquals("", ReportJson.update(JSON.writeValueAsString(update)).difficulties());
        assertEquals(1, ReportJson.submit("{\"expectedVersion\":1}").expectedVersion());
        assertEquals("Good", ReportJson.review("{\"expectedVersion\":2,\"comment\":\" Good \"}").comment());
        assertEquals("FIX_REPORT", ReportJson.revision("{\"expectedVersion\":3,\"reasonCode\":\"FIX_REPORT\"}").reasonCode());
        assertEquals(25, ReportJson.query(Map.of("caseId", ID)).size());
        assertNull(ReportJson.readQuery(Map.of()).revisionNo());
        assertEquals(2, ReportJson.readQuery(Map.of("revisionNo", "2")).revisionNo());
        assertEquals(100, ReportJson.query(Map.of("caseId", ID, "state", "REVIEWED", "page", "2", "size", "100")).size());
    }
    @ParameterizedTest @ValueSource(strings = {"caseId", "weekStart", "completedWork", "difficulties", "nextPlan", "evidenceFileIds"})
    void everyCreateFieldIsRequiredAndNonNull(String field) {
        var fields = create(); fields.remove(field); schema(() -> ReportJson.create(JSON.writeValueAsString(fields)));
        fields.put(field, null); schema(() -> ReportJson.create(JSON.writeValueAsString(fields)));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "null", "[]", "true", "1", "\"text\"", "{", "{}", "{} {}"})
    void everyCommandRejectsNonObjectsAndInvalidJson(String body) {
        schema(() -> ReportJson.create(body)); schema(() -> ReportJson.update(body)); schema(() -> ReportJson.submit(body));
        schema(() -> ReportJson.review(body)); schema(() -> ReportJson.revision(body));
    }
    @Test void rejectsDuplicateKeysTrailingJsonAndUnknownClientAuthority() {
        schema(() -> ReportJson.submit("{\"expectedVersion\":1,\"expectedVersion\":2}"));
        schema(() -> ReportJson.submit("{\"expectedVersion\":1} true"));
        schema(() -> ReportJson.create(" ".repeat(2_000_001)));
        for (String key : List.of("actor", "reviewerUserId", "state", "dueAt", "lateFlag", "unknown")) {
            var fields = create(); fields.put(key, null);
            schema(() -> ReportJson.create(JSON.writeValueAsString(fields)));
        }
    }
    @ParameterizedTest @ValueSource(strings = {"null", "true", "\"1\"", "1.0", "1e0", "-1", "9007199254740992", "9223372036854775808", "[]", "{}"})
    void versionCannotBeCoercedRoundedOrOutsideV(String value) {
        schema(() -> ReportJson.submit("{\"expectedVersion\":" + value + "}"));
    }
    @Test void exactIntegerExtremesAndNonEmptyFileArrayParseWithoutGrantingBinding() {
        assertEquals(0, ReportJson.submit("{\"expectedVersion\":0}").expectedVersion());
        assertEquals(9_007_199_254_740_991L, ReportJson.submit("{\"expectedVersion\":9007199254740991}").expectedVersion());
        var fields = create(); fields.put("evidenceFileIds", List.of(ID));
        assertEquals(List.of(UUID.fromString(ID)), ReportJson.create(JSON.writeValueAsString(fields)).evidenceFileIds());
    }
    @Test void validatesDatesUuidsTextAndFileItemTypesStrictly() {
        for (Object value : List.of("2026-02-30", "2026-1-02", "2026-11-02T00:00:00Z", 1, true)) {
            var fields = create(); fields.put("weekStart", value); schema(() -> ReportJson.create(JSON.writeValueAsString(fields)));
        }
        for (Object value : List.of("1-1-1-1-1", " " + ID, 1, true)) {
            var fields = create(); fields.put("caseId", value); schema(() -> ReportJson.create(JSON.writeValueAsString(fields)));
        }
        for (Object value : List.of(1, true, List.of(), Map.of(), " ")) {
            var fields = create(); fields.put("completedWork", value); schema(() -> ReportJson.create(JSON.writeValueAsString(fields)));
        }
        for (Object value : List.of(ID, List.of(1), List.of(true), Arrays.asList((Object) null), List.of("1-1-1-1-1"))) {
            var fields = create(); fields.put("evidenceFileIds", value); schema(() -> ReportJson.create(JSON.writeValueAsString(fields)));
        }
    }
    @Test void optionalQueryFieldsRejectExplicitNullUnknownNamesAndCoercion() {
        schema(() -> ReportJson.query(Map.of()));
        for (String value : List.of("reviewed", " REVIEWED", "0")) schema(() -> ReportJson.query(Map.of("caseId", ID, "state", value)));
        for (String value : List.of("0", "-1", "1.0", "1e0", "2147483648")) schema(() -> ReportJson.readQuery(Map.of("revisionNo", value)));
        schema(() -> ReportJson.query(Map.of("caseId", ID, "size", "101")));
        schema(() -> ReportJson.query(Map.of("caseId", ID, "page", "10001")));
        schema(() -> ReportJson.readQuery(Map.of("actor", ID)));
        var fields = new HashMap<String, String>(); fields.put("caseId", ID); fields.put("state", null);
        schema(() -> ReportJson.query(fields));
    }
    private void schema(org.junit.jupiter.api.function.Executable action) {
        var failure = assertThrows(OperationFailure.class, action);
        assertEquals(400, failure.status); assertEquals("SCHEMA_VALIDATION", failure.code);
        assertFalse(failure.getMessage().contains(ID));
    }
}
