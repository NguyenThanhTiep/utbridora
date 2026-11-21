package vn.utbridora.auth.modules.matching;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import vn.utbridora.auth.modules.cooperation.domain.DemandModels.*;
import vn.utbridora.auth.modules.matching.api.MatchingController;
import vn.utbridora.auth.modules.matching.application.MatchingService;
import vn.utbridora.auth.modules.matching.domain.ProtectedAggregatePolicy;
import vn.utbridora.auth.modules.matching.domain.ProtectedAggregatePolicy.ProtectedAggregate;
import vn.utbridora.auth.modules.matching.domain.ProtectedAggregatePolicy.ReleaseIdentity;
import vn.utbridora.auth.modules.matching.domain.StrictClassifier;
import vn.utbridora.auth.modules.matching.domain.StrictClassifier.MatchResult;
import vn.utbridora.auth.modules.student.api.StudentProfileDtos.DateRange;
import vn.utbridora.auth.modules.student.port.StudentProfileSnapshotPort.*;
import static vn.utbridora.auth.operations.WorkflowValues.JSON;

/**
 * QA04 - Bộ Unit Test Độc Lập cho Phân hệ T4 (T4-03, T4-04, T4-05):
 * Gọi trực tiếp vào mã nguồn thật (ProtectedAggregatePolicy, StrictClassifier, MatchingService/Controller DTO)
 * để kiểm chứng k=5, suppression, fingerprint canonical, Zero PII, SRS Oracle S1-S11 và smoke performance.
 */
class QA04MatchingPrivacyTest {

    private ProfileSnapshot buildProfile(String id, String major, boolean hasJava, boolean matchesDate) {
        List<SkillValue> skills = hasJava
                ? List.of(new SkillValue("JAVA", 3, new BigDecimal("2.0"), "SELF_DECLARED"))
                : List.of(new SkillValue("PYTHON", 2, BigDecimal.ONE, "SELF_DECLARED"));
        List<DateRange> periods = matchesDate
                ? List.of(new DateRange("2027-01-01", "2027-06-01"))
                : List.of(new DateRange("2026-01-01", "2026-05-01"));
        return new ProfileSnapshot(
                "snap-" + id,
                "student-" + id,
                "school-01",
                "term-2027-1",
                major,
                true,
                skills,
                true,
                periods,
                1,
                "hash-" + id,
                "demo-v1",
                "academic-v1",
                "skills-v1",
                Instant.EPOCH
        );
    }

    private RequiredCriteria javaCriteria() {
        return new RequiredCriteria(
                List.of("SE"),
                List.of(new SkillCriterion("JAVA", 3, BigDecimal.ONE)),
                new vn.utbridora.auth.modules.cooperation.domain.DemandModels.DateRange(
                        LocalDate.parse("2027-02-01"),
                        LocalDate.parse("2027-05-01")
                )
        );
    }

    @Test
    @DisplayName("TC-QA04-U01 [T4-03/T4-04]: Ngưỡng k=5 và suppression qua ProtectedAggregatePolicy & StrictClassifier thật (0, 1, 4, 5, 9, 10, 19, 20)")
    void verifiesKAnonymityBandsDirectlyAgainstProductionPolicyAndClassifier() {
        RequiredCriteria criteria = javaCriteria();
        Map<Integer, String> expectedBands = new LinkedHashMap<>();
        expectedBands.put(0, "SUPPRESSED");
        expectedBands.put(1, "SUPPRESSED");
        expectedBands.put(4, "SUPPRESSED");
        expectedBands.put(5, "5-9");
        expectedBands.put(9, "5-9");
        expectedBands.put(10, "10-19");
        expectedBands.put(19, "10-19");
        expectedBands.put(20, "20+");

        for (var entry : expectedBands.entrySet()) {
            int targetMatches = entry.getKey();
            List<ProfileSnapshot> cohort = new ArrayList<>();
            for (int i = 0; i < targetMatches; i++) {
                cohort.add(buildProfile("match-" + targetMatches + "-" + i, "SE", true, true));
            }
            // Thêm nhiễu NOT_MATCH (sai ngành / thiếu kỹ năng / lệch lịch) để xác nhận chỉ đếm nhóm MATCH
            cohort.add(buildProfile("noise-major-" + targetMatches, "BA", true, true));
            cohort.add(buildProfile("noise-skill-" + targetMatches, "SE", false, true));
            cohort.add(buildProfile("noise-date-" + targetMatches, "SE", true, false));

            long actualMatched = cohort.stream()
                    .map(profile -> StrictClassifier.evaluate(profile, criteria, List.of(), Map.of()))
                    .filter(result -> "MATCH".equals(result.overallStatus()))
                    .count();

            assertEquals(targetMatches, actualMatched, "Số SV MATCH nội bộ phải khớp fixture=" + targetMatches);
            assertEquals(entry.getValue(), ProtectedAggregatePolicy.eligibleBand(actualMatched),
                    "Band trả về cho fixture=" + targetMatches + " phải là " + entry.getValue());
        }

        assertThrows(IllegalArgumentException.class, () -> ProtectedAggregatePolicy.eligibleBand(-1),
                "Số âm không hợp lệ phải bị từ chối");
    }

