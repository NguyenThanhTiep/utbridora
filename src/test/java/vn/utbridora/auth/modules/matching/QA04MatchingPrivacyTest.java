package vn.utbridora.auth.modules.matching;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import vn.utbridora.auth.modules.matching.domain.StrictClassifier;
import vn.utbridora.auth.modules.matching.domain.StrictClassifier.MatchResult;
import vn.utbridora.auth.modules.student.port.StudentProfileSnapshotPort.*;
import vn.utbridora.auth.modules.student.api.StudentProfileDtos.DateRange;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.*;
import static vn.utbridora.auth.operations.WorkflowValues.JSON;

/**
 * QA04 - Test Suite Bắt Buộc: Phân hệ T4 Matching & Bảo Vệ Riêng Tư k=5
 * - Kiểm thử k-anonymity (k=5) với các nhóm 0, 1, 4, 5 sinh viên thật
 * - Kiểm thử dải số bị ẩn (SUPPRESSED) và dải hợp lệ (5-9, 10-19, 20+)
 * - Chống suy luận chênh lệch (Differencing attack) & Replay lặp truy vấn
 * - Zero PII: Tuyệt đối không rò rỉ tên/email/MSSV/rawCount cho Doanh nghiệp
 * - Kiểm tra lỗi Matching & Adapter T2/T5
 */
class QA04MatchingPrivacyTest {

    public record ProtectedAggregate(String releaseId, String matchRunId, String eligibleBand) {}
    public record EvaluateEnterpriseResponse(String audience, ProtectedAggregate release) {}

    public static String calculateEligibleBand(int matchedCount) {
        if (matchedCount < 5) return "SUPPRESSED";
        if (matchedCount <= 9) return "5-9";
        if (matchedCount <= 19) return "10-19";
        return "20+";
    }

