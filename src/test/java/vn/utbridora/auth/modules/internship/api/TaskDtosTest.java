package vn.utbridora.auth.modules.internship.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.cfg.EnumFeature;
import static org.junit.jupiter.api.Assertions.*;
import static vn.utbridora.auth.modules.internship.api.TaskDtos.*;

class TaskDtosTest {
    private static final UUID GROUP = UUID.fromString("00000000-0000-4000-8000-000000000061");
    private static final UUID CASE = UUID.fromString("00000000-0000-4000-8000-000000000031");
    private static final Instant DUE = Instant.parse("2026-11-09T10:00:00Z");
    // Contract test mapper only, not the Spring MVC configuration.
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();

    private static CreateTask create(List<UUID> cases, Boolean lateAllowed) {
        return new CreateTask(GROUP, " Task ", " Description ", DUE, TaskPriority.NORMAL, cases, lateAllowed);
    }

    private static AssignmentDto assignment() {
        return new AssignmentDto(GROUP, CASE, GROUP, AssignmentState.NEW, 0L, null);
    }

    private static TaskDto task(List<AssignmentDto> assignments) {
        return new TaskDto(CASE, GROUP, "Task", "Description", DUE, TaskPriority.HIGH, false, 1L, assignments);
    }

    @Test void defaultLateAllowedAndTextNormalization() {
        assertTrue(create(List.of(CASE), null).lateAllowed());
        assertFalse(create(List.of(CASE), false).lateAllowed());
        assertEquals("Task", create(List.of(CASE), true).title());
        assertEquals("Description", create(List.of(CASE), true).description());
    }

