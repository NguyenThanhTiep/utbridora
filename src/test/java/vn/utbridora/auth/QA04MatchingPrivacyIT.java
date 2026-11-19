package vn.utbridora.auth;

import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.modules.cooperation.application.*;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.*;
import vn.utbridora.auth.modules.matching.application.MatchingService;
import vn.utbridora.auth.modules.matching.domain.StrictClassifier;
import vn.utbridora.auth.modules.student.api.StudentProfileDtos;
import vn.utbridora.auth.modules.student.application.*;
import vn.utbridora.auth.modules.student.port.StudentProfileSnapshotPort;
import static vn.utbridora.auth.operations.WorkflowValues.JSON;

/**
 * QA04 - Bộ Integration Test Độc Lập (Real Spring Boot + MySQL 8.4 + HTTP Client)
 * Bao phủ đầy đủ T4-03, T4-04, T4-05:
 * - Fixture 0 / 1 / 4 / 5 / 10 / 20 sinh viên thật (k=5, SUPPRESSED, 5-9, 10-19, 20+)
 * - Chống suy luận chênh lệch 10/10/10 -> 9/10/11 qua đổi bộ lọc / actor / demand (HTTP 409 PRIVACY_RELEASE_LOCKED)
 * - Chống bypass qua JSON (/summary), Export CSV (/summary.csv), Chart (/summary.chart) và kill-switch (503)
 * - Zero PII (không rò rỉ họ tên, email, MSSV, studentUserId, rawCount, tổng, %)
 * - Lỗi Adapter T5 (CRITERIA_STALE 409) và thiếu active batch (503)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.demo.local.enabled=true",
        "DEMO_PASSWORD=Synthetic-qa04-test-password-123",
        "app.bootstrap.admin.email=admin@example.test",
        "app.bootstrap.admin.password=Synthetic-qa04-admin-password-456",
        "app.t5.placement-delivery.enabled=false",
        "logging.level.org.springframework.jdbc=WARN"
})
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QA04MatchingPrivacyIT {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4").withDatabaseName("utbridora");
    static final Path FILE_ROOT = createTempDir();

    static Path createTempDir() {
        try {
            return Files.createTempDirectory("utbridora-qa04-privacy-");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.files.storage-root", FILE_ROOT::toString);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired AuthorizationPolicy core;
    @Autowired SchoolDelegationService delegations;
    @Autowired StudentProfileService profiles;
    @Autowired StudentEligibilityService eligibility;
    @Autowired DemandService demands;
    @Autowired CooperationService cooperations;
    @Autowired StudentProfileSnapshotPort snapshots;

    @LocalServerPort int port;

    record Session(HttpClient client, String csrfHeader, String csrfToken) {}

    DemandInput baseInput;
    StudentProfileSnapshotPort.ProfileSnapshot seedProfile;
    Session hr, coordinator, school, student;

    static String id(int n) {
        return "00000000-0000-4000-8000-" + String.format("%012d", n);
    }

    static String key() {
        return UUID.randomUUID().toString();
    }

    Authentication as(int n) {
        var auth = core.currentSubject(id(n)).orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(auth);
        return auth;
    }

    @BeforeAll
    void setupActorsAndSeedProfile() throws Exception {
        var admin = core.currentSubject(
                jdbc.queryForObject("SELECT id FROM users WHERE email='admin@example.test'", String.class)
        ).orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(admin);

        delegations.bootstrap(admin, key(), new SchoolDelegationService.Bootstrap(
                id(2), id(11), id(21),
                List.of("matching:run:execute", "matching:school:detail_read", "matching:school:nominate",
                        "cooperation:school:review", "student:eligibility:school:approve"),
                List.of(),
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(86400),
                "DEMO-QA04-PRIVACY"
        ));

        baseInput = demands.list(as(4), id(12), null, null, false, 0, 25).content().getFirst().data();
        var current = profiles.read(as(6), id(21));
        var old = current.profile();
        var range = baseInput.required().dateRange();
        var skills = baseInput.required().skills().stream()
                .map(s -> new StudentProfileDtos.CanonicalSkillInput(s.skillCode(), 5, new BigDecimal("10"), null))
                .toList();
        var update = new StudentProfileDtos.ProfileInput(
                current.profileVersion(), id(21), old.majorCode(), true, skills, List.of(), true,
                List.of(new StudentProfileDtos.DateRange(range.startDate().toString(), range.endDate().toString())),
                old.orientationCodes(), old.subjectCodes(), old.projects(), old.certificates(), old.cvFileId(), old.phone()
        );
        profiles.save(as(6), key(), JSON.writeValueAsString(update));
        eligibility.decide(as(2), key(), new StudentEligibilityService.EligibilityDecision(
                id(6), id(21), 1L, true, "ELIGIBLE", "DEMO-QA04-ELIGIBILITY"));

        var captured = snapshots.captureForMatching(as(2), id(21), baseInput.academicCatalogVersion(), baseInput.taxonomyVersion(), key());
        seedProfile = snapshots.readPage(as(2), captured.datasetSnapshotId(), null, 100).items().stream()
                .filter(p -> p.studentUserId().equals(id(6)))
                .findFirst()
                .orElseThrow();

        hr = login("hr@example.test");
        coordinator = login("coordinator@example.test");
        school = login("school@example.test");
        student = login("student@example.test");
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void resetGate() {
        jdbc.update("UPDATE matching_privacy_release_settings SET enabled=false WHERE id=1");
        SecurityContextHolder.clearContext();
    }

    private DemandDto createReadyApprovedDemand(DemandInput inputData) {
        var draft = demands.create(as(4), id(12), key(), inputData);
        var submitted = cooperations.submitWithCooperation(as(4), id(12), key(),
                new CooperationService.Submit(draft.demandId(), draft.version()));
        cooperations.decide(as(2), id(11), submitted.cooperation().cooperationId(), key(),
                new CooperationService.Decision(2L, "APPROVE", null, "DEMO-QA04-APPROVE", List.of()));
        return demands.list(as(4), id(12), null, null, false, 0, 100).content().stream()
                .filter(d -> d.demandId().equals(draft.demandId()))
                .findFirst()
                .orElseThrow();
    }

    private void activateBatch(String batchId, String datasetId) {
        jdbc.update("INSERT INTO matching_demo_batches VALUES(?,?,?,?,UTC_TIMESTAMP(6))",
                batchId, id(11), id(21), datasetId);
        jdbc.update("INSERT INTO matching_active_demo_batches VALUES(?,?,?) ON DUPLICATE KEY UPDATE demo_batch_id=VALUES(demo_batch_id)",
                id(11), id(21), batchId);
    }

    @Test
    @DisplayName("TC-QA04-IT01 [T4-03/T4-04]: Fixture 0/1/4/5/10/20 sinh viên thật, suppression k=5, Zero PII trên JSON / CSV / Chart")
    void verifiesKAnonymityBandsAndZeroPiiAcrossJsonCsvAndChartForFixtures0_1_4_5_10_20() throws Exception {
        var demand = createReadyApprovedDemand(baseInput);
        jdbc.update("UPDATE matching_privacy_release_settings SET enabled=true WHERE id=1");

        Map<Integer, String> cases = new LinkedHashMap<>();
        cases.put(0, "SUPPRESSED");
        cases.put(1, "SUPPRESSED");
        cases.put(4, "SUPPRESSED");
        cases.put(5, "5-9");
        cases.put(10, "10-19");
        cases.put(20, "20+");

        for (var entry : cases.entrySet()) {
            int cohortSize = entry.getKey();
            String expectedBand = entry.getValue();
            String datasetId = key(), batchId = key();
            var snapshotSet = new StudentProfileSnapshotPort.SnapshotSet(
                    datasetId, id(11), id(21), "qa04-k5-fixture-" + cohortSize,
                    baseInput.academicCatalogVersion(), baseInput.taxonomyVersion(), Instant.now(), cohortSize
            );
            jdbc.update("INSERT INTO student_snapshot_sets VALUES(?,?,?,?)",
                    datasetId, id(11), id(21), JSON.writeValueAsString(snapshotSet));

            List<String> createdEmails = new ArrayList<>();
            List<String> createdNames = new ArrayList<>();
            List<String> createdUserIds = new ArrayList<>();

            for (int i = 0; i < cohortSize; i++) {
                String userId = key(), snapId = key();
                String studentName = "Nguyen Van QA04 " + cohortSize + "_" + i;
                String studentEmail = "qa04.sv." + cohortSize + "." + i + "." + userId.substring(0, 6) + "@example.test";
                createdNames.add(studentName);
                createdEmails.add(studentEmail);
                createdUserIds.add(userId);

                jdbc.update("INSERT INTO users(id,email,password_hash,display_name,status,created_at,updated_at) " +
                                "SELECT ?,?,password_hash,?,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6) FROM users WHERE id=?",
                        userId, studentEmail, studentName, id(6));
                var snap = new StudentProfileSnapshotPort.ProfileSnapshot(
                        snapId, userId, id(11), id(21),
                        seedProfile.majorCode(), seedProfile.skillsDeclared(), seedProfile.skills(),
                        seedProfile.availabilityDeclared(), seedProfile.availablePeriods(),
                        seedProfile.profileVersion(), "qa04-hash-" + cohortSize + "-" + i,
                        seedProfile.completenessVersion(), seedProfile.academicCatalogVersion(),
                        seedProfile.taxonomyVersion(), Instant.now()
                );
                jdbc.update("INSERT INTO student_profile_snapshots VALUES(?,?,?,?)",
                        snapId, datasetId, userId, JSON.writeValueAsString(snap));
            }
            activateBatch(batchId, datasetId);

            var evaluateReq = new MatchingService.Evaluate(demand.demandId(), demand.version(), demand.criteriaVersion());
            var evalResp = http(hr, "POST", "/matching/evaluate", JSON.writeValueAsString(evaluateReq), id(12), key(), true);
            assertEquals(200, evalResp.statusCode(), "Fixture=" + cohortSize + " body=" + evalResp.body());

            var evaluated = JSON.readValue(evalResp.body(), MatchingService.Evaluated.class);
            assertEquals("ENTERPRISE", evaluated.audience());
            assertNull(evaluated.summary(), "Doanh nghiệp không được nhận trường summary chứa số đếm chi tiết");
            assertNotNull(evaluated.release());
            assertEquals(expectedBand, evaluated.release().eligibleBand(), "Sai băng bảo vệ cho nhóm " + cohortSize + " SV");

            var evalTree = JSON.readTree(evalResp.body());
            assertEquals(Set.of("audience", "release"), new HashSet<>(evalTree.propertyNames()));
            assertEquals(Set.of("releaseId", "matchRunId", "eligibleBand"), new HashSet<>(evalTree.get("release").propertyNames()));

            // Kiểm tra cả 3 đường dẫn biểu diễn: JSON (/summary), Export CSV (/summary.csv), Chart (/summary.chart)
            String runId = evaluated.release().matchRunId();
            var summaryResp = http(hr, "GET", "/matching/runs/" + runId + "/summary", null, id(12), null, false);
            var csvResp = http(hr, "GET", "/matching/runs/" + runId + "/summary.csv", null, id(12), null, false);
            var chartResp = http(hr, "GET", "/matching/runs/" + runId + "/summary.chart", null, id(12), null, false);

            assertEquals(200, summaryResp.statusCode(), summaryResp.body());
            assertEquals(200, csvResp.statusCode(), csvResp.body());
            assertEquals(200, chartResp.statusCode(), chartResp.body());

            // Kiểm tra cấu trúc CSV (chỉ 2 dòng, 3 cột, không có rawCount/tổng/%)
            List<String> csvLines = csvResp.body().lines().toList();
            assertEquals(2, csvLines.size(), "CSV chỉ được có header và 1 dòng release");
            assertEquals("releaseId,matchRunId,eligibleBand", csvLines.get(0));
            assertEquals(evaluated.release().releaseId() + "," + runId + "," + expectedBand, csvLines.get(1));

            // Kiểm tra cấu trúc Chart
            var chartTree = JSON.readTree(chartResp.body());
            assertEquals(Set.of("eligibleBands"), new HashSet<>(chartTree.propertyNames()));
            assertEquals(expectedBand, chartTree.get("eligibleBands").get(0).asString());

            // Quét chống rò rỉ PII và số liệu nhạy cảm trên mọi phản hồi
            for (String body : List.of(evalResp.body(), summaryResp.body(), csvResp.body(), chartResp.body())) {
                assertFalse(body.contains("@"), "Không được rò rỉ ký tự email @: " + body);
                assertFalse(body.contains("%"), "Không được rò rỉ phần trăm %: " + body);
                for (String forbidden : List.of("rawCount", "total", "percent", "percentage", "profileCount",
                        "matched", "missingData", "notMatched", "fingerprint", "studentUserId", "Nguyen Van QA04")) {
                    assertFalse(body.contains(forbidden), "Phản hồi lộ trường/dữ liệu cấm '" + forbidden + "': " + body);
                }
                for (String email : createdEmails) assertFalse(body.contains(email));
                for (String name : createdNames) assertFalse(body.contains(name));
                for (String uid : createdUserIds) assertFalse(body.contains(uid));
            }
        }
    }

    @Test
    @DisplayName("TC-QA04-IT02 [T4-05]: Hai truy vấn 10/10/10 và 9/10/11 (đổi bộ lọc / actor / demand) -> replay 200 & differencing HTTP 409 không lộ tổng/%")
    void blocksDifferencingAttack10_10_10Versus9_10_11AcrossActorsAndDemandsWithHttp409() throws Exception {
        var baseDemand = createReadyApprovedDemand(baseInput);
        jdbc.update("UPDATE matching_privacy_release_settings SET enabled=true WHERE id=1");

        var baseRequired = baseInput.required();
        var firstSkill = baseRequired.skills().getFirst();
        BigDecimal baseYears = firstSkill.minYearsPractice() == null ? BigDecimal.ZERO : firstSkill.minYearsPractice();
        BigDecimal raisedYears = baseYears.add(new BigDecimal("0.5"));

        // Tạo 30 hồ sơ sinh viên thật với phân bố chuẩn 10 MATCH / 10 MISSING_DATA / 10 NOT_MATCH
        String datasetId = key(), batchId = key();
        var snapshotSet = new StudentProfileSnapshotPort.SnapshotSet(
                datasetId, id(11), id(21), "qa04-diff-10-10-10",
                baseInput.academicCatalogVersion(), baseInput.taxonomyVersion(), Instant.now(), 30
        );
        jdbc.update("INSERT INTO student_snapshot_sets VALUES(?,?,?,?)",
                datasetId, id(11), id(21), JSON.writeValueAsString(snapshotSet));

        var builtProfiles = new ArrayList<StudentProfileSnapshotPort.ProfileSnapshot>();
        for (int i = 0; i < 30; i++) {
            String userId = key(), snapId = key();
            jdbc.update("INSERT INTO users(id,email,password_hash,display_name,status,created_at,updated_at) " +
                            "SELECT ?,?,password_hash,?,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6) FROM users WHERE id=?",
                    userId, "qa04.diff." + i + "." + userId.substring(0, 6) + "@example.test", "QA04 Diff Student " + i, id(6));

            boolean isMissing = (i >= 10 && i < 20);
            boolean isNotMatch = (i >= 20);
            var skills = new ArrayList<>(seedProfile.skills());
            if (i < 10) {
                // SV 0 có đúng baseYears (sẽ rớt từ MATCH -> NOT_MATCH khi tăng ngưỡng thêm 0.5 năm); SV 1..9 vượt ngưỡng rõ
                BigDecimal targetYears = (i == 0) ? baseYears : raisedYears.add(BigDecimal.ONE);
                for (int s = 0; s < skills.size(); s++) {
                    if (skills.get(s).skillCode().equals(firstSkill.skillCode())) {
                        skills.set(s, new StudentProfileSnapshotPort.SkillValue(
                                skills.get(s).skillCode(), skills.get(s).level(), targetYears, skills.get(s).source()));
                        break;
                    }
                }
            }
            var snap = new StudentProfileSnapshotPort.ProfileSnapshot(
                    snapId, userId, id(11), id(21),
                    isNotMatch ? "QA04_WRONG_MAJOR" : seedProfile.majorCode(),
                    !isMissing, isMissing ? List.of() : List.copyOf(skills),
                    true, seedProfile.availablePeriods(),
                    seedProfile.profileVersion(), "qa04-diff-hash-" + i,
                    seedProfile.completenessVersion(), seedProfile.academicCatalogVersion(),
                    seedProfile.taxonomyVersion(), Instant.now()
            );
            builtProfiles.add(snap);
            jdbc.update("INSERT INTO student_profile_snapshots VALUES(?,?,?,?)",
                    snapId, datasetId, userId, JSON.writeValueAsString(snap));
        }
        activateBatch(batchId, datasetId);

        // Truy vấn 1 (10/10/10) bởi HR
        var req1 = new MatchingService.Evaluate(baseDemand.demandId(), baseDemand.version(), baseDemand.criteriaVersion());
        var resp1 = http(hr, "POST", "/matching/evaluate", JSON.writeValueAsString(req1), id(12), key(), true);
        assertEquals(200, resp1.statusCode(), resp1.body());
        var release1 = JSON.readValue(resp1.body(), MatchingService.Evaluated.class).release();
        assertEquals("10-19", release1.eligibleBand());

        // Xác minh phân bố DB của lần chạy 1 đúng là 10 / 10 / 10
        Map<String, Integer> dbCounts1 = jdbc.query(
                "SELECT overall_status, COUNT(*) cnt FROM match_results WHERE run_id=? GROUP BY overall_status",
                (r, n) -> Map.entry(r.getString("overall_status"), r.getInt("cnt")),
                release1.matchRunId()
        ).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertEquals(Map.of("MATCH", 10, "MISSING_DATA", 10, "NOT_MATCH", 10), dbCounts1);

        int runsAfterFirst = jdbc.queryForObject("SELECT COUNT(*) FROM match_runs WHERE demand_id=?", Integer.class, baseDemand.demandId());

        // Replay 1: Đổi actor (coordinator) + Idempotency-Key mới -> phải phát lại nguyên release cũ
        var replayActorResp = http(coordinator, "POST", "/matching/evaluate", JSON.writeValueAsString(req1), id(12), key(), true);
        assertEquals(200, replayActorResp.statusCode(), replayActorResp.body());
        assertEquals(release1, JSON.readValue(replayActorResp.body(), MatchingService.Evaluated.class).release());
        assertEquals(runsAfterFirst, jdbc.queryForObject("SELECT COUNT(*) FROM match_runs WHERE demand_id=?", Integer.class, baseDemand.demandId()));

        // Replay 2: Đổi demand khác nhưng cùng bộ tiêu chí -> cùng fingerprint, vẫn phát lại nguyên release cũ
        var cloneDemandInput = new DemandInput(
                baseInput.schoolOrgId(), baseInput.termId(), "QA04 Replay Same Criteria " + key(),
                baseInput.quantity(), baseInput.description(), baseInput.location(), baseInput.benefits(),
                baseInput.deadline(), baseInput.required(), baseInput.preferred(),
                baseInput.academicCatalogVersion(), baseInput.taxonomyVersion()
        );
        var sameCriteriaDemand = demands.create(as(4), id(12), key(), cloneDemandInput);
        var replayDemandResp = http(coordinator, "POST", "/matching/evaluate",
                JSON.writeValueAsString(new MatchingService.Evaluate(sameCriteriaDemand.demandId(), sameCriteriaDemand.version(), sameCriteriaDemand.criteriaVersion())),
                id(12), key(), true);
        assertEquals(200, replayDemandResp.statusCode(), replayDemandResp.body());
        assertEquals(release1, JSON.readValue(replayDemandResp.body(), MatchingService.Evaluated.class).release());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM matching_privacy_releases WHERE demo_batch_id=?", Integer.class, batchId));

        // Chuẩn bị truy vấn 2 (9/10/11): tăng minYearsPractice thêm 0.5 để tách đúng 1 sinh viên ra khỏi MATCH
        var shiftedSkills = new ArrayList<>(baseRequired.skills());
        shiftedSkills.set(0, new SkillCriterion(firstSkill.skillCode(), firstSkill.minLevel(), raisedYears));
        var shiftedCriteria = new RequiredCriteria(baseRequired.majorCodes(), List.copyOf(shiftedSkills), baseRequired.dateRange());

        // Xác minh bộ phân loại thật cho đúng 9 MATCH / 10 MISSING_DATA / 11 NOT_MATCH
        Map<String, Long> expectedShifted = builtProfiles.stream()
                .map(p -> StrictClassifier.evaluate(p, shiftedCriteria, baseInput.preferred(), Map.of()).overallStatus())
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));
        assertEquals(Map.of("MATCH", 9L, "MISSING_DATA", 10L, "NOT_MATCH", 11L), expectedShifted);

        var probeDemandInput = new DemandInput(
                baseInput.schoolOrgId(), baseInput.termId(), "QA04 Differencing Probe 9-10-11 " + key(),
                baseInput.quantity(), baseInput.description(), baseInput.location(), baseInput.benefits(),
                baseInput.deadline(), shiftedCriteria, baseInput.preferred(),
                baseInput.academicCatalogVersion(), baseInput.taxonomyVersion()
        );
        var probeDemand = demands.create(as(4), id(12), key(), probeDemandInput);
        var probeReqJson = JSON.writeValueAsString(new MatchingService.Evaluate(probeDemand.demandId(), probeDemand.version(), probeDemand.criteriaVersion()));

        // Cả HR và Coordinator thử gọi truy vấn 9/10/11 trong cùng batch -> ĐỀU PHẢI BỊ CHẶN HTTP 409
        Set<String> forbiddenErrorKeys = Set.of(
                "total", "rawCount", "count", "matched", "missingData", "notMatched",
                "percent", "percentage", "difference", "fingerprint", "criteriaHash", "eligibleCount",
                "eligibleBand", "email", "studentUserId"
        );
        for (Session attacker : List.of(hr, coordinator)) {
            var blockedResp = http(attacker, "POST", "/matching/evaluate", probeReqJson, id(12), key(), true);
            assertEquals(409, blockedResp.statusCode(), "Khác fingerprint trong cùng batch bắt buộc trả 409: " + blockedResp.body());
            assertTrue(blockedResp.body().contains("PRIVACY_RELEASE_LOCKED"), blockedResp.body());
            assertFalse(blockedResp.body().contains("%"), "Lỗi 409 không được lộ phần trăm %: " + blockedResp.body());
            assertFalse(blockedResp.body().contains("5-9"), "Lỗi 409 không được lộ băng mới: " + blockedResp.body());
            assertFalse(blockedResp.body().contains("10-19"), "Lỗi 409 không được lộ băng cũ: " + blockedResp.body());

            var keys = new HashSet<String>();
            collectKeys(JSON.readTree(blockedResp.body()), keys);
            assertTrue(Collections.disjoint(forbiddenErrorKeys, keys), "Body lỗi 409 rò rỉ khóa nhạy cảm: " + keys);
        }

        // Ledger vẫn giữ duy nhất 1 release, không sinh thêm match_runs cho probeDemand, và ghi audit DENIED
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM matching_privacy_releases WHERE demo_batch_id=?", Integer.class, batchId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM match_runs WHERE demand_id=?", Integer.class, probeDemand.demandId()));
        var auditRow = jdbc.queryForMap(
                "SELECT result, metadata_json FROM audit_logs WHERE action='PRIVACY_RELEASE_BLOCKED' ORDER BY created_at DESC LIMIT 1");
        assertEquals("DENIED", auditRow.get("result"));
        var meta = JSON.readTree((String) auditRow.get("metadata_json"));
        assertEquals(Set.of("enterpriseOrgId", "schoolOrgId", "termId", "demoBatchId", "reasonCode"),
                new HashSet<>(meta.propertyNames()), "Metadata audit từ chối không được chứa số đếm hay band");
        assertEquals("PRIVACY_RELEASE_LOCKED", meta.get("reasonCode").asString());
    }

    @Test
    @DisplayName("TC-QA04-IT03 [T4-04/T4-05]: Negative/Security - Chống bypass Export/Chart, phân quyền, ẩn danh, kill-switch 503 & lỗi Adapter")
    void enforcesAccessControlsExportChartNonBypassKillSwitchAndAdapterGuards() throws Exception {
        var demand = createReadyApprovedDemand(baseInput);

        // 1. Mặc định đóng (enabled = false) -> doanh nghiệp nhận 503 PRIVACY_RELEASE_UNAVAILABLE
        jdbc.update("UPDATE matching_privacy_release_settings SET enabled=false WHERE id=1");
        var evalReq = new MatchingService.Evaluate(demand.demandId(), demand.version(), demand.criteriaVersion());
        var closedResp = http(hr, "POST", "/matching/evaluate", JSON.writeValueAsString(evalReq), id(12), key(), true);
        assertEquals(503, closedResp.statusCode(), closedResp.body());
        assertTrue(closedResp.body().contains("PRIVACY_RELEASE_UNAVAILABLE"), closedResp.body());

        // Bật cổng + tạo batch 5 SV để lấy release hợp lệ
        jdbc.update("UPDATE matching_privacy_release_settings SET enabled=true WHERE id=1");
        String datasetId = key(), batchId = key();
        var set = new StudentProfileSnapshotPort.SnapshotSet(
                datasetId, id(11), id(21), "qa04-sec-batch",
                baseInput.academicCatalogVersion(), baseInput.taxonomyVersion(), Instant.now(), 5);
        jdbc.update("INSERT INTO student_snapshot_sets VALUES(?,?,?,?)", datasetId, id(11), id(21), JSON.writeValueAsString(set));
        for (int i = 0; i < 5; i++) {
            String uid = key(), sid = key();
            jdbc.update("INSERT INTO users(id,email,password_hash,display_name,status,created_at,updated_at) " +
                            "SELECT ?,?,password_hash,'QA04 Sec SV','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6) FROM users WHERE id=?",
                    uid, "qa04.sec." + i + "." + uid.substring(0, 6) + "@example.test", id(6));
            var snap = new StudentProfileSnapshotPort.ProfileSnapshot(
                    sid, uid, id(11), id(21), seedProfile.majorCode(), seedProfile.skillsDeclared(), seedProfile.skills(),
                    seedProfile.availabilityDeclared(), seedProfile.availablePeriods(), seedProfile.profileVersion(),
                    "qa04-sec-" + i, seedProfile.completenessVersion(), seedProfile.academicCatalogVersion(),
                    seedProfile.taxonomyVersion(), Instant.now());
            jdbc.update("INSERT INTO student_profile_snapshots VALUES(?,?,?,?)", sid, datasetId, uid, JSON.writeValueAsString(snap));
        }
        activateBatch(batchId, datasetId);

        var okResp = http(hr, "POST", "/matching/evaluate", JSON.writeValueAsString(evalReq), id(12), key(), true);
        assertEquals(200, okResp.statusCode(), okResp.body());
        var release = JSON.readValue(okResp.body(), MatchingService.Evaluated.class).release();
        String runId = release.matchRunId();

        // 2. Doanh nghiệp cố gọi chi tiết sinh viên (/details) -> bị chặn 403 ACCESS_DENIED
        var detailsDenied = http(hr, "GET", "/matching/runs/" + runId + "/details?overallStatus=MATCH", null, id(12), null, false);
        assertEquals(403, detailsDenied.statusCode(), detailsDenied.body());

        // 3. Lỗi Adapter T5: gửi expectedDemandVersion sai/cũ -> HTTP 409 CRITERIA_STALE
        var staleReq = new MatchingService.Evaluate(demand.demandId(), demand.version() + 99, demand.criteriaVersion());
        var staleResp = http(hr, "POST", "/matching/evaluate", JSON.writeValueAsString(staleReq), id(12), key(), true);
        assertEquals(409, staleResp.statusCode(), staleResp.body());
        assertTrue(staleResp.body().contains("CRITERIA_STALE"), staleResp.body());

        // 4. Chống bypass trên cả 3 đường dẫn (/summary, /summary.csv, /summary.chart) với Ẩn danh (401), SV (404), Trường (404), DN ngoài phạm vi (404)
        var anonymousClient = HttpClient.newHttpClient();
        String foreignOrg = key(), foreignUser = key(), foreignEmail = "qa04.outsider." + foreignUser.substring(0, 6) + "@example.test";
        jdbc.update("INSERT INTO organizations(id,name,type,status,created_at) VALUES(?,?,'ENTERPRISE','VERIFIED',UTC_TIMESTAMP(6))", foreignOrg, "QA04 Foreign Enterprise");
        jdbc.update("INSERT INTO users(id,email,password_hash,display_name,status,created_at,updated_at) SELECT ?,?,password_hash,'QA04 Outsider','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6) FROM users WHERE id=?", foreignUser, foreignEmail, id(4));
        jdbc.update("INSERT INTO memberships(id,user_id,organization_id,role,active,created_at) VALUES(?,?,?,'ENTERPRISE',true,UTC_TIMESTAMP(6))", key(), foreignUser, foreignOrg);
        try {
            var outsider = login(foreignEmail);
            for (String suffix : List.of("summary", "summary.csv", "summary.chart")) {
                String path = "/matching/runs/" + runId + "/" + suffix;

                var anonResp = anonymousClient.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(401, anonResp.statusCode(), "Ẩn danh phải bị chặn 401 ở " + suffix);

                for (var entry : List.of(
                        new AbstractMap.SimpleEntry<>(student, (String) null),
                        new AbstractMap.SimpleEntry<>(school, id(11)),
                        new AbstractMap.SimpleEntry<>(outsider, foreignOrg)
                )) {
                    var denied = http(entry.getKey(), "GET", path, null, entry.getValue(), null, false);
                    assertEquals(404, denied.statusCode(), "Role ngoài phạm vi phải nhận 404 ở " + suffix + ": " + denied.body());
                    assertFalse(denied.body().contains(release.releaseId()), denied.body());
                    assertFalse(denied.body().contains(release.eligibleBand()), denied.body());
                }
            }
        } finally {
            jdbc.update("DELETE FROM audit_logs WHERE actor_id=?", foreignUser);
            jdbc.update("DELETE FROM memberships WHERE user_id=?", foreignUser);
            jdbc.update("DELETE FROM users WHERE id=?", foreignUser);
            jdbc.update("DELETE FROM organizations WHERE id=?", foreignOrg);
        }

        // 5. Kill-switch: Tắt cổng bảo vệ -> không thể bypass qua summary, summary.csv hay summary.chart (tất cả đều trả 503)
        jdbc.update("UPDATE matching_privacy_release_settings SET enabled=false WHERE id=1");
        for (String suffix : List.of("summary", "summary.csv", "summary.chart")) {
            var blocked = http(hr, "GET", "/matching/runs/" + runId + "/" + suffix, null, id(12), null, false);
            assertEquals(503, blocked.statusCode(), "Khi tắt cổng bảo vệ, " + suffix + " phải trả 503");
        }

        // 6. Thiếu active batch khi bật cổng -> fail-closed 503 PRIVACY_RELEASE_UNAVAILABLE
        jdbc.update("UPDATE matching_privacy_release_settings SET enabled=true WHERE id=1");
        jdbc.update("DELETE FROM matching_active_demo_batches WHERE school_org_id=? AND term_id=?", id(11), id(21));
        var noBatchResp = http(hr, "POST", "/matching/evaluate", JSON.writeValueAsString(evalReq), id(12), key(), true);
        assertEquals(503, noBatchResp.statusCode(), noBatchResp.body());
        assertTrue(noBatchResp.body().contains("PRIVACY_RELEASE_UNAVAILABLE"), noBatchResp.body());
    }

    private static void collectKeys(tools.jackson.databind.JsonNode node, Set<String> out) {
        if (node.isObject()) {
            node.propertyNames().forEach(name -> {
                out.add(name);
                collectKeys(node.get(name), out);
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectKeys(child, out));
        }
    }

    private Session login(String email) throws Exception {
        var client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        var csrfBefore = JSON.readTree(client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        ).body());
        var loginResp = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .header(csrfBefore.get("headerName").asString(), csrfBefore.get("token").asString())
                        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Synthetic-qa04-test-password-123"
                        ))))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, loginResp.statusCode(), loginResp.body());
        var csrfAfter = JSON.readTree(client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        ).body());
        return new Session(client, csrfAfter.get("headerName").asString(), csrfAfter.get("token").asString());
    }

    private HttpResponse<String> http(Session session, String method, String path, String body,
                                      String orgId, String idempotencyKey, boolean withCsrf) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path));
        if (orgId != null) builder.header("X-Organization-Id", orgId);
        if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
        if (withCsrf) builder.header(session.csrfHeader(), session.csrfToken());
        if (body != null) builder.header("Content-Type", "application/json");
        return session.client().send(
                builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }
}
