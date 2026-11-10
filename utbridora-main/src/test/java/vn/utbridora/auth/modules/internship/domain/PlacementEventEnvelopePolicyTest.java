package vn.utbridora.auth.modules.internship.domain;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementEventEnvelopePolicyTest {
    // Independent contract boundary; do not derive expected values from production constants.
    private static final long MAX_VERSION = 9_007_199_254_740_991L;

    static Stream<Arguments> supportedEventsAndVersions() {
        return Stream.of("PlacementApproved", "PlacementActivated", "PlacementCancelled", "PlacementCompleted")
                .flatMap(eventType -> Stream.of(0L, 1L, 1_000L, MAX_VERSION - 1, MAX_VERSION)
                        .map(version -> Arguments.of(eventType, version)));
    }

    @ParameterizedTest
    @MethodSource("supportedEventsAndVersions")
    void acceptsAllFourEventTypesAtValidVersionBoundaries(String eventType, long version) {
        assertTrue(PlacementEventEnvelopePolicy.hasSupportedTypeAndVersions(
                eventType, 1, version, version));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        " ", "UNKNOWN", "placementActivated", "PLACEMENT_ACTIVATED",
        " PlacementActivated", "PlacementActivated ", "CaseClosed"
    })
    void rejectsMissingOrUnsupportedEventTypes(String eventType) {
        assertFalse(PlacementEventEnvelopePolicy.hasSupportedTypeAndVersions(eventType, 1, 3L, 3L));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 2, 11, Integer.MAX_VALUE})
    void rejectsEveryTestedSchemaVersionOtherThanOne(Integer schemaVersion) {
        assertFalse(PlacementEventEnvelopePolicy.hasSupportedTypeAndVersions(
                "PlacementActivated", schemaVersion, 3L, 3L));
    }

    static Stream<Arguments> invalidVersionPairs() {
        return Stream.<Long>of(null, -1L, Long.MIN_VALUE, MAX_VERSION + 1, Long.MAX_VALUE)
                .flatMap(version -> Stream.of(
                        Arguments.of(version, 3L),
                        Arguments.of(3L, version),
                        Arguments.of(version, version)));
    }

    @ParameterizedTest
    @MethodSource("invalidVersionPairs")
    void rejectsMissingOrOutOfRangeVersionsEvenWhenEqual(Long aggregateVersion, Long placementVersion) {
        assertFalse(PlacementEventEnvelopePolicy.hasSupportedTypeAndVersions(
                "PlacementActivated", 1, aggregateVersion, placementVersion));
    }

    @ParameterizedTest
    @CsvSource({
        "0,1", "1,0", "1,2", "2,1",
        "9007199254740990,9007199254740991",
        "9007199254740991,9007199254740990"
    })
    void rejectsUnequalVersionsInsideTheAllowedRange(long aggregateVersion, long placementVersion) {
        assertFalse(PlacementEventEnvelopePolicy.hasSupportedTypeAndVersions(
                "PlacementActivated", 1, aggregateVersion, placementVersion));
    }

    @Test
    void rejectsAnEntirelyMissingHeader() {
        assertFalse(PlacementEventEnvelopePolicy.hasSupportedTypeAndVersions(null, null, null, null));
    }
}
