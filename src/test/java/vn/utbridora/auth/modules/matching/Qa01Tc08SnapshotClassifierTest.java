package vn.utbridora.auth.modules.matching;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import vn.utbridora.auth.modules.matching.domain.StrictClassifier;
import vn.utbridora.auth.modules.matching.domain.StrictClassifier.Status;
import vn.utbridora.auth.modules.student.port.StudentProfileSnapshotPort.ProfileSnapshot;
import vn.utbridora.auth.modules.student.port.StudentProfileSnapshotPort.SkillValue;
import vn.utbridora.auth.modules.student.api.StudentProfileDtos.DateRange;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.RequiredCriteria;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.SkillCriterion;

/** QA01 / T1-05-TC08: narrow, independent assertions on the real strict classifier. */
class Qa01Tc08SnapshotClassifierTest {
    private static final String FROM = "2027-01-01";
    private static final String THROUGH = "2027-01-31";

    private static RequiredCriteria required() {
        return new RequiredCriteria(
            List.of("SE"),
            List.of(new SkillCriterion("JAVA", 3, new BigDecimal("1.0"))),
            new vn.utbridora.auth.modules.cooperation.domain.DemandModels.DateRange(
                LocalDate.parse(FROM), LocalDate.parse(THROUGH)));
    }

    private static ProfileSnapshot snapshot(String major, boolean declaredSkills,
                                            List<SkillValue> skills, boolean declaredAvailability,
                                            List<DateRange> ranges, long version, String hash) {
        return new ProfileSnapshot(
            "qa01-tc08-snapshot", "qa01-tc08-student", "qa01-school", "qa01-term",
            major, declaredSkills, skills, declaredAvailability, ranges,
            version, hash, "demo-v1", "academic-v1", "taxonomy-v1", Instant.EPOCH);
    }

    private static Status status(StrictClassifier.MatchResult result, String criterion) {
        return result.details().stream()
            .filter(d -> d.criterionCode().equals(criterion) && d.kind().equals(
                criterion.equals("AVAILABILITY") ? "AVAILABILITY" : "SKILL"))
            .findFirst().orElseThrow().status();
    }

    @Test
    void unreportedAndReportedEmptyAreNotConflated() {
        var unreported = StrictClassifier.evaluate(
            snapshot("SE", false, List.of(), false, List.of(), 7, "h7"),
            required(), List.of(), Map.of());
        assertEquals("MISSING_DATA", unreported.overallStatus());
        assertEquals(Status.UNKNOWN, status(unreported, "JAVA"));
        assertEquals(Status.UNKNOWN, status(unreported, "AVAILABILITY"));
        assertNull(unreported.fitScore());
        assertEquals("DATA_NOT_DECLARED", unreported.details().stream()
            .filter(d -> d.criterionCode().equals("JAVA")).findFirst().orElseThrow().reasonCode());

        var emptyButDeclared = StrictClassifier.evaluate(
            snapshot("SE", true, List.of(), true, List.of(), 7, "h7"),
            required(), List.of(), Map.of());
        assertEquals("NOT_MATCH", emptyButDeclared.overallStatus());
        assertEquals(Status.FAIL, status(emptyButDeclared, "JAVA"));
        assertEquals(Status.FAIL, status(emptyButDeclared, "AVAILABILITY"));
        assertNull(emptyButDeclared.fitScore());
    }

    @Test
    void failedRequiredCriterionTakesPrecedenceOverUnknown() {
        var result = StrictClassifier.evaluate(
            snapshot("SE", false, List.of(), true, List.of(), 7, "h7"),
            required(), List.of(), Map.of());
        assertEquals("NOT_MATCH", result.overallStatus());
        assertEquals(Status.UNKNOWN, status(result, "JAVA"));
        assertEquals(Status.FAIL, status(result, "AVAILABILITY"));
    }

    @Test
    void validSnapshotPreservesOwnerReferenceAndVersion() {
        var valid = snapshot("SE", true,
            List.of(new SkillValue("JAVA", 4, new BigDecimal("1.5"), "SELF_DECLARED")),
            true, List.of(new DateRange(FROM, "2027-01-15"), new DateRange("2027-01-16", THROUGH)),
            7, "qa01-relevant-hash");
        var result = StrictClassifier.evaluate(valid, required(),
            List.of(new SkillCriterion("JAVA", null, null),
                    new SkillCriterion("SQL", null, null)), Map.of());
        assertEquals("MATCH", result.overallStatus());
        assertEquals(new BigDecimal("50.00"), result.fitScore());
        assertEquals("qa01-tc08-student", result.studentUserId());
        assertEquals("qa01-tc08-snapshot", result.profileSnapshotId());
        assertEquals(7L, result.profileVersion());
        assertEquals("qa01-relevant-hash", result.relevantHash());
    }

    @Test
    void dateGapAndUnknownMajorHaveDifferentOutcomes() {
        var values = List.of(new SkillValue("JAVA", 3, new BigDecimal("1.0"), "SELF_DECLARED"));
        var gap = StrictClassifier.evaluate(
            snapshot("SE", true, values, true,
                List.of(new DateRange(FROM, "2027-01-12"),
                        new DateRange("2027-01-14", THROUGH)), 7, "h7"),
            required(), List.of(), Map.of());
        assertEquals("NOT_MATCH", gap.overallStatus());
        assertEquals(Status.FAIL, status(gap, "AVAILABILITY"));

        var noMajor = StrictClassifier.evaluate(
            snapshot(null, true, values, true, List.of(new DateRange(FROM, THROUGH)), 7, "h7"),
            required(), List.of(), Map.of());
        assertEquals("MISSING_DATA", noMajor.overallStatus());
        assertEquals(Status.UNKNOWN, noMajor.details().getFirst().status());
    }
}