    @Test
    @DisplayName("TC-QA04-U02 [T4-04/T4-05]: Fingerprint SHA-256 canonical ổn định, tách biệt mọi chiều scope và không chứa actor/demand")
    void verifiesCanonicalFingerprintStabilityAndScopeIsolation() {
        ReleaseIdentity base = new ReleaseIdentity(
                "ent-01", "school-01", "term-2027-1", "dataset-01",
                "criteria-hash-a", "profile-v1", StrictClassifier.RULE_VERSION, "skills-v1"
        );
        String fpBase = ProtectedAggregatePolicy.fingerprint(base);
        assertEquals(64, fpBase.length());
        assertTrue(fpBase.matches("[0-9a-f]{64}"), "Fingerprint phải là chuỗi hex SHA-256 64 ký tự");

        // Cùng 8 chiều canonical -> luôn ra cùng fingerprint (ổn định khi đổi actor / demand khác cùng tiêu chí)
        assertEquals(fpBase, ProtectedAggregatePolicy.fingerprint(new ReleaseIdentity(
                "ent-01", "school-01", "term-2027-1", "dataset-01",
                "criteria-hash-a", "profile-v1", StrictClassifier.RULE_VERSION, "skills-v1"
        )));

        // Thay đổi bất kỳ chiều nào đều sinh fingerprint khác -> buộc ledger phát hiện và chặn 409 nếu trong cùng batch
        List<ReleaseIdentity> alteredScopes = List.of(
                new ReleaseIdentity("ent-02", base.schoolOrgId(), base.termId(), base.datasetSnapshotId(), base.criteriaHash(), base.profileSnapshotVersion(), base.ruleVersion(), base.taxonomyVersion()),
                new ReleaseIdentity(base.enterpriseOrgId(), "school-02", base.termId(), base.datasetSnapshotId(), base.criteriaHash(), base.profileSnapshotVersion(), base.ruleVersion(), base.taxonomyVersion()),
                new ReleaseIdentity(base.enterpriseOrgId(), base.schoolOrgId(), "term-2027-2", base.datasetSnapshotId(), base.criteriaHash(), base.profileSnapshotVersion(), base.ruleVersion(), base.taxonomyVersion()),
                new ReleaseIdentity(base.enterpriseOrgId(), base.schoolOrgId(), base.termId(), "dataset-02", base.criteriaHash(), base.profileSnapshotVersion(), base.ruleVersion(), base.taxonomyVersion()),
                new ReleaseIdentity(base.enterpriseOrgId(), base.schoolOrgId(), base.termId(), base.datasetSnapshotId(), "criteria-hash-b", base.profileSnapshotVersion(), base.ruleVersion(), base.taxonomyVersion()),
                new ReleaseIdentity(base.enterpriseOrgId(), base.schoolOrgId(), base.termId(), base.datasetSnapshotId(), base.criteriaHash(), "profile-v2", base.ruleVersion(), base.taxonomyVersion()),
                new ReleaseIdentity(base.enterpriseOrgId(), base.schoolOrgId(), base.termId(), base.datasetSnapshotId(), base.criteriaHash(), base.profileSnapshotVersion(), "strict-v2", base.taxonomyVersion()),
                new ReleaseIdentity(base.enterpriseOrgId(), base.schoolOrgId(), base.termId(), base.datasetSnapshotId(), base.criteriaHash(), base.profileSnapshotVersion(), base.ruleVersion(), "skills-v2")
        );
        for (ReleaseIdentity altered : alteredScopes) {
            assertNotEquals(fpBase, ProtectedAggregatePolicy.fingerprint(altered),
                    "Thay đổi phạm vi/tiêu chí phải sinh fingerprint khác: " + altered);
        }

        // Bắt buộc các trường không được rỗng/null
        assertThrows(IllegalArgumentException.class, () -> new ReleaseIdentity(
                " ", base.schoolOrgId(), base.termId(), base.datasetSnapshotId(),
                base.criteriaHash(), base.profileSnapshotVersion(), base.ruleVersion(), base.taxonomyVersion()));
    }

