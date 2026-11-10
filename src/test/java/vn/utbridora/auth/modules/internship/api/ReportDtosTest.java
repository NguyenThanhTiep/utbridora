package vn.utbridora.auth.modules.internship.api;

import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.modules.internship.domain.WeeklyReport;
import static vn.utbridora.auth.modules.internship.api.ReportDtos.*;
import static org.junit.jupiter.api.Assertions.*;

class ReportDtosTest {
    private final UUID id = UUID.randomUUID();
    private final LocalDate week = LocalDate.parse("2026-11-02");
    @Test void normalizesUnicodeWhitespaceAndCountsCodepointsNotUtf16() {
        var input = new ReportInput(id, week, "\u00a0 " + "😀".repeat(10_000) + "\u3000", "\u00a0", " Plan ", List.of());
        assertEquals(10_000, input.completedWork().codePointCount(0, input.completedWork().length()));
        assertEquals("", input.difficulties()); assertEquals("Plan", input.nextPlan());
        assertThrows(IllegalArgumentException.class, () -> new ReportInput(id, week, "😀".repeat(10_001), "", "Next", List.of()));
        new ReportInput(id, week, "Work", "😀".repeat(5_000), "😀".repeat(5_000), List.of());
    }
    static Stream<Arguments> invalidText() {
        return Stream.of(Arguments.of(null, "", "Next"), Arguments.of("\u00a0", "", "Next"),
                Arguments.of("Work", null, "Next"), Arguments.of("Work", "X".repeat(5_001), "Next"),
                Arguments.of("Work", "", null), Arguments.of("Work", "", " "),
                Arguments.of("Work", "", "X".repeat(5_001)));
    }
    @ParameterizedTest @MethodSource("invalidText")
    void validatesBothCreateAndUpdateContent(String work, String difficulties, String plan) {
        assertThrows(IllegalArgumentException.class, () -> new ReportInput(id, week, work, difficulties, plan, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new UpdateReport(1L, work, difficulties, plan, List.of()));
    }
    @Test void allRequiredValuesAndFileArrayBoundsAreChecked() {
        assertThrows(IllegalArgumentException.class, () -> new ReportInput(null, week, "Work", "", "Next", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ReportInput(id, null, "Work", "", "Next", List.of()));
        for (List<UUID> invalid : Arrays.asList(null, Arrays.asList((UUID) null), Collections.nCopies(21, id))) {
            assertThrows(IllegalArgumentException.class, () -> new ReportInput(id, week, "Work", "", "Next", invalid));
        }
        var source = new ArrayList<>(Collections.nCopies(20, id));
        var input = new ReportInput(id, week, "Work", "", "Next", source); source.clear();
        assertEquals(20, input.evidenceFileIds().size());
        assertThrows(UnsupportedOperationException.class, () -> input.evidenceFileIds().clear());
    }
    @ParameterizedTest @NullSource @ValueSource(longs = {-1, 9_007_199_254_740_992L})
    void everyCommandChecksVersionBounds(Long version) {
        assertThrows(IllegalArgumentException.class, () -> new UpdateReport(version, "Work", "", "Next", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SubmitReport(version));
        assertThrows(IllegalArgumentException.class, () -> new ReportReview(version, "Good"));
        assertThrows(IllegalArgumentException.class, () -> new CreateReportRevision(version, "FIX"));
    }
    @Test void commandsAcceptExactVersionExtremesAndRequireCommentAndReason() {
        for (long version : new long[]{0, 9_007_199_254_740_991L}) {
            new SubmitReport(version); new ReportReview(version, "Good"); new CreateReportRevision(version, "FIX");
        }
        for (String value : new String[]{null, "", " ", "X".repeat(2_001)}) {
            assertThrows(IllegalArgumentException.class, () -> new ReportReview(1L, value));
        }
        assertThrows(IllegalArgumentException.class, () -> new CreateReportRevision(1L, "lowercase"));
    }
    @Test void queriesHaveRequiredCaseAndContractDefaultsAndLimits() {
        var query = new ReportQuery(id, null, null, null);
        assertEquals(0, query.page()); assertEquals(25, query.size());
        new ReportQuery(id, WeeklyReport.State.REVIEWED, 10_000, 100);
        assertThrows(IllegalArgumentException.class, () -> new ReportQuery(null, null, null, null));
        for (int page : new int[]{-1, 10_001}) assertThrows(IllegalArgumentException.class, () -> new ReportQuery(id, null, page, 1));
        for (int size : new int[]{0, 101}) assertThrows(IllegalArgumentException.class, () -> new ReportQuery(id, null, 0, size));
        new ReportReadQuery(null); new ReportReadQuery(1);
        assertThrows(IllegalArgumentException.class, () -> new ReportReadQuery(0));
    }
    @Test void responseSerializesExactlyTheV1FieldsWithExplicitNullableTimes() {
        var report = WeeklyReport.create(id, UUID.randomUUID(), week, week, week.plusDays(30),
                new WeeklyReport.Content("Work", "", "Next", List.of()));
        var mapper = JsonMapper.builder().build();
        Map<?, ?> fields = mapper.convertValue(mapper.readTree(mapper.writeValueAsString(ReportDto.from(report, report.current()))), Map.class);
        assertEquals(Set.of("reportId", "caseId", "weekStart", "revisionNo", "version", "state", "completedWork",
                "difficulties", "nextPlan", "evidenceFileIds", "dueAt", "submittedAt", "reviewedAt", "lateFlag"), fields.keySet());
        assertNull(fields.get("submittedAt")); assertNull(fields.get("reviewedAt")); assertEquals("DRAFT", fields.get("state"));
        var submitted = report.submit(1, report.dueAt());
        var reviewed = submitted.review(2, UUID.randomUUID(), "Good", report.dueAt());
        var dto = ReportDto.from(reviewed, reviewed.current());
        assertEquals(reviewed.dueAt(), dto.submittedAt()); assertEquals(dto.submittedAt(), dto.reviewedAt());
        assertFalse(dto.lateFlag());
    }
}
