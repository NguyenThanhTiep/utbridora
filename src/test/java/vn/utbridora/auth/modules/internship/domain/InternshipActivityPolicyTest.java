package vn.utbridora.auth.modules.internship.domain;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class InternshipActivityPolicyTest {
    private static final Instant NOW = Instant.parse("2026-11-06T03:00:00Z");

    @ParameterizedTest
    @CsvSource({
        "WAITING_START,PENDING_APPROVAL,false",
        "WAITING_START,APPROVED,false",
        "WAITING_START,ACTIVE,false",
        "WAITING_START,CANCELLED,false",
        "WAITING_START,COMPLETED,false",
        "IN_PROGRESS,PENDING_APPROVAL,false",
        "IN_PROGRESS,APPROVED,false",
        "IN_PROGRESS,ACTIVE,true",
        "IN_PROGRESS,CANCELLED,false",
        "IN_PROGRESS,COMPLETED,false",
        "COMPLETED,PENDING_APPROVAL,false",
        "COMPLETED,APPROVED,false",
        "COMPLETED,ACTIVE,false",
        "COMPLETED,CANCELLED,false",
        "COMPLETED,COMPLETED,false",
        "TERMINATED,PENDING_APPROVAL,false",
        "TERMINATED,APPROVED,false",
        "TERMINATED,ACTIVE,false",
        "TERMINATED,CANCELLED,false",
        "TERMINATED,COMPLETED,false",
        "CANCELLED,PENDING_APPROVAL,false",
        "CANCELLED,APPROVED,false",
        "CANCELLED,ACTIVE,false",
        "CANCELLED,CANCELLED,false",
        "CANCELLED,COMPLETED,false"
    })
    void stateMatrixRequiresInProgressCaseAndActivePlacementEvenAfterEffectiveTime(
            String caseState, String placementState, boolean expected) {
        assertEquals(expected, InternshipActivityPolicy.hasRequiredStateAndStartTime(
                caseState, placementState, NOW.minusSeconds(1), NOW));
    }

    @ParameterizedTest
    @CsvSource({"-1,true", "0,true", "1,false"})
    void effectiveTimeBoundaryIsInclusive(long effectiveOffsetNanos, boolean expected) {
        assertEquals(expected, InternshipActivityPolicy.hasRequiredStateAndStartTime(
                "IN_PROGRESS", "ACTIVE", NOW.plusNanos(effectiveOffsetNanos), NOW));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"UNKNOWN", "in_progress", " IN_PROGRESS", "IN_PROGRESS "})
    void invalidCaseStateFailsClosed(String caseState) {
        assertFalse(InternshipActivityPolicy.hasRequiredStateAndStartTime(
                caseState, "ACTIVE", NOW, NOW));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"UNKNOWN", "active", " ACTIVE", "ACTIVE "})
    void invalidPlacementStateFailsClosed(String placementState) {
        assertFalse(InternshipActivityPolicy.hasRequiredStateAndStartTime(
                "IN_PROGRESS", placementState, NOW, NOW));
    }

    @Test
    void missingTimeFailsClosed() {
        assertFalse(InternshipActivityPolicy.hasRequiredStateAndStartTime(
                "IN_PROGRESS", "ACTIVE", null, NOW));
        assertFalse(InternshipActivityPolicy.hasRequiredStateAndStartTime(
                "IN_PROGRESS", "ACTIVE", NOW, null));
        assertFalse(InternshipActivityPolicy.hasRequiredStateAndStartTime(
                "IN_PROGRESS", "ACTIVE", null, null));
    }
}