    @Test
    @DisplayName("TC-QA04-U03 [T4-04]: Zero PII trên DTO thật (JSON Summary & Chart) dành cho Doanh nghiệp")
    void verifiesZeroPiiOnProductionEnterpriseDtos() throws Exception {
        ProtectedAggregate release = new ProtectedAggregate(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                ProtectedAggregatePolicy.eligibleBand(5)
        );
        MatchingService.Evaluated evaluated = new MatchingService.Evaluated("ENTERPRISE", null, release);
        String json = JSON.writeValueAsString(evaluated);
        var root = JSON.readTree(json);

        assertEquals(Set.of("audience", "release"), new HashSet<>(root.propertyNames()),
                "Payload doanh nghiệp chỉ được có đúng 2 khóa audience và release (không có summary)");
        assertEquals("ENTERPRISE", root.get("audience").asString());

        var releaseNode = root.get("release");
        assertEquals(Set.of("releaseId", "matchRunId", "eligibleBand"), new HashSet<>(releaseNode.propertyNames()),
                "Release chỉ được phép có đúng 3 trường: releaseId, matchRunId, eligibleBand");
        assertEquals("5-9", releaseNode.get("eligibleBand").asString());

        // Chart DTO cũng chỉ chứa đúng danh sách eligibleBands
        String chartJson = JSON.writeValueAsString(new MatchingController.ProtectedChart(List.of(release.eligibleBand())));
        var chartNode = JSON.readTree(chartJson);
        assertEquals(Set.of("eligibleBands"), new HashSet<>(chartNode.propertyNames()));
        assertEquals("5-9", chartNode.get("eligibleBands").get(0).asString());

        List<String> forbiddenTokens = List.of(
                "rawCount", "total", "percent", "percentage", "profileCount", "matched", "missingData", "notMatched",
                "students", "studentUserId", "studentName", "displayName", "email", "phone", "mssv",
                "fingerprint", "criteriaHash", "summary", "details", "@"
        );
        for (String token : forbiddenTokens) {
            assertFalse(json.contains(token), "JSON summary không được chứa token cấm: " + token);
            assertFalse(chartJson.contains(token), "JSON chart không được chứa token cấm: " + token);
        }
    }

