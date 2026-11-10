package vn.utbridora.auth.modules.internship.domain;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.utbridora.auth.operations.OperationFailure;
import static org.junit.jupiter.api.Assertions.*;

class WeeklyReportPolicyTest {
    private final LocalDate start = LocalDate.parse("2026-11-04"), end = LocalDate.parse("2026-12-31");

    @Test void acceptsPartialFirstAndLastWeeksAndRejectsAdjacentWeeks() {
        WeeklyReportPolicy.requireWeek(LocalDate.parse("2026-11-02"), start, end);
        WeeklyReportPolicy.requireWeek(LocalDate.parse("2026-12-28"), start, end);
        invalidWeek("2026-10-26"); invalidWeek("2027-01-04");
        assertEquals("REPORT_WEEK_INVALID", assertThrows(OperationFailure.class,
                () -> WeeklyReportPolicy.requireWeek(start, end, start)).code);
    }
    @ParameterizedTest @ValueSource(strings = {"2026-11-03", "2026-11-04", "2026-11-05", "2026-11-06", "2026-11-07", "2026-11-08"})
    void rejectsEveryNonMonday(String day) { invalidWeek(day); }

    @Test void singleSundayPlacementStillIntersectsItsMondayWeek() {
        var sunday = LocalDate.parse("2026-11-08");
        WeeklyReportPolicy.requireWeek(LocalDate.parse("2026-11-02"), sunday, sunday);
    }
    @Test void deadlineUsesVietnamZoneIncludingYearBoundary() {
        assertEquals(Instant.parse("2026-11-10T16:59:59Z"), WeeklyReportPolicy.dueAt(LocalDate.parse("2026-11-02")));
        assertEquals(Instant.parse("2027-01-05T16:59:59Z"), WeeklyReportPolicy.dueAt(LocalDate.parse("2026-12-28")));
        assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), WeeklyReportPolicy.ZONE);
    }
    @Test void deadlineIsInclusiveAndLateSubmissionsRemainAllowed() {
        var due = Instant.parse("2026-11-10T16:59:59Z");
        assertFalse(WeeklyReportPolicy.lateFlag(due, due.minusNanos(1)));
        assertFalse(WeeklyReportPolicy.lateFlag(due, due));
        assertTrue(WeeklyReportPolicy.lateFlag(due, due.plusNanos(1)));
        assertTrue(WeeklyReportPolicy.lateFlag(due, due.plus(Duration.ofDays(100))));
    }
    @Test void versionsRejectStaleAndOverflowWithoutRounding() {
        assertEquals(1, WeeklyReportPolicy.nextVersion(0, 0));
        assertEquals("VERSION_CONFLICT", assertThrows(OperationFailure.class, () -> WeeklyReportPolicy.nextVersion(2, 1)).code);
        assertEquals("VERSION_CONFLICT", assertThrows(OperationFailure.class,
                () -> WeeklyReportPolicy.nextVersion(WeeklyReportPolicy.MAX_VERSION, WeeklyReportPolicy.MAX_VERSION)).code);
        for (Long value : new Long[]{null, -1L, WeeklyReportPolicy.MAX_VERSION + 1}) {
            assertThrows(IllegalArgumentException.class, () -> WeeklyReportPolicy.version(value));
        }
    }
    private void invalidWeek(String day) {
        var failure = assertThrows(OperationFailure.class,
                () -> WeeklyReportPolicy.requireWeek(LocalDate.parse(day), start, end));
        assertEquals(422, failure.status); assertEquals("REPORT_WEEK_INVALID", failure.code);
    }
}