    @Test void caseCountBoundariesDistinctnessAndImmutability() {
        List<UUID> thousand = IntStream.range(0, 1000).mapToObj(i -> new UUID(0, i)).toList();
        assertEquals(1000, create(thousand, null).caseIds().size());
        assertThrows(IllegalArgumentException.class, () -> create(List.of(), null));
        var tooMany = new ArrayList<>(thousand);
        tooMany.add(new UUID(0, 1000));
        assertThrows(IllegalArgumentException.class, () -> create(tooMany, null));
        assertThrows(IllegalArgumentException.class, () -> create(List.of(CASE, CASE), null));
        assertThrows(IllegalArgumentException.class, () -> create(null, null));
        assertThrows(IllegalArgumentException.class, () -> create(Arrays.asList((UUID) null), null));
        var mutable = new ArrayList<>(List.of(CASE));
        var value = create(mutable, null);
        mutable.clear();
        assertEquals(List.of(CASE), value.caseIds());
        assertThrows(UnsupportedOperationException.class, () -> value.caseIds().clear());
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"", " ", "\u00a0\u2003"})
    void requiredTaskTextRejectsBlank(String value) {
        assertThrows(IllegalArgumentException.class,
                () -> new CreateTask(GROUP, value, "D", DUE, TaskPriority.LOW, List.of(CASE), true));
        assertThrows(IllegalArgumentException.class,
                () -> new CreateTask(GROUP, "T", value, DUE, TaskPriority.LOW, List.of(CASE), true));
    }

    @Test void unicodeTextLimitsUseCodepoints() {
        String astral = "\uD83D\uDE80";
        var value = new CreateTask(GROUP, astral.repeat(200), astral.repeat(10_000),
                DUE, TaskPriority.LOW, List.of(CASE), null);
        assertEquals(400, value.title().length());
        assertThrows(IllegalArgumentException.class, () -> new CreateTask(GROUP, astral.repeat(201),
                "D", DUE, TaskPriority.LOW, List.of(CASE), null));
        assertThrows(IllegalArgumentException.class, () -> new CreateTask(GROUP, "T",
                astral.repeat(10_001), DUE, TaskPriority.LOW, List.of(CASE), null));
    }

    @ParameterizedTest @NullSource @ValueSource(longs = {-1L, 9_007_199_254_740_992L})
    void requiredVersionsRejectInvalidValues(Long version) {
        assertThrows(IllegalArgumentException.class,
                () -> new AssignmentDto(GROUP, CASE, GROUP, AssignmentState.NEW, version, null));
        assertThrows(IllegalArgumentException.class,
                () -> new TaskDto(CASE, GROUP, "T", "D", DUE, TaskPriority.HIGH, true, version, List.of()));
    }

    @Test void responsesKeepNullableSubmissionAndRequiredAssignmentList() {
        assertNull(assignment().currentSubmissionId());
        assertTrue(task(List.of()).assignments().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> task(null));
        assertThrows(IllegalArgumentException.class, () -> task(Arrays.asList((AssignmentDto) null)));
        var mutable = new ArrayList<>(List.of(assignment()));
        var response = task(mutable);
        mutable.clear();
        assertEquals(1, response.assignments().size());
        assertThrows(UnsupportedOperationException.class, () -> response.assignments().clear());
        for (long version : new long[] {0, 9_007_199_254_740_991L}) {
            assertEquals(version,
                    new AssignmentDto(GROUP, CASE, GROUP, AssignmentState.NEW, version, null).version());
        }
    }

    @Test void queriesHavePublishedFiltersAndPaginationWithoutInventedDateConstraint() {
        var query = new TaskQuery(GROUP, CASE, AssignmentState.IN_PROGRESS, DUE, DUE, 10_000, 100);
        assertEquals(GROUP, query.groupId());
        assertEquals(CASE, query.caseId());
        assertEquals(AssignmentState.IN_PROGRESS, query.state());
        var defaults = new TaskQuery(null, null, null, null, null, null, null);
        assertEquals(0, defaults.page());
        assertEquals(25, defaults.size());
        assertThrows(IllegalArgumentException.class,
                () -> new TaskQuery(null, null, null, null, null, -1, 25));
        assertThrows(IllegalArgumentException.class,
                () -> new TaskQuery(null, null, null, null, null, 10_001, 25));
        for (int size : new int[] {-1, 0, 101}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new TaskQuery(null, null, null, null, null, 0, size));
        }
    }

    @Test void jsonRoundTripsAllCreateReadValuesAndQueries() {
        for (Object value : List.of(create(List.of(CASE), true), assignment(), task(List.of(assignment())),
                new TaskQuery(GROUP, CASE, AssignmentState.COMPLETED, DUE, DUE, 0, 25))) {
            assertEquals(value, JSON.readValue(JSON.writeValueAsString(value), value.getClass()));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fields(Object value) {
        return JSON.readValue(JSON.writeValueAsString(value), Map.class);
    }

    @Test void jsonRejectsMissingOrNullRequiredFieldsAcrossCreateAndResponses() {
        for (Object value : List.of(create(List.of(CASE), true), assignment(), task(List.of()))) {
            for (String field : fields(value).keySet()) {
                if (field.equals("currentSubmissionId")) continue; // Required-nullable needs wire presence validation.
                if (value instanceof CreateTask && field.equals("lateAllowed")) continue;
                for (boolean remove : new boolean[] {true, false}) {
                    var body = new LinkedHashMap<>(fields(value));
                    if (remove) body.remove(field); else body.put(field, null);
                    assertThrows(JacksonException.class,
                            () -> JSON.readValue(JSON.writeValueAsString(body), value.getClass()), field);
                }
            }
        }
    }

    @Test void jsonOmittedLateAllowedDefaultsTrueAndInvalidEnumOrUnknownFieldFails() {
        var body = new LinkedHashMap<>(fields(create(List.of(CASE), false)));
        body.remove("lateAllowed");
        assertTrue(JSON.readValue(JSON.writeValueAsString(body), CreateTask.class).lateAllowed());
        for (Object invalid : List.of("URGENT", "high", 0)) {
            body.put("priority", invalid);
            assertThrows(JacksonException.class,
                    () -> JSON.readValue(JSON.writeValueAsString(body), CreateTask.class));
        }
        body.put("priority", "HIGH");
        body.put("actorId", GROUP.toString());
        assertThrows(JacksonException.class,
                () -> JSON.readValue(JSON.writeValueAsString(body), CreateTask.class));
        for (TaskPriority priority : TaskPriority.values()) {
            assertEquals(priority, JSON.readValue('"' + priority.name() + '"', TaskPriority.class));
        }
        for (AssignmentState state : AssignmentState.values()) {
            assertEquals(state, JSON.readValue('"' + state.name() + '"', AssignmentState.class));
        }
    }
}