    @Test
    @DisplayName("TC-QA04-U04 [T4-03]: Bộ 11 kịch bản độc lập S1-S11 từ SRS Oracle và quy tắc xếp hạng R11")
    void verifiesIndependentSrsOracleS1ThroughS11AndR11Ranking() throws Exception {
        var oracle = JSON.readTree(Files.readString(Path.of("docs/testing/fixtures/t4-s1-s11.json")));
        var required = oracle.get("required");
        var skills = new ArrayList<SkillCriterion>();
        required.get("skills").forEach(s -> skills.add(new SkillCriterion(s.asString(), null, null)));
        var majors = new ArrayList<String>();
        required.get("majorCodes").forEach(m -> majors.add(m.asString()));
        var criteria = new RequiredCriteria(majors, skills, new vn.utbridora.auth.modules.cooperation.domain.DemandModels.DateRange(
                LocalDate.parse(required.get("dateRange").get("startDate").asString()),
                LocalDate.parse(required.get("dateRange").get("endDate").asString())
        ));

        int count = 0;
        var results = new ArrayList<MatchResult>();
        for (var c : oracle.get("cases")) {
            var p = c.get("profile");
            var values = new ArrayList<SkillValue>();
            p.get("skills").forEach(s -> values.add(new SkillValue(s.asString(), 3, BigDecimal.ONE, "SELF_DECLARED")));
            var periods = new ArrayList<DateRange>();
            p.get("availablePeriods").forEach(r -> periods.add(new DateRange(r.get("startDate").asString(), r.get("endDate").asString())));
            var profile = new ProfileSnapshot(
                    "snapshot-" + count, "student-" + String.format("%02d", count), "school", "term",
                    p.get("majorCode").isNull() ? null : p.get("majorCode").asString(),
                    p.get("skillsDeclared").asBoolean(),
                    values,
                    p.get("availabilityDeclared").asBoolean(),
                    periods,
                    1, "hash", "demo-v1", "academic-v1", "skills-v1", Instant.EPOCH
            );
            MatchResult result = StrictClassifier.evaluate(profile, criteria, List.of(), Map.of("Java Core", "JAVA"));
            assertEquals(c.get("expectedOutcome").asString(), result.overallStatus(), c.get("id").asString());
            if (!"MATCH".equals(result.overallStatus())) {
                assertNull(result.fitScore(), "R11: Nhóm không đạt MATCH không được có fitScore");
            } else {
                assertNotNull(result.fitScore(), "R11: Nhóm MATCH phải có fitScore");
            }
            results.add(result);
            count++;
        }
        assertEquals(11, count, "Phải kiểm tra đủ cả 11 kịch bản SRS S1-S11");
        var ranked = results.stream().sorted(StrictClassifier.ranking()).toList();
        assertEquals("MATCH", ranked.getFirst().overallStatus(), "R11: Nhóm MATCH phải xếp trên các nhóm không có điểm");
    }

    @Test
    @DisplayName("TC-QA04-U05 [T4-05]: Smoke Performance nhỏ - 400 hồ sơ x 5 kỹ năng bắt buộc, kiểm tra p95")
    void verifiesSmallScaleMatchingClassifierPerformance() {
        var requiredSkills = List.of("JAVA", "SQL", "SPRING", "GIT", "DOCKER").stream()
                .map(code -> new SkillCriterion(code, 1, BigDecimal.ZERO)).toList();
        var criteria = new RequiredCriteria(List.of("SE"), requiredSkills,
                new vn.utbridora.auth.modules.cooperation.domain.DemandModels.DateRange(
                        LocalDate.parse("2027-01-01"), LocalDate.parse("2027-01-31")));
        var availability = List.of(new DateRange("2027-01-01", "2027-01-31"));
        var skills = requiredSkills.stream()
                .map(c -> new SkillValue(c.skillCode(), 3, BigDecimal.ONE, "SELF_DECLARED")).toList();

        var profiles = new ArrayList<ProfileSnapshot>(400);
        for (int i = 0; i < 400; i++) {
            profiles.add(new ProfileSnapshot("qa04-snap-" + i, "qa04-student-" + i, "school", "term", "SE",
                    true, skills, true, availability, 1, "hash-" + i, "demo-v1", "academic-v1", "skills-v1", Instant.EPOCH));
        }

        long coldStart = System.nanoTime();
        var cold = profiles.stream().map(p -> StrictClassifier.evaluate(p, criteria, List.of(), Map.of())).toList();
        long coldMs = (System.nanoTime() - coldStart) / 1_000_000;
        assertEquals(400, cold.size());

        for (int w = 0; w < 20; w++) {
            assertEquals(400, profiles.stream().map(p -> StrictClassifier.evaluate(p, criteria, List.of(), Map.of())).count());
        }

        long[] samples = new long[100];
        for (int r = 0; r < samples.length; r++) {
            long start = System.nanoTime();
            long matched = profiles.stream()
                    .map(p -> StrictClassifier.evaluate(p, criteria, List.of(), Map.of()))
                    .filter(res -> "MATCH".equals(res.overallStatus()))
                    .count();
            samples[r] = System.nanoTime() - start;
            assertEquals(400, matched);
        }
        Arrays.sort(samples);
        double p95Ms = samples[(int) Math.ceil(samples.length * 0.95) - 1] / 1_000_000.0;
        System.out.printf(Locale.ROOT,
                "QA04 T4-05 Performance Smoke: profiles=400, requiredSkills=5, cold=%d ms, p95=%.3f ms%n",
                coldMs, p95Ms);
        assertTrue(p95Ms < 250.0, "p95 cho 400 hồ sơ x 5 kỹ năng phải nhỏ hơn 250 ms, thực tế=" + p95Ms);
    }
}
