package vn.utbridora.auth.modules.internship.domain;

import java.time.*;
import java.util.List;
import java.util.UUID;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** Pure CP-WS3 v1.0 rules; no authorization, persistence or file-scan assumptions. */
public final class WeeklyReportPolicy {
    public static final long MAX_VERSION = 9_007_199_254_740_991L;
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private WeeklyReportPolicy() {}

    public static <T> T required(T value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    public static String text(String value, int minimum, int maximum, String field) {
        required(value, field);
        String normalized = value.replaceAll("(?U)^\\s+|\\s+$", "");
        int length = normalized.codePointCount(0, normalized.length());
        if (length < minimum || length > maximum) throw new IllegalArgumentException(field + " is outside Text range");
        return normalized;
    }

    public static long version(Long value) {
        if (value == null || value < 0 || value > MAX_VERSION) throw new IllegalArgumentException("Invalid report version");
        return value;
    }

    public static long nextVersion(long current, long expected) {
        version(current); version(expected);
        if (current != expected || current == MAX_VERSION) throw failure(409, "VERSION_CONFLICT");
        return current + 1;
    }

    public static String reason(String value) {
        if (value == null || !value.matches("[A-Z0-9_]{3,64}")) throw new IllegalArgumentException("Invalid revision reason");
        return value;
    }

    public static List<UUID> files(List<UUID> values) {
        required(values, "evidenceFileIds");
        if (values.size() > 20 || values.stream().anyMatch(id -> id == null)) {
            throw new IllegalArgumentException("Invalid evidenceFileIds");
        }
        // CP-WS3 v1.0 declares an array, not a set; preserve its order and cardinality.
        return List.copyOf(values);
    }

    public static void requireWeek(LocalDate weekStart, LocalDate startDate, LocalDate endDate) {
        required(weekStart, "weekStart"); required(startDate, "startDate"); required(endDate, "endDate");
        try {
            if (startDate.isAfter(endDate) || weekStart.getDayOfWeek() != DayOfWeek.MONDAY
                    || weekStart.isAfter(endDate) || weekStart.plusDays(6).isBefore(startDate)) {
                throw failure(422, "REPORT_WEEK_INVALID");
            }
            dueAt(weekStart);
        } catch (DateTimeException invalid) { throw failure(422, "REPORT_WEEK_INVALID"); }
    }

    public static Instant dueAt(LocalDate weekStart) {
        required(weekStart, "weekStart");
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) throw failure(422, "REPORT_WEEK_INVALID");
        try { return weekStart.plusDays(8).atTime(23, 59, 59).atZone(ZONE).toInstant(); }
        catch (DateTimeException invalid) { throw failure(422, "REPORT_WEEK_INVALID"); }
    }

    public static boolean lateFlag(Instant dueAt, Instant submittedAt) {
        return required(submittedAt, "submittedAt").isAfter(required(dueAt, "dueAt"));
    }
}
