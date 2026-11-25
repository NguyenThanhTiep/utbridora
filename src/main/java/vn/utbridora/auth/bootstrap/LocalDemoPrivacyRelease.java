package vn.utbridora.auth.bootstrap;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import vn.utbridora.auth.modules.student.api.StudentProfileDtos.DateRange;
import vn.utbridora.auth.modules.student.port.StudentProfileSnapshotPort.*;
import vn.utbridora.auth.operations.OperationLedger;
import vn.utbridora.auth.operations.WorkflowValues;

/** Opt-in local demo setup for T4 protected enterprise aggregate statistics (fail-closed outside local opt-in). */
@Configuration(proxyBeanMethods = false)
@Profile("local")
@ConditionalOnProperty(name = { "app.demo.local.enabled", "app.demo.local.privacy-release-enabled" }, havingValue = "true")
public class LocalDemoPrivacyRelease {
    static final String FIXTURE = "LOCAL_PRIVACY_RELEASE_V1";
    private static final String PREFIX = "00000000-0000-4000-8000-";
    private static final String SCHOOL_ID = PREFIX + "000000000011";
    private static final String TERM_ID = PREFIX + "000000000021";
    private static final String DATASET_ID = PREFIX + "000000000201";
    private static final String BATCH_ID = PREFIX + "000000000202";

    private static String id(int number) {
        return PREFIX + String.format(Locale.ROOT, "%012d", number);
    }