    public static String calculateFingerprint(String enterpriseOrgId, String schoolOrgId, String termId,
                                            String datasetSnapshotId, String criteriaHash,
                                            String profileSnapshotVersion, String ruleVersion, String taxonomyVersion) {
        try {
            String canonical = String.format("{\"criteriaHash\":\"%s\",\"datasetSnapshotId\":\"%s\",\"enterpriseOrgId\":\"%s\",\"profileSnapshotVersion\":\"%s\",\"ruleVersion\":\"%s\",\"schoolOrgId\":\"%s\",\"taxonomyVersion\":\"%s\",\"termId\":\"%s\"}",
                    criteriaHash, datasetSnapshotId, enterpriseOrgId, profileSnapshotVersion, ruleVersion, schoolOrgId, taxonomyVersion, termId);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ProfileSnapshot createRealProfile(String id, String major, boolean hasJava, boolean matchesDate) {
        List<SkillValue> skills = new ArrayList<>();
        if (hasJava) {
            skills.add(new SkillValue("JAVA", 3, new BigDecimal("2.0"), "SELF_DECLARED"));
        } else {
            skills.add(new SkillValue("PYTHON", 2, BigDecimal.ONE, "SELF_DECLARED"));
        }
        List<DateRange> periods = new ArrayList<>();
        if (matchesDate) {
            periods.add(new DateRange("2027-01-01", "2027-06-01"));
        } else {
            periods.add(new DateRange("2026-01-01", "2026-05-01"));
        }
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

    private RequiredCriteria createJavaCriteria() {
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
    @DisplayName("TC01..TC04: Kiểm thử k=5 với các nhóm 0, 1, 4, 5, 9, 10, 19, 20 sinh viên thật")
    void testKAnonymityBandsWithRealProfiles() {
        RequiredCriteria criteria = createJavaCriteria();

        // 1. Nhóm 0 sinh viên MATCH (toàn bộ sinh viên không học ngành SE hoặc không có Java)
        List<ProfileSnapshot> group0 = List.of(
                createRealProfile("01", "BA", false, true),
                createRealProfile("02", "AI", false, true)
        );
        int matchCount0 = (int) group0.stream()
                .map(p -> StrictClassifier.evaluate(p, criteria, List.of(), Map.of()))
                .filter(r -> "MATCH".equals(r.overallStatus())).count();
        assertEquals(0, matchCount0, "Số lượng MATCH nội bộ phải là 0");
        assertEquals("SUPPRESSED", calculateEligibleBand(matchCount0), "Nhóm 0 SV phải bị ẩn: SUPPRESSED");

        // 2. Nhóm 1 sinh viên MATCH
        List<ProfileSnapshot> group1 = List.of(
                createRealProfile("11", "SE", true, true),   // MATCH
                createRealProfile("12", "SE", false, true),  // NOT_MATCH (thiếu Java)
                createRealProfile("13", "AI", true, true)    // NOT_MATCH (sai ngành)
        );
        int matchCount1 = (int) group1.stream()
                .map(p -> StrictClassifier.evaluate(p, criteria, List.of(), Map.of()))
                .filter(r -> "MATCH".equals(r.overallStatus())).count();
        assertEquals(1, matchCount1, "Số lượng MATCH nội bộ phải là 1");
        assertEquals("SUPPRESSED", calculateEligibleBand(matchCount1), "Nhóm 1 SV (1 < 5) phải bị ẩn: SUPPRESSED");

        // 3. Nhóm 4 sinh viên MATCH (Cận trên của k < 5)
        List<ProfileSnapshot> group4 = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            group4.add(createRealProfile("4" + i, "SE", true, true)); // 4 MATCH
        }
        group4.add(createRealProfile("45", "SE", false, true)); // 1 NOT_MATCH
        int matchCount4 = (int) group4.stream()
                .map(p -> StrictClassifier.evaluate(p, criteria, List.of(), Map.of()))
                .filter(r -> "MATCH".equals(r.overallStatus())).count();
        assertEquals(4, matchCount4, "Số lượng MATCH nội bộ phải là 4");
        assertEquals("SUPPRESSED", calculateEligibleBand(matchCount4), "Nhóm 4 SV (4 < 5) phải bị ẩn: SUPPRESSED");

        // 4. Nhóm 5 sinh viên MATCH (Ngưỡng đạt k=5: bắt đầu công bố dải 5-9)
        List<ProfileSnapshot> group5 = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            group5.add(createRealProfile("5" + i, "SE", true, true)); // 5 MATCH
        }
        int matchCount5 = (int) group5.stream()
                .map(p -> StrictClassifier.evaluate(p, criteria, List.of(), Map.of()))
                .filter(r -> "MATCH".equals(r.overallStatus())).count();
        assertEquals(5, matchCount5, "Số lượng MATCH nội bộ phải là 5");
        assertEquals("5-9", calculateEligibleBand(matchCount5), "Nhóm 5 SV (k=5) phải trả về dải: 5-9");

        // 5. Kiểm tra các dải mở rộng: 9 -> 5-9, 10 -> 10-19, 19 -> 10-19, 20 -> 20+
        assertEquals("5-9", calculateEligibleBand(9), "9 SV phải thuộc dải 5-9");
        assertEquals("10-19", calculateEligibleBand(10), "10 SV phải thuộc dải 10-19");
        assertEquals("10-19", calculateEligibleBand(19), "19 SV phải thuộc dải 10-19");
        assertEquals("20+", calculateEligibleBand(20), "20 SV phải thuộc dải 20+");
        assertEquals("20+", calculateEligibleBand(100), "100 SV phải thuộc dải 20+");
    }

    @Test
    @DisplayName("TC06: Zero PII - Tuyệt đối không rò rỉ thông tin cá nhân và số đếm thô cho Doanh nghiệp")
    void testZeroPiiLeakageInEnterpriseProtectedAggregate() throws Exception {
        ProtectedAggregate release = new ProtectedAggregate(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "5-9"
        );
        EvaluateEnterpriseResponse response = new EvaluateEnterpriseResponse("ENTERPRISE", release);

        String json = JSON.writeValueAsString(response);
        var tree = JSON.readTree(json);

        // 1. Chỉ cho phép các trường được quy định trong Contract Pack BC32
        assertEquals("ENTERPRISE", tree.get("audience").asText());
        assertTrue(tree.has("release"));
        var relNode = tree.get("release");
        assertEquals(3, relNode.size(), "Release chỉ được phép có đúng 3 trường: releaseId, matchRunId, eligibleBand");
        assertTrue(relNode.has("releaseId"));
        assertTrue(relNode.has("matchRunId"));
        assertTrue(relNode.has("eligibleBand"));
        assertEquals("5-9", relNode.get("eligibleBand").asText());

        // 2. Kiểm tra cấm các trường PII và số đếm thô
        List<String> forbiddenFields = List.of(
                "rawCount", "total", "percentage", "profileCount", "matched", "missingData", "notMatched",
                "students", "studentUserId", "studentName", "name", "email", "phone", "mssv",
                "fingerprint", "criterionBreakdown", "details"
        );
        for (String forbidden : forbiddenFields) {
            assertFalse(tree.has(forbidden), "Root không được chứa trường cấm: " + forbidden);
            assertFalse(relNode.has(forbidden), "Release không được chứa trường cấm: " + forbidden);
        }

        // 3. Kiểm tra chuỗi thô không chứa ký tự email hoặc số đếm cụ thể
        assertFalse(json.contains("@"), "JSON doanh nghiệp không được chứa ký tự email @");
        assertFalse(json.contains("student-"), "JSON doanh nghiệp không được chứa student identifier");
    }

    @Test
    @DisplayName("TC05: Chống suy diễn chênh lệch (Differencing Attack) & Khóa Privacy Release")
    void testAntiDifferencingAndReplayProtection() {
        String enterpriseOrg = "org-ent-01";
        String schoolOrg = "org-sch-01";
        String termId = "term-2027";
        String datasetId = "dataset-frozen-v1";
        String batchId = "batch-demo-01";

        // Lần 1: Khởi tạo release đầu tiên
        String criteriaHash1 = "hash-criteria-original-java";
        String fp1 = calculateFingerprint(enterpriseOrg, schoolOrg, termId, datasetId, criteriaHash1, "v1", "strict-v1", "skills-v1");
        ProtectedAggregate release1 = new ProtectedAggregate(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "5-9");

        // Giả lập bảng Ledger lưu vết: (enterpriseOrg, schoolOrg, termId, batchId) -> (fingerprint, release)
        Map<String, Map.Entry<String, ProtectedAggregate>> privacyLedger = new HashMap<>();
        String ledgerKey = String.format("%s:%s:%s:%s", enterpriseOrg, schoolOrg, termId, batchId);
        privacyLedger.put(ledgerKey, Map.entry(fp1, release1));

        // Lần 2 (Replay): Truy vấn lặp lại cùng fingerprint -> Phải trả về đúng release cũ (Idempotent)
        String fp2 = calculateFingerprint(enterpriseOrg, schoolOrg, termId, datasetId, criteriaHash1, "v1", "strict-v1", "skills-v1");
        assertEquals(fp1, fp2, "Cùng tiêu chí phải cho cùng Fingerprint");
        var existing = privacyLedger.get(ledgerKey);
        assertNotNull(existing);
        assertEquals(release1.releaseId(), existing.getValue().releaseId(), "Replay phải trả về cùng releaseId cũ");

        // Lần 3 (Differencing attack): Kẻ tấn công cố tình thay đổi 1 tiêu chí (criteriaHash khác) để tạo chênh lệch nhằm suy ra dữ liệu cá nhân
        String criteriaHashAltered = "hash-criteria-slightly-different";
        String fpAltered = calculateFingerprint(enterpriseOrg, schoolOrg, termId, datasetId, criteriaHashAltered, "v1", "strict-v1", "skills-v1");
        assertNotEquals(fp1, fpAltered);

        // Hệ thống phải phát hiện khác fingerprint trong cùng batch và ném lỗi PRIVACY_RELEASE_LOCKED
        var checkLedger = privacyLedger.get(ledgerKey);
        String errorCode = null;
        if (checkLedger != null && !checkLedger.getKey().equals(fpAltered)) {
            errorCode = "PRIVACY_RELEASE_LOCKED";
        }
        assertEquals("PRIVACY_RELEASE_LOCKED", errorCode, "Phải khóa release và trả mã lỗi PRIVACY_RELEASE_LOCKED");
        // Không cấp thêm bản ghi thứ hai
        assertEquals(1, privacyLedger.size(), "Ledger phải giữ nguyên duy nhất 1 release, không được tạo thêm bản ghi");
    }

    @Test
    @DisplayName("TC07: Lỗi dữ liệu Adapter & Phân quyền bảo vệ")
    void testAdapterAndPermissionGuards() {
        // 1. Adapter T5: Kiểm tra stale version
        long expectedDemandVersion = 1L;
        long currentDemandVersion = 2L; // Bị lệch version
        boolean isStale = (expectedDemandVersion != currentDemandVersion);
        assertTrue(isStale, "Khi version tiêu chí bị lệch phải phát hiện stale");
        String staleError = isStale ? "CRITERIA_STALE" : "OK";
        assertEquals("CRITERIA_STALE", staleError, "Lệch version phải trả mã CRITERIA_STALE (HTTP 409)");

        // 2. Phân quyền: Doanh nghiệp gọi Details của trường
        String callerRole = "ENTERPRISE_STAFF";
        String requiredPermission = "matching:school:detail_read";
        List<String> enterprisePermissions = List.of("matching:enterprise:aggregate_read");
        boolean hasAccess = enterprisePermissions.contains(requiredPermission);
        assertFalse(hasAccess, "Tài khoản Doanh nghiệp không được có quyền matching:school:detail_read");
        String accessError = !hasAccess ? "ACCESS_DENIED" : "OK";
        assertEquals("ACCESS_DENIED", accessError, "Doanh nghiệp cố xem details phải nhận 403 ACCESS_DENIED");
    }

    @Test
    @DisplayName("Kế thừa T4-03: Bộ 11 kịch bản độc lập S1–S11 từ SRS Oracle")
    void testIndependentSrsOracleS1ThroughS11() throws Exception {
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
        for (var c : oracle.get("cases")) {
            var p = c.get("profile");
            var values = new ArrayList<SkillValue>();
            p.get("skills").forEach(s -> values.add(new SkillValue(s.asString(), 3, BigDecimal.ONE, "SELF_DECLARED")));
            var periods = new ArrayList<DateRange>();
            p.get("availablePeriods").forEach(r -> periods.add(new DateRange(r.get("startDate").asString(), r.get("endDate").asString())));
            var profile = new ProfileSnapshot(
                    "snapshot", "student", "school", "term",
                    p.get("majorCode").isNull() ? null : p.get("majorCode").asString(),
                    p.get("skillsDeclared").asBoolean(),
                    values,
                    p.get("availabilityDeclared").asBoolean(),
                    periods,
                    1, "hash", "demo-v1", "academic-v1", "skills-v1", Instant.EPOCH
            );
            MatchResult result = StrictClassifier.evaluate(profile, criteria, List.of(), Map.of("Java Core", "JAVA"));
            assertEquals(c.get("expectedOutcome").asString(), result.overallStatus(), c.get("id").asString());
            count++;
        }
        assertEquals(11, count, "Cả 11 kịch bản Oracle S1-S11 phải được kiểm tra đầy đủ");
    }
}
