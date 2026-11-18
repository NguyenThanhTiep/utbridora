package vn.utbridora.auth.modules.matching;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.DateRange;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.RequiredCriteria;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.SkillCriterion;
import vn.utbridora.auth.modules.matching.domain.StrictClassifier;
import vn.utbridora.auth.modules.student.api.StudentProfileDtos;
import vn.utbridora.auth.modules.student.port.StudentProfileSnapshotPort.ProfileSnapshot;
import vn.utbridora.auth.modules.student.port.StudentProfileSnapshotPort.SkillValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchingPerformanceSmokeTest {
    @Test void reportsP95ForFourHundredDistinctProfilesAndFiveRequiredSkills() {
        var requiredSkills = List.of("JAVA", "SQL", "SPRING", "GIT", "DOCKER").stream()
            .map(code -> new SkillCriterion(code, 1, BigDecimal.ZERO)).toList();
        var criteria = new RequiredCriteria(List.of("SE"), requiredSkills,
            new DateRange(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-01-31")));
        var availability = List.of(new StudentProfileDtos.DateRange("2027-01-01", "2027-01-31"));
        var skills = requiredSkills.stream()
            .map(c -> new SkillValue(c.skillCode(), 3, BigDecimal.ONE, "SELF_DECLARED")).toList();
        var profiles = new ArrayList<ProfileSnapshot>(400);
        for (int index = 0; index < 400; index++) {
            profiles.add(new ProfileSnapshot("snapshot-" + index, "student-" + index, "school", "term", "SE",
                true, skills, true, availability, 1, "hash-" + index, "demo-v1", "academic-v1", "skills-v1", Instant.EPOCH));
        }

        long coldStarted = System.nanoTime();
        var coldResults = profiles.stream().map(profile -> StrictClassifier.evaluate(profile, criteria, List.of(), Map.of())).toList();
        long coldMillis = (System.nanoTime() - coldStarted) / 1_000_000;
        assertEquals(400, coldResults.size());

        for (int warmup = 0; warmup < 20; warmup++) {
            var warmResults = profiles.stream().map(profile -> StrictClassifier.evaluate(profile, criteria, List.of(), Map.of())).toList();
            assertEquals(400, warmResults.size());
        }

        var samples = new long[100];
        for (int run = 0; run < samples.length; run++) {
            long started = System.nanoTime();
            var results = profiles.stream().map(profile -> StrictClassifier.evaluate(profile, criteria, List.of(), Map.of())).toList();
            samples[run] = System.nanoTime() - started;
            assertEquals(400, results.size());
            assertEquals(400, results.stream().filter(result -> "MATCH".equals(result.overallStatus())).count());
        }

        java.util.Arrays.sort(samples);
        double p95Millis = samples[(int) Math.ceil(samples.length * 0.95) - 1] / 1_000_000.0;
        System.out.printf(java.util.Locale.ROOT,
            "T4-07 smoke classifier cục bộ: hồ sơ=400, kỹ năng bắt buộc=5, lần lạnh=%d ms, làm nóng=20, đo=100, p95=%.3f ms, đồng thời=1, chỉ StrictClassifier%n",
            coldMillis, p95Millis);
    }
}
