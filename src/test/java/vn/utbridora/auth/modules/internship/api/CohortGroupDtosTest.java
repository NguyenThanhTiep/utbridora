package vn.utbridora.auth.modules.internship.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.utbridora.auth.modules.internship.api.CohortGroupDtos.*;
import static org.junit.jupiter.api.Assertions.*;

class CohortGroupDtosTest {
    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000021");

    @Test void cohortQueryDefaultsAndBoundariesFollowC0() {
        var defaults = new CohortQuery(null, null, null);
        assertNull(defaults.termId());
        assertEquals(0, defaults.page());
        assertEquals(25, defaults.size());
        assertEquals(ID, new CohortQuery(ID, 10_000, 100).termId());
        assertEquals(1, new CohortQuery(null, 0, 1).size());
        for (int page : new int[] {-1, 10_001}) {
            assertThrows(IllegalArgumentException.class, () -> new CohortQuery(ID, page, 25));
        }
        for (int size : new int[] {0, -1, 101}) {
            assertThrows(IllegalArgumentException.class, () -> new CohortQuery(ID, 0, size));
        }
    }
    // Isolated contract reader, not the production Spring MVC mapper.
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();

    private record Fixture(Class<?> type, String json) {}

    private static List<Fixture> fixtures() {
        String cohortId = "00000000-0000-4000-8000-000000000061";
        String enterpriseId = "00000000-0000-4000-8000-000000000012";
        String schoolId = "00000000-0000-4000-8000-000000000011";
        return List.of(
                new Fixture(CreateCohort.class, """
                    {"termId":"%s","code":"DEMO-2026-A","name":"Đợt thực tập Demo"}
                    """.formatted(ID)),
                new Fixture(CohortDto.class, """
                    {"cohortId":"%s","enterpriseOrgId":"%s","schoolOrgId":"%s",
                     "termId":"%s","code":"DEMO-2026-A","name":"Đợt thực tập Demo","version":1}
                    """.formatted(cohortId, enterpriseId, schoolId, ID)),
                new Fixture(GroupDto.class, """
                    {"groupId":"%s","cohortId":"%s","code":"G1",
                     "caseIds":[],"mentorUserIds":[],"version":0}
                    """.formatted(enterpriseId, cohortId)),
                new Fixture(AssignGroupMember.class, """
                    {"caseId":"%s","expectedCaseVersion":0,"expectedGroupVersion":1}
                    """.formatted(ID)),
                new Fixture(MoveCase.class, """
                    {"caseId":"%s","toGroupId":"%s","expectedCaseVersion":1,
                     "expectedFromGroupVersion":2,"expectedToGroupVersion":3,"reasonCode":"MOVE_CASE"}
                    """.formatted(ID, enterpriseId)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fields(Fixture fixture) {
        return JSON.readValue(fixture.json(), Map.class);
    }

    @Test void jsonRoundTripPreservesEveryFieldAndPublishedCohortIds() {
        for (Fixture fixture : fixtures()) {
            Object value = JSON.readValue(fixture.json(), fixture.type());
            assertEquals(fields(fixture), JSON.readValue(JSON.writeValueAsString(value), Map.class));
            assertEquals(value, JSON.readValue(JSON.writeValueAsString(value), fixture.type()));
        }
        var cohort = JSON.readValue(fixtures().get(1).json(), CohortDto.class);
        assertNotEquals(cohort.enterpriseOrgId(), cohort.schoolOrgId());
        assertEquals(ID, cohort.termId());
    }

    @Test void jsonRejectsEveryMissingOrNullRequiredField() {
        for (Fixture fixture : fixtures()) {
            for (String field : fields(fixture).keySet()) {
                var missing = new LinkedHashMap<>(fields(fixture));
                missing.remove(field);
                assertThrows(JacksonException.class,
                        () -> JSON.readValue(JSON.writeValueAsString(missing), fixture.type()),
                        fixture.type().getSimpleName() + " missing " + field);
                var nullable = new LinkedHashMap<>(fields(fixture));
                nullable.put(field, null);
                assertThrows(JacksonException.class,
                        () -> JSON.readValue(JSON.writeValueAsString(nullable), fixture.type()),
                        fixture.type().getSimpleName() + " null " + field);
            }
        }
    }

    @Test void strictContractReaderRejectsUnknownFields() {
        for (Fixture fixture : fixtures()) {
            var body = new LinkedHashMap<>(fields(fixture));
            body.put("actorId", ID.toString());
            assertThrows(JacksonException.class,
                    () -> JSON.readValue(JSON.writeValueAsString(body), fixture.type()));
        }
    }

    @Test void jsonRejectsMalformedIdsAndInvalidListItems() {
        for (Fixture fixture : fixtures()) {
            for (String field : fields(fixture).keySet()) {
                if (!field.endsWith("Id") && !field.endsWith("Ids")) continue;
                for (String invalid : List.of("", "not-a-uuid", "1-1-1-1-1")) {
                    var body = new LinkedHashMap<>(fields(fixture));
                    body.put(field, field.endsWith("Ids") ? List.of(invalid) : invalid);
                    assertThrows(JacksonException.class,
                            () -> JSON.readValue(JSON.writeValueAsString(body), fixture.type()));
                }
                if (field.endsWith("Ids")) {
                    var body = new LinkedHashMap<>(fields(fixture));
                    body.put(field, Arrays.asList((Object) null));
                    assertThrows(JacksonException.class,
                            () -> JSON.readValue(JSON.writeValueAsString(body), fixture.type()));
                }
            }
        }
    }

    @Test void jsonVersionBoundariesAndWrongWireTypes() {
        for (Fixture fixture : fixtures()) {
            for (String field : fields(fixture).keySet()) {
                if (!field.toLowerCase(java.util.Locale.ROOT).endsWith("version")) continue;
                for (Object invalid : List.of(-1L, 9_007_199_254_740_992L, 0.5, true, "1")) {
                    var body = new LinkedHashMap<>(fields(fixture));
                    body.put(field, invalid);
                    assertThrows(JacksonException.class,
                            () -> JSON.readValue(JSON.writeValueAsString(body), fixture.type()));
                }
                for (long boundary : new long[] {0L, 9_007_199_254_740_991L}) {
                    var body = new LinkedHashMap<>(fields(fixture));
                    body.put(field, boundary);
                    assertNotNull(JSON.readValue(JSON.writeValueAsString(body), fixture.type()));
                }
            }
        }
    }

    @Test void jsonUnicodeLimitsNormalizeEdgesButPreserveInternalWhitespace() {
        var body = new LinkedHashMap<>(fields(fixtures().getFirst()));
        body.put("code", "\u00a0" + "\uD83D\uDE80".repeat(64) + "\u2003");
        body.put("name", "\u2003A  B\u00a0");
        var input = JSON.readValue(JSON.writeValueAsString(body), CreateCohort.class);
        assertEquals(64, input.code().codePointCount(0, input.code().length()));
        assertEquals("A  B", input.name());
        body.put("name", "\uD83D\uDE80".repeat(200));
        assertNotNull(JSON.readValue(JSON.writeValueAsString(body), CreateCohort.class));
        body.put("name", "\uD83D\uDE80".repeat(201));
        assertThrows(JacksonException.class,
                () -> JSON.readValue(JSON.writeValueAsString(body), CreateCohort.class));
    }

    @Test void jsonRejectsDuplicateCaseMembership() {
        var body = new LinkedHashMap<>(fields(fixtures().get(2)));
        body.put("caseIds", List.of(ID.toString(), ID.toString()));
        assertThrows(JacksonException.class,
                () -> JSON.readValue(JSON.writeValueAsString(body), GroupDto.class));
    }

    @Test void publishedCohortExampleAndUnicodeNormalization() {
        var input = new CreateCohort(ID, " DEMO-2026-A ", "\u00a0Đợt thực tập Demo\u2003");
        assertEquals("DEMO-2026-A", input.code());
        assertEquals("Đợt thực tập Demo", input.name());
        var response = new CohortDto(ID, ID, ID, input.termId(), input.code(), input.name(), 1L);
        assertEquals(input.name(), response.name());
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"", " ", "\u00a0\u2003"})
    void rejectsMissingOrBlankText(String value) {
        assertThrows(IllegalArgumentException.class, () -> new CreateCohort(ID, value, "Name"));
        assertThrows(IllegalArgumentException.class, () -> new CreateCohort(ID, "Code", value));
    }

    @Test void textLimitsUseCodepointsAfterTrim() {
        String astral = "\uD83D\uDE80";
        assertEquals(astral.repeat(64), new CreateCohort(ID, astral.repeat(64), "Name").code());
        assertEquals(200, new CreateCohort(ID, "Code", "a".repeat(200)).name().length());
        assertThrows(IllegalArgumentException.class,
                () -> new CreateCohort(ID, astral.repeat(65), "Name"));
        assertThrows(IllegalArgumentException.class,
                () -> new CreateCohort(ID, "Code", "a".repeat(201)));
    }

    @ParameterizedTest @NullSource @ValueSource(longs = {-1L, 9_007_199_254_740_992L})
    void everyVersionFieldRejectsMissingAndOutOfRange(Long value) {
        assertThrows(IllegalArgumentException.class, () -> new CohortDto(ID, ID, ID, ID, "C", "N", value));
        assertThrows(IllegalArgumentException.class, () -> new GroupDto(ID, ID, "G", List.of(), List.of(), value));
        assertThrows(IllegalArgumentException.class, () -> new AssignGroupMember(ID, value, 0L));
        assertThrows(IllegalArgumentException.class, () -> new AssignGroupMember(ID, 0L, value));
        assertThrows(IllegalArgumentException.class, () -> new MoveCase(ID, ID, value, 0L, 0L, "MOVE"));
        assertThrows(IllegalArgumentException.class, () -> new MoveCase(ID, ID, 0L, value, 0L, "MOVE"));
        assertThrows(IllegalArgumentException.class, () -> new MoveCase(ID, ID, 0L, 0L, value, "MOVE"));
    }

    @Test void acceptsBothVersionBoundaries() {
        for (long version : new long[] {0, 9_007_199_254_740_991L}) {
            assertEquals(version, new CohortDto(ID, ID, ID, ID, "C", "N", version).version());
            assertEquals(version, new GroupDto(ID, ID, "G", List.of(), List.of(), version).version());
            assertEquals(version, new AssignGroupMember(ID, version, version).expectedGroupVersion());
            assertEquals(version, new MoveCase(ID, ID, version, version, version, "MOVE").expectedCaseVersion());
        }
    }

    @Test void listsAreRequiredAllowEmptyAndDefensivelyCopied() {
        var cases = new ArrayList<>(List.of(ID));
        var mentors = new ArrayList<>(List.of(ID, ID));
        var group = new GroupDto(ID, ID, "G", cases, mentors, 0L);
        cases.clear(); mentors.clear();
        assertEquals(List.of(ID), group.caseIds());
        assertEquals(List.of(ID, ID), group.mentorUserIds());
        assertThrows(UnsupportedOperationException.class, () -> group.caseIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> group.mentorUserIds().clear());
        assertTrue(new GroupDto(ID, ID, "G", List.of(), List.of(), 0L).caseIds().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new GroupDto(ID, ID, "G", null, List.of(), 0L));
        assertThrows(IllegalArgumentException.class, () -> new GroupDto(ID, ID, "G", List.of(), null, 0L));
        assertThrows(IllegalArgumentException.class, () -> new GroupDto(ID, ID, "G", List.of(ID, ID), List.of(), 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new GroupDto(ID, ID, "G", Arrays.asList((UUID) null), List.of(), 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new GroupDto(ID, ID, "G", List.of(), Arrays.asList((UUID) null), 0L));
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"ab", "move", "HAS SPACE", " MOVE", "MOVE!"})
    void reasonMustMatchExactContractPattern(String reason) {
        assertThrows(IllegalArgumentException.class, () -> new MoveCase(ID, ID, 0L, 0L, 0L, reason));
    }

    @Test void reasonBoundariesAndRequiredIds() {
        assertEquals("A_1", new MoveCase(ID, ID, 0L, 0L, 0L, "A_1").reasonCode());
        assertEquals(64, new MoveCase(ID, ID, 0L, 0L, 0L, "A".repeat(64)).reasonCode().length());
        assertThrows(IllegalArgumentException.class, () -> new MoveCase(ID, ID, 0L, 0L, 0L, "A".repeat(65)));
        assertThrows(IllegalArgumentException.class, () -> new CreateCohort(null, "C", "N"));
        assertThrows(IllegalArgumentException.class, () -> new AssignGroupMember(null, 0L, 0L));
        assertThrows(IllegalArgumentException.class, () -> new MoveCase(ID, null, 0L, 0L, 0L, "MOVE"));
        assertThrows(IllegalArgumentException.class, () -> new CohortDto(ID, ID, null, ID, "C", "N", 0L));
    }
}
