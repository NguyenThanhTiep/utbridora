package vn.utbridora.auth.modules.cooperation.persistence;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.modules.cooperation.api.*;
import vn.utbridora.auth.modules.cooperation.api.CooperationDtos.VerificationView;

@Repository
public class VerificationTaxCheckStore {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    public record Input(String registrationCode, String registeredName, String address) {
        public static Input from(VerificationView view) { return new Input(view.data().registrationCode(), view.data().registeredName(), view.data().address()); }
    }
    public record Job(String id, String leaseToken, int attemptCount, Input input) {}
    public VerificationTaxCheckStore(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.transactions = new TransactionTemplate(manager);
    }
    /** Joins the submit transaction; a failed command cannot leave a runnable job. */
    public void enqueue(VerificationView view) {
        if (view.submissionNo() < 1) throw new IllegalArgumentException("Submission required");
        jdbc.update("""
            INSERT INTO verification_tax_checks(id,verification_id,submission_no,input_json,state,attempt_count,next_attempt_at,created_at)
            VALUES (?,?,?,?,'PENDING',0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE id=id
            """, UUID.randomUUID().toString(), view.verificationId(), view.submissionNo(), JSON.writeValueAsString(Input.from(view)));
    }
    public VerificationTaxCheckView read(VerificationView view) {
        return jdbc.queryForObject("SELECT * FROM verification_tax_checks WHERE verification_id=? AND submission_no=?", (rs, n) -> {
            if (!Input.from(view).equals(JSON.readValue(rs.getString("input_json"), Input.class)))
                throw new IllegalStateException("Assessment submission mismatch");
            String result = rs.getString("result_json");
            String state = rs.getString("state");
            return new VerificationTaxCheckView(view.verificationId(), view.submissionNo(), state, rs.getInt("attempt_count"),
                "PENDING".equals(state) ? instant(rs, "next_attempt_at") : null, instant(rs, "completed_at"),
                result == null ? null : JSON.readValue(result, TaxAssessmentView.class));
        }, view.verificationId(), view.submissionNo());
    }
    /** Repeated clicks join pending work. Completed work has a short cooldown. */
    public void refresh(VerificationView view) {
        enqueue(view);
        jdbc.update("""
            UPDATE verification_tax_checks SET state='PENDING',attempt_count=0,next_attempt_at=UTC_TIMESTAMP(6),
              lease_token=NULL,lease_until=NULL,result_json=NULL,completed_at=NULL
            WHERE verification_id=? AND submission_no=? AND state IN ('COMPLETED','FAILED')
              AND completed_at<=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 30 SECOND)
            """, view.verificationId(), view.submissionNo());
    }
    public Optional<Job> claim() {
        return transactions.execute(status -> {
            // A crash on the final attempt also becomes terminal rather than staying PROCESSING forever.
            jdbc.update("""
                UPDATE verification_tax_checks SET state='FAILED',lease_token=NULL,lease_until=NULL,completed_at=UTC_TIMESTAMP(6)
                WHERE state='PROCESSING' AND lease_until<UTC_TIMESTAMP(6) AND attempt_count>=4
                """);
            var jobs = jdbc.query("""
                SELECT id,input_json,attempt_count FROM verification_tax_checks
                WHERE attempt_count<4 AND ((state='PENDING' AND next_attempt_at<=UTC_TIMESTAMP(6))
                  OR (state='PROCESSING' AND lease_until<UTC_TIMESTAMP(6)))
                ORDER BY next_attempt_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (rs, n) -> new Job(rs.getString("id"), UUID.randomUUID().toString(), rs.getInt("attempt_count") + 1,
                    JSON.readValue(rs.getString("input_json"), Input.class)));
            if (jobs.isEmpty()) return Optional.empty();
            Job job = jobs.getFirst();
            jdbc.update("""
                UPDATE verification_tax_checks SET state='PROCESSING',attempt_count=?,lease_token=?,
                  lease_until=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 120 SECOND) WHERE id=?
                """, job.attemptCount(), job.leaseToken(), job.id());
            return Optional.of(job);
        });
    }
    public void finish(Job job, TaxAssessmentView assessment) {
        boolean unavailable = assessment.lookupStatus() == TaxAssessmentView.LookupStatus.UNAVAILABLE;
        boolean retry = unavailable && job.attemptCount() < 4;
        int delay = new int[]{30, 120, 600, 600}[Math.min(3, job.attemptCount() - 1)];
        jdbc.update("""
            UPDATE verification_tax_checks SET state=?,result_json=?,next_attempt_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL ? SECOND),
              completed_at=IF(?,NULL,UTC_TIMESTAMP(6)),lease_token=NULL,lease_until=NULL
            WHERE id=? AND state='PROCESSING' AND lease_token=?
            """, retry ? "PENDING" : unavailable ? "FAILED" : "COMPLETED", JSON.writeValueAsString(assessment), delay,
            retry, job.id(), job.leaseToken());
    }
    private static Instant instant(ResultSet rs, String field) throws SQLException {
        Timestamp value = rs.getTimestamp(field, Calendar.getInstance(TimeZone.getTimeZone("UTC")));
        return value == null ? null : value.toInstant();
    }
}