    @Bean
    @Order(115)
    CommandLineRunner seedLocalPrivacyRelease(JdbcTemplate jdbc, TransactionTemplate tx) {
        return args -> tx.executeWithoutResult(status -> {
            if (!"utbridora".equals(jdbc.queryForObject("SELECT DATABASE()", String.class))) return;
            jdbc.queryForObject("SELECT lock_name FROM core_locks WHERE lock_name='GLOBAL_ADMIN' FOR UPDATE", String.class);
            if (jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='local_demo_seed_receipts'",
                    Integer.class) == 0) return;
            if (jdbc.queryForObject("SELECT COUNT(*) FROM local_demo_seed_receipts WHERE fixture_id='LOCAL_SYNTHETIC_V1' AND state='DONE'", Integer.class) != 1) return;
            if (!jdbc.queryForList("SELECT state FROM local_demo_seed_receipts WHERE fixture_id=? FOR UPDATE", String.class, FIXTURE).isEmpty()) return;

            boolean hasActiveBatch = jdbc.queryForObject(
                "SELECT COUNT(*) FROM matching_active_demo_batches WHERE school_org_id=? AND term_id=?",
                Integer.class, SCHOOL_ID, TERM_ID) > 0;
            if (!hasActiveBatch) {
                var termRows = jdbc.queryForList(
                    "SELECT DATE_FORMAT(start_date,'%Y-%m-%d') AS start_date, DATE_FORMAT(end_date,'%Y-%m-%d') AS end_date FROM terms WHERE id=? AND school_org_id=?",
                    TERM_ID, SCHOOL_ID);
                var catalogRows = jdbc.queryForList("SELECT catalog_version FROM academic_catalogs WHERE term_id=? AND school_org_id=?", String.class, TERM_ID, SCHOOL_ID);
                var seedHashRows = jdbc.queryForList("SELECT password_hash FROM users WHERE id=?", String.class, id(6));
                if (termRows.isEmpty() || catalogRows.isEmpty() || seedHashRows.isEmpty()) return;

                String startDate = String.valueOf(termRows.getFirst().get("start_date"));
                String endDate = String.valueOf(termRows.getFirst().get("end_date"));
                String academicVersion = catalogRows.getFirst();
                String taxonomyVersion = "skills-v" + jdbc.queryForObject("SELECT taxonomy_version FROM taxonomy_catalog_state WHERE id=1", Long.class);
                String placeholderHash = seedHashRows.getFirst();
                Instant now = Instant.now();

                var snapshots = new ArrayList<ProfileSnapshot>();
                // 6 matching cohort snapshots + 1 existing non-matching profile -> eligible count = 6 ("5-9")
                for (int index = 1; index <= 6; index++) {
                    String userId = id(100 + index);
                    String email = "privacy-cohort-" + index + "@example.test";
                    if (jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=? OR email=?", Integer.class, userId, email) == 0) {
                        jdbc.update(
                            "INSERT INTO users(id,email,password_hash,display_name,status,security_version,failed_attempts,created_at,updated_at) " +
                            "VALUES(?,?,?,?,'DEACTIVATED',0,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                            userId, email, placeholderHash, "DEMO Privacy Cohort " + index);
                    }
                    if (jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=?", Integer.class, userId) != 1) continue;
                    snapshots.add(snapshot(
                        id(300 + index), userId, academicVersion, taxonomyVersion, now,
                        "SE", true, List.of(new SkillValue("JAVA", 3, BigDecimal.valueOf(2), "SELF_DECLARED")),
                        true, List.of(new DateRange(startDate, endDate))));
                }
                snapshots.add(snapshot(
                    id(307), id(6), academicVersion, taxonomyVersion, now,
                    "SE", false, List.of(),
                    true, List.of(new DateRange(startDate, endDate))));

                if (jdbc.queryForObject("SELECT COUNT(*) FROM student_snapshot_sets WHERE id=?", Integer.class, DATASET_ID) == 0) {
                    String setHash = OperationLedger.payloadHash(snapshots.stream()
                        .map(p -> List.of(p.studentUserId(), p.profileVersion(), p.relevantHash())).toList());
                    var snapshotSet = new SnapshotSet(DATASET_ID, SCHOOL_ID, TERM_ID, setHash, academicVersion, taxonomyVersion, now, snapshots.size());
                    jdbc.update("INSERT INTO student_snapshot_sets(id,school_org_id,term_id,data_json) VALUES(?,?,?,?)",
                        DATASET_ID, SCHOOL_ID, TERM_ID, WorkflowValues.JSON.writeValueAsString(snapshotSet));
                    for (var profile : snapshots) {
                        jdbc.update("INSERT INTO student_profile_snapshots(id,dataset_id,user_id,data_json) VALUES(?,?,?,?)",
                            profile.profileSnapshotId(), DATASET_ID, profile.studentUserId(), WorkflowValues.JSON.writeValueAsString(profile));
                    }
                }
                if (jdbc.queryForObject("SELECT COUNT(*) FROM matching_demo_batches WHERE demo_batch_id=?", Integer.class, BATCH_ID) == 0) {
                    jdbc.update("INSERT INTO matching_demo_batches(demo_batch_id,school_org_id,term_id,dataset_snapshot_id,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6))",
                        BATCH_ID, SCHOOL_ID, TERM_ID, DATASET_ID);
                }
                jdbc.update("INSERT INTO matching_active_demo_batches(school_org_id,term_id,demo_batch_id) VALUES(?,?,?)",
                    SCHOOL_ID, TERM_ID, BATCH_ID);
            }

            jdbc.update("UPDATE matching_privacy_release_settings SET enabled=true WHERE id=1");
            jdbc.update("INSERT INTO local_demo_seed_receipts VALUES (?,'DONE',UTC_TIMESTAMP(6))", FIXTURE);
        });
    }

    private static ProfileSnapshot snapshot(String snapshotId, String userId, String academicVersion, String taxonomyVersion,
            Instant now, String major, boolean skillsDeclared, List<SkillValue> skills, boolean availabilityDeclared, List<DateRange> periods) {
        var relevant = new TreeMap<String, Object>();
        relevant.put("majorCode", major);
        relevant.put("termId", TERM_ID);
        relevant.put("skillsDeclared", skillsDeclared);
        relevant.put("skills", skills);
        relevant.put("availabilityDeclared", availabilityDeclared);
        relevant.put("availablePeriods", periods);
        return new ProfileSnapshot(
            snapshotId, userId, SCHOOL_ID, TERM_ID, major,
            skillsDeclared, skills, availabilityDeclared, periods,
            1L, OperationLedger.payloadHash(relevant), "demo-v1", academicVersion, taxonomyVersion, now);
    }
}
