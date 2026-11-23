package vn.utbridora.auth.modules.matching.advisory;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.utbridora.auth.modules.matching.domain.StrictClassifier.MatchResult;
import static vn.utbridora.auth.modules.matching.advisory.AdvisoryModels.*;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@Repository
public class AdvisoryRepository {
    private final JdbcTemplate jdbc;
    public AdvisoryRepository(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public void admission(String run,String school) {
        jdbc.queryForObject("SELECT id FROM matching_advisory_control WHERE id=1 FOR UPDATE",Integer.class);
        if(count("run_id=? AND state IN ('QUEUED','RUNNING') AND expires_at>UTC_TIMESTAMP(6)",run)>0) throw failure(409,"AI_ADVISORY_JOB_ACTIVE");
        if(count("state IN ('QUEUED','RUNNING') AND expires_at>UTC_TIMESTAMP(6)")>=32
            || count("school_org_id=? AND state IN ('QUEUED','RUNNING') AND expires_at>UTC_TIMESTAMP(6)",school)>=2
            || count("run_id=? AND created_at>UTC_TIMESTAMP(6)-INTERVAL 24 HOUR",run)>=10)
            throw new vn.utbridora.auth.operations.OperationFailure(429,"AI_ADVISORY_CAPACITY_EXCEEDED","Hàng đợi đang đầy.",30);
    }
    private int count(String condition,Object... args) {
        // A locking current read is essential: earlier scope/ledger reads may have
        // established a REPEATABLE READ snapshot before the admission guard wait.
        return jdbc.queryForList("SELECT id FROM matching_advisory_jobs WHERE "+condition+" FOR UPDATE",String.class,args).size();
    }
    public boolean schoolOrigin(String run) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM matching_run_origins o JOIN match_runs r ON r.id=o.run_id WHERE o.run_id=? AND o.audience='SCHOOL' AND NOT EXISTS(SELECT 1 FROM matching_privacy_releases p WHERE p.match_run_id=o.run_id) AND NOT EXISTS(SELECT 1 FROM matching_demo_batches b WHERE b.dataset_snapshot_id=r.dataset_id)",Integer.class,run)==1;
    }
    public List<MatchResult> population(String run) {
        var rows=jdbc.query("SELECT data_json FROM match_results WHERE run_id=? AND overall_status='MATCH' ORDER BY student_user_id LIMIT 5001",(r,n)->JSON.readValue(r.getString(1),MatchResult.class),run);
        if(rows.size()>5000) throw failure(409,"AI_ADVISORY_LIMIT_EXCEEDED");return rows;
    }
    public void insert(Data d) {jdbc.update("INSERT INTO matching_advisory_jobs(id,run_id,school_org_id,data_json,state,created_at,expires_at,next_attempt_at) VALUES(?,?,?,?,'QUEUED',?,?,?)",d.id(),d.run().matchRunId(),d.school(),JSON.writeValueAsString(d),utc(d.createdAt()),utc(d.expiresAt()),utc(d.createdAt()));}
    public Stored load(String id,boolean lock) {
        return jdbc.query("SELECT * FROM matching_advisory_jobs WHERE id=?"+(lock?" FOR UPDATE":""),(r,n)->new Stored(JSON.readValue(r.getString("data_json"),Data.class),r.getString("state"),instant(r,"completed_at"),r.getString("counts_json")==null?null:JSON.readValue(r.getString("counts_json"),Counts.class),r.getString("failure_code"),r.getInt("attempt"),r.getLong("lease_token"),instant(r,"lease_until")),uuid(id)).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
    }
    public void invalidate(Stored s,String state,String reason) {jdbc.update("UPDATE matching_advisory_jobs SET state=?,failure_code=?,counts_json=NULL,lease_token=lease_token+1,lease_until=NULL WHERE id=?",state,reason,s.data().id());}
    public List<AdvisoryPolicy.Item> items(String id,int page,int size) {return jdbc.query("SELECT data_json FROM matching_advisory_rows WHERE job_id=? ORDER BY position LIMIT ? OFFSET ?",(r,n)->JSON.readValue(r.getString(1),AdvisoryPolicy.Item.class),id,size,page*size);}
    public Stored claim() {
        var ids=jdbc.queryForList("SELECT id FROM matching_advisory_jobs WHERE expires_at>UTC_TIMESTAMP(6) AND ((state='QUEUED' AND next_attempt_at<=UTC_TIMESTAMP(6)) OR (state='RUNNING' AND lease_until<=UTC_TIMESTAMP(6))) ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED",String.class);
        if(ids.isEmpty()) return null;var s=load(ids.getFirst(),true);
        if(s.attempt()>=3) {invalidate(s,"FAILED","WORK_TIMEOUT");return load(s.data().id(),false);}
        jdbc.update("UPDATE matching_advisory_jobs SET state='RUNNING',attempt=attempt+1,lease_token=lease_token+1,lease_until=UTC_TIMESTAMP(6)+INTERVAL 90 SECOND WHERE id=?",s.data().id());return load(s.data().id(),false);
    }
    public boolean live(Stored s) {var now=load(s.data().id(),true);return now.state().equals("RUNNING") && now.token()==s.token() && now.leaseUntil()!=null && Instant.now().isBefore(now.leaseUntil()) && Instant.now().isBefore(now.data().expiresAt());}
    public void heartbeat(Stored s) {jdbc.update("UPDATE matching_advisory_jobs SET lease_until=LEAST(expires_at,UTC_TIMESTAMP(6)+INTERVAL 90 SECOND) WHERE id=? AND state='RUNNING' AND lease_token=? AND lease_until>UTC_TIMESTAMP(6) AND expires_at>UTC_TIMESTAMP(6)",s.data().id(),s.token());}
    public void publish(Stored s,List<AdvisoryPolicy.Item> rows,Counts counts,String reason,Map<String,String> hashes) {
        if(!live(s)) return;
        jdbc.update("DELETE FROM matching_advisory_rows WHERE job_id=?",s.data().id());
        for(var row:rows) jdbc.update("INSERT INTO matching_advisory_rows(job_id,student_user_id,position,data_json,input_hash) VALUES(?,?,?,?,?)",s.data().id(),row.studentUserId(),row.position(),JSON.writeValueAsString(row),Objects.requireNonNull(hashes.get(row.studentUserId())));
        jdbc.update("UPDATE matching_advisory_jobs SET state=?,counts_json=?,failure_code=?,completed_at=UTC_TIMESTAMP(6),lease_until=NULL WHERE id=? AND lease_token=?",counts.scoredCount()>0?"READY":"NO_DATA",JSON.writeValueAsString(counts),reason,s.data().id(),s.token());
    }
    public void fail(Stored s,String reason,boolean retry) {
        if(!live(s)) return;
        if(retry && s.attempt()<3) jdbc.update("UPDATE matching_advisory_jobs SET state='QUEUED',lease_until=NULL,next_attempt_at=UTC_TIMESTAMP(6)+INTERVAL ? SECOND WHERE id=? AND lease_token=?",s.attempt()==1?5:30,s.data().id(),s.token());
        else jdbc.update("UPDATE matching_advisory_jobs SET state='FAILED',failure_code=?,counts_json=NULL,lease_until=NULL WHERE id=? AND lease_token=?",reason,s.data().id(),s.token());
    }
    public List<String> expired() {return jdbc.queryForList("SELECT id FROM matching_advisory_jobs WHERE expires_at<=UTC_TIMESTAMP(6) AND state NOT IN ('EXPIRED','REVOKED') ORDER BY expires_at LIMIT 16 FOR UPDATE SKIP LOCKED",String.class);}
    public boolean held() {return jdbc.query("SELECT hold_enabled,hold_until FROM matching_advisory_control WHERE id=1 FOR SHARE",(r,n)->r.getBoolean(1) && (instant(r,"hold_until")==null || Instant.now().isBefore(instant(r,"hold_until")))).stream().findFirst().orElse(true);}
    public List<String> purgeable() {return jdbc.queryForList("SELECT id FROM matching_advisory_jobs WHERE sources_purged=false AND expires_at<=UTC_TIMESTAMP(6) ORDER BY expires_at LIMIT 16 FOR UPDATE SKIP LOCKED",String.class);}
    public void purged(String id) {jdbc.update("DELETE FROM matching_advisory_rows WHERE job_id=?",id);jdbc.update("UPDATE matching_advisory_jobs SET sources_purged=true WHERE id=?",id);}
}
