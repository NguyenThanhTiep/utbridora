package vn.utbridora.auth.modules.internship.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.operations.OperationFailure;
import static org.junit.jupiter.api.Assertions.*;
import static vn.utbridora.auth.modules.internship.api.SubmissionDtos.ReviewVerdict;

class SubmissionJsonTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ID = "00000000-0000-4000-8000-000000000041";

    private static Map<String, Object> submission() {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("expectedAssignmentVersion", 2L);
        fields.put("fileIds", List.of());
        fields.put("url", "https://example.test/submission-v1");
        fields.put("note", null);
        return fields;
    }
    private static Map<String, Object> review() {
        return new LinkedHashMap<>(Map.of("expectedAssignmentVersion", 3L, "verdict", "REQUEST_REVISION", "comment", "Revise"));
    }

    @Test void parsesTheUrlAndFileFixturesWithExplicitNullableFields() {
        var url = SubmissionJson.submission(JSON.writeValueAsString(submission()));
        assertEquals(2L, url.expectedAssignmentVersion());
        assertEquals(List.of(), url.fileIds());
        assertEquals("https://example.test/submission-v1", url.url());
        assertNull(url.note());
        var fields = submission();
        fields.put("fileIds", List.of(ID, "00000000-0000-4000-8000-000000000042"));
        fields.put("url", null);
        fields.put("note", "\u00a0 Note \u3000");
        var files = SubmissionJson.submission(JSON.writeValueAsString(fields));
        assertEquals(List.of(UUID.fromString(ID), new UUID(0x4000, 0x8000000000000042L)), files.fileIds());
        assertNull(files.url());
        assertEquals("Note", files.note());
        assertEquals(ReviewVerdict.REQUEST_REVISION, SubmissionJson.review(JSON.writeValueAsString(review())).verdict());
        assertEquals("Comment", SubmissionJson.comment("{\"text\":\" Comment \"}").text());
    }

    static Stream<Arguments> invalidSubmissionFields() {
        return Stream.of(
                Arguments.of("expectedAssignmentVersion", null), Arguments.of("expectedAssignmentVersion", "2"),
                Arguments.of("expectedAssignmentVersion", 2.0), Arguments.of("expectedAssignmentVersion", true),
                Arguments.of("expectedAssignmentVersion", List.of(2)), Arguments.of("expectedAssignmentVersion", -1L),
                Arguments.of("expectedAssignmentVersion", 9_007_199_254_740_992L),
                Arguments.of("fileIds", null), Arguments.of("fileIds", ID), Arguments.of("fileIds", Map.of()),
                Arguments.of("fileIds", List.of(41)), Arguments.of("fileIds", List.of(true)),
                Arguments.of("fileIds", java.util.Arrays.asList((Object) null)),
                Arguments.of("fileIds", List.of("1-1-1-1-1")), Arguments.of("fileIds", List.of(" " + ID)),
                Arguments.of("fileIds", List.of("z".repeat(36))),
                Arguments.of("url", 1), Arguments.of("url", List.of("https://example.test")),
                Arguments.of("url", true), Arguments.of("url", Map.of()),
                Arguments.of("note", 1), Arguments.of("note", false), Arguments.of("note", List.of()),
                Arguments.of("note", Map.of()), Arguments.of("note", "A".repeat(2_001)));
    }

    @ParameterizedTest @MethodSource("invalidSubmissionFields")
    void rejectsWrongTypesMissingValuesAndInvalidRangesWithoutCoercion(String field, Object invalid) {
        var fields = submission();fields.put(field, invalid);
        schema(() -> SubmissionJson.submission(JSON.writeValueAsString(fields)));
    }

    @ParameterizedTest @ValueSource(strings = {"expectedAssignmentVersion", "fileIds", "url", "note"})
    void nullableDoesNotMeanOmittable(String field) {
        var fields = submission();fields.remove(field);
        schema(() -> SubmissionJson.submission(JSON.writeValueAsString(fields)));
    }

    @ParameterizedTest @ValueSource(strings = {"expectedAssignmentVersion", "verdict", "comment"})
    void reviewRequiresAllItsFieldsAndForbidsNull(String field) {
        var fields = review();fields.remove(field);
        schema(() -> SubmissionJson.review(JSON.writeValueAsString(fields)));
        fields.put(field, null);
        schema(() -> SubmissionJson.review(JSON.writeValueAsString(fields)));
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {
        " ", "null", "[]", "true", "1", "\"text\"", "{", "{}", "{} {}",
        "{\"text\":\"a\",\"text\":\"b\"}", "{\"text\":\"a\"} true"
    })
    void everyParserRejectsNonObjectMalformedDuplicateOrTrailingInput(String body) {
        schema(() -> SubmissionJson.submission(body));
        schema(() -> SubmissionJson.review(body));
        schema(() -> SubmissionJson.comment(body));
    }

    @ParameterizedTest @ValueSource(strings = {"state", "studentUserId", "actor", "fileId", "unknown"})
    void closedObjectsRejectExtraFieldsAcrossRequests(String extra) {
        var fields = submission();fields.put(extra, null);
        schema(() -> SubmissionJson.submission(JSON.writeValueAsString(fields)));
        var reviewFields = review();reviewFields.put(extra, "value");
        String body = JSON.writeValueAsString(reviewFields);
        schema(() -> SubmissionJson.review(body));
        schema(() -> SubmissionJson.comment(JSON.writeValueAsString(Map.of("text", "Comment", extra, "value"))));
    }

    @ParameterizedTest @ValueSource(strings = {"accept", " ACCEPT", "REQUEST_REVISION ", "UNKNOWN", "0", "1", "false", "[]"})
    void verdictIsAnExactWireStringNotAnOrdinalOrCoercedScalar(String value) {
        var fields = review();fields.put("verdict", value);
        schema(() -> SubmissionJson.review(JSON.writeValueAsString(fields)));
        schema(() -> SubmissionJson.review("{\"expectedAssignmentVersion\":3,\"verdict\":0,\"comment\":\"Comment\"}"));
    }

    @Test void exactVersionWireBoundariesAreNeverRounded() {
        for (long version : new long[]{0, 1, 9_007_199_254_740_991L}) {
            var fields = submission();fields.put("expectedAssignmentVersion", version);
            assertEquals(version, SubmissionJson.submission(JSON.writeValueAsString(fields)).expectedAssignmentVersion());
        }
        for (String number : List.of("2.0", "2e0", "9007199254740991.0", "9223372036854775808", "9007199254740992", "-1")) {
            String body = JSON.writeValueAsString(submission()).replace("\"expectedAssignmentVersion\":2", "\"expectedAssignmentVersion\":" + number);
            schema(() -> SubmissionJson.submission(body));
        }
    }

    @Test void artifactBusinessErrorsRetainTheirContractCode() {
        var fields = submission();fields.put("url", null);
        artifact(JSON.writeValueAsString(fields));
        fields.put("url", "https://example.test");fields.put("fileIds", List.of(ID));
        artifact(JSON.writeValueAsString(fields));
        fields.put("url", null);fields.put("fileIds", List.of(ID, ID.toUpperCase(java.util.Locale.ROOT)));
        artifact(JSON.writeValueAsString(fields));
        var ids = new ArrayList<String>();
        for (int i = 0; i < 21; i++) ids.add(new UUID(0, i).toString());
        fields.put("fileIds", ids);artifact(JSON.writeValueAsString(fields));
    }

    @ParameterizedTest @ValueSource(strings = {"http://example.test", "https://user:private@example.test", "", "https:example.test"})
    void malformedHttpsUrlIsASafeSchemaError(String url) {
        var fields = submission();fields.put("url", url);
        var error = assertThrows(OperationFailure.class, () -> SubmissionJson.submission(JSON.writeValueAsString(fields)));
        assertEquals(400, error.status);
        assertEquals("SCHEMA_VALIDATION", error.code);
        assertFalse(error.getMessage().contains("private"));
        assertFalse(error.getMessage().contains("example.test"));
    }

    @Test void commentAndReviewTextCannotBeMissingNullNumericOrEmpty() {
        for (String body : List.of("{}", "{\"text\":null}", "{\"text\":1}", "{\"text\":false}", "{\"text\":[]}", "{\"text\":\"\"}")) {
            schema(() -> SubmissionJson.comment(body));
        }
        var fields = review();fields.put("comment", " ");
        schema(() -> SubmissionJson.review(JSON.writeValueAsString(fields)));
        fields.put("comment", 2);schema(() -> SubmissionJson.review(JSON.writeValueAsString(fields)));
        schema(() -> SubmissionJson.comment(JSON.writeValueAsString(Map.of("text", "X".repeat(2_001)))));
    }

    @Test void duplicateKeysAreRejectedEvenIfTheRestOfSubmissionIsValid() {
        String valid = JSON.writeValueAsString(submission());
        schema(() -> SubmissionJson.submission(valid.replace("\"note\":null", "\"note\":null,\"note\":\"replacement\"")));
        schema(() -> SubmissionJson.submission(valid + " {}"));
        schema(() -> SubmissionJson.submission(" ".repeat(2_000_001)));
    }

    private static void artifact(String body) {
        var error = assertThrows(OperationFailure.class, () -> SubmissionJson.submission(body));
        assertEquals(422, error.status);
        assertEquals("SUBMISSION_ARTIFACT_INVALID", error.code);
    }
    private static void schema(org.junit.jupiter.api.function.Executable call) {
        var error = assertThrows(OperationFailure.class, call);
        assertEquals(400, error.status);
        assertEquals("SCHEMA_VALIDATION", error.code);
    }
}
