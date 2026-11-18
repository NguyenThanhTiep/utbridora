package vn.utbridora.auth.files;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.audit.AuditService;
import static vn.utbridora.auth.files.FileScanner.*;

/** Durable claims and fenced completions. No database lock is held during scanner I/O. */
@Service
public class FileScanService {
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private Clock clock=Clock.systemUTC();
    public FileScanService(JdbcTemplate jdbc,AuditService audit) {this.jdbc=jdbc;this.audit=audit;}
    public record Job(String fileId,String leaseId,int attempt,String storageKey,long size,String sha256,String name,String type,String resourceType,String purpose) {}
    private static LocalDateTime utc(Instant value) {return LocalDateTime.ofInstant(value,ZoneOffset.UTC);}
    private Instant now() {return clock.instant().truncatedTo(ChronoUnit.MICROS);}
    @Transactional
    public Job claim() {
        Instant now=now();
        var rows=jdbc.queryForList("SELECT * FROM files WHERE scan_status='PENDING' AND deleted_at IS NULL AND (scan_next_attempt_at IS NULL OR scan_next_attempt_at<=?) AND (scan_lease_until IS NULL OR scan_lease_until<=?) ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED",utc(now),utc(now));
        if(rows.isEmpty()) return null;
        var row=rows.getFirst();String id=(String)row.get("id");int attempt=((Number)row.get("scan_attempt")).intValue();
        if(row.get("scan_lease_id")!=null || attempt>=4) {
            finish(id,(String)row.get("organization_id"),attempt,Result.retry(Reason.SCANNER_TIMEOUT),now);
            return null;
        }
        String lease=UUID.randomUUID().toString();attempt++;
        jdbc.update("UPDATE files SET scan_attempt=?,scan_lease_id=?,scan_lease_until=?,scan_next_attempt_at=NULL,version=version+1 WHERE id=?",attempt,lease,utc(now.plusSeconds(75)),id);
        var bindings=jdbc.queryForList("SELECT binding_kind,purpose FROM file_bindings WHERE file_id=?",id);
        String resource=bindings.isEmpty()?null:(String)bindings.getFirst().get("binding_kind");
        String purpose=bindings.isEmpty()?null:(String)bindings.getFirst().get("purpose");
        return new Job(id,lease,attempt,(String)row.get("storage_key"),((Number)row.get("size_bytes")).longValue(),
            (String)row.get("sha256"),(String)row.get("original_name"),(String)row.get("content_type"),resource,purpose);
    }
    @Transactional
    public boolean complete(Job job,Result result) {
        var rows=jdbc.queryForList("SELECT organization_id,scan_lease_until FROM files WHERE id=? AND scan_status='PENDING' AND deleted_at IS NULL AND scan_attempt=? AND scan_lease_id=? FOR UPDATE",job.fileId(),job.attempt(),job.leaseId());
        if(rows.isEmpty()) return false;
        Instant now=now();var row=rows.getFirst();
        Object leaseUntil=row.get("scan_lease_until");
        Instant deadline=(leaseUntil instanceof java.sql.Timestamp stamp?stamp.toLocalDateTime():(LocalDateTime)leaseUntil).toInstant(ZoneOffset.UTC);
        if(!now.isBefore(deadline)) result=Result.retry(Reason.SCANNER_TIMEOUT);
        finish(job.fileId(),(String)row.get("organization_id"),job.attempt(),result,now);return true;
    }
    private void finish(String id,String org,int attempt,Result result,Instant now) {
        boolean retry=result.outcome()==Outcome.RETRY && attempt<4;
        String status=retry?"PENDING":result.outcome()==Outcome.CLEAN?"CLEAN":"REJECTED";
        LocalDateTime next=retry?utc(now.plusSeconds(new int[]{5,30,120}[attempt-1])):null;
        jdbc.update("UPDATE files SET scan_status=?,scanner_reason_code=?,scan_next_attempt_at=?,scan_lease_id=NULL,scan_lease_until=NULL,version=version+1 WHERE id=?",status,result.reason().name(),next,id);
        audit.fileScanCompleted(id,org,status,attempt,result.reason().name());
    }
}
