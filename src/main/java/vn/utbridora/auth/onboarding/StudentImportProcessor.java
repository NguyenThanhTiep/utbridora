package vn.utbridora.auth.onboarding;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.operations.*;
import static vn.utbridora.auth.operations.WorkflowValues.*;
import static vn.utbridora.auth.onboarding.StudentImportContracts.*;

/** Each method is a separate, bounded database transaction; no scan or SMTP inside it. */
@Service
public class StudentImportProcessor {
    private final StudentImportService s;
    public StudentImportProcessor(StudentImportService service) {s=service;}
    public record Lease(String jobId,String owner,long epoch) {}
    @Transactional public Lease claim(String owner) {
        Instant now=s.clock.instant();
        var expired=s.jdbc.queryForList("SELECT id FROM student_import_jobs WHERE state='PROCESSING' AND lease_until<=? ORDER BY id LIMIT 20 FOR UPDATE SKIP LOCKED",String.class,utc(now));
        for(String id:expired) {
            var j=s.job(id,true);var counts=s.project(j);
            if(counts.processedRows()==j.selected() && j.selected()>0) {s.finalizeJob(j);continue;}
            s.jdbc.update("UPDATE student_import_rows SET row_state='TRANSIENT_FAILED',error_codes=JSON_ARRAY('TRANSIENT_DEPENDENCY'),version=version+1 WHERE job_id=? AND selected=true AND row_state='PROCESSING'",id);
            fail(j,"LEASE_EXPIRED");
        }
        s.jdbc.update("UPDATE student_import_jobs SET state='EXPIRED',version=version+1 WHERE state='PREVIEW_READY' AND preview_expires_at<=?",utc(now));
        var jobs=s.jdbc.queryForList("SELECT id FROM student_import_jobs WHERE state='PROCESSING' AND (lease_owner IS NULL OR lease_owner=?) ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED",String.class,owner);
        if(jobs.isEmpty()) return null;var j=s.job(jobs.getFirst(),true);
        long epoch=j.epoch()+1;
        s.jdbc.update("UPDATE student_import_jobs SET lease_owner=?,lease_epoch=?,lease_until=? WHERE id=?",owner,epoch,utc(now.plusSeconds(120)),j.id());
        return new Lease(j.id(),owner,epoch);
    }
    @Transactional public boolean process(Lease lease) {
        var j=s.job(lease.jobId(),true);Instant now=s.clock.instant();
        if(!owns(j,lease,now)) return false;
        var auth=s.permissions.currentSubject(j.initiatedBy()).orElse(null);
        try {
            if(auth==null || ActorContext.fromAuthenticatedWorkflow(auth,j.school()).currentSecurityVersion()!=j.security()) throw failure(403,"AUTHORITY_REVOKED");
            s.access.require(auth,j.school(),j.term(),StudentImportService.PERMISSION);
            s.access.requireStamp(j.assignment(),j.authority(),j.initiatedBy(),j.school(),j.term(),StudentImportService.PERMISSION);
        } catch(OperationFailure denied) {if(denied.status!=403 && denied.status!=401 && denied.status!=404) throw denied;fail(j,"AUTHORITY_REVOKED");return false;}
        var rows=s.jdbc.queryForList("SELECT * FROM student_import_rows WHERE job_id=? AND selected=true AND row_state IN ('QUEUED','PROCESSING','TRANSIENT_FAILED') ORDER BY row_no,id LIMIT 1 FOR UPDATE",j.id());
        if(rows.isEmpty()) {s.finalizeJob(j);return false;}
        var row=rows.getFirst();String rowId=row.get("id").toString();
        if(row.get("next_attempt_at")!=null && now.isBefore(time(row.get("next_attempt_at")))) {heartbeat(j);return false;}
        var data=JSON.readValue(row.get("normalized_snapshot").toString(),ImportSourceData.class);
        var claim=s.claims.lockEmail(data.email()); // Global identity fence before identity/roster/token locks.
        String operation=row.get("operation_id").toString();
        var result=s.rosters.inspectRoster(ActorContext.fromAuthenticatedWorkflow(auth,j.school()),s.ensure(j,data,operation));
        if(result.status().equals("NEEDS_REVIEW") || !s.claims.permitsRoster(claim,j.school(),result.rosterId()) && !result.status().equals("ALREADY_LINKED")) {
            terminal(j,rowId,"NEEDS_REVIEW",result.status().equals("NEEDS_REVIEW")?result.reasonCode():"IDENTITY_CONFLICT",null,null,null,null,null);return true;
        }
        var current=s.terms.findById(j.term()).orElse(null);
        var catalog=s.catalogs.forProfile(j.school(),j.term());
        if(current==null || current.status()!=vn.utbridora.auth.student.port.TermReferencePort.Status.ACTIVE || current.version()!=j.termVersion() || !catalog.version().equals(j.catalog())) {
            terminal(j,rowId,"PERMANENT_FAILED","REFERENCE_DATA_CHANGED",null,null,null,null,null);return true;
        }
        s.jdbc.update("UPDATE student_import_rows SET row_state='PROCESSING' WHERE id=?",rowId);
        String effectKey=OperationLedger.payloadHash(Map.of("schoolOrgId",j.school(),"studentCode",data.studentCode(),"termId",j.term(),"email",data.email()));
        String payloadHash=OperationLedger.payloadHash(data);
        var effects=s.jdbc.queryForList("SELECT * FROM student_import_effects WHERE effect_key=? FOR UPDATE",effectKey);
        if(!effects.isEmpty() && !effects.getFirst().get("payload_hash").equals(payloadHash)) {
            terminal(j,rowId,"NEEDS_REVIEW","ROSTER_METADATA_CONFLICT",null,null,null,null,null);return true;
        }
        result=s.rosters.ensureRoster(ActorContext.fromAuthenticatedWorkflow(auth,j.school()),s.ensure(j,data,operation));
        if(result.status().equals("NEEDS_REVIEW")) {terminal(j,rowId,"NEEDS_REVIEW",result.reasonCode(),null,null,null,null,null);return true;}
        boolean active=result.status().equals("ALREADY_LINKED");String claimId=null;
        if(!active) {
            claim=s.claims.attachRoster(claim,j.school(),result.rosterId(),result.userId());
            if(claim.token()==null) claim=s.claims.issueRoster(claim,operation,j.initiatedBy(),"INITIAL");
            claimId=claim.id();
        }
        String effect=effects.isEmpty()?UUID.randomUUID().toString():effects.getFirst().get("id").toString();
        if(effects.isEmpty()) s.jdbc.update("INSERT INTO student_import_effects(id,effect_key,payload_hash,school_org_id,term_id,roster_id,claim_id,user_id,created_at) VALUES(?,?,?,?,?,?,?,?,?)",effect,effectKey,payloadHash,j.school(),j.term(),result.rosterId(),claimId,result.userId(),utc(now));
        String outcome=active?"LINKED_EXISTING":effects.isEmpty()?result.status():"REUSED_PENDING";
        terminal(j,rowId,active?"ALREADY_LINKED":"PROVISIONED",null,result.rosterId(),effect,claimId,result.userId(),outcome);
        s.audit.workflow(j.initiatedBy(),active?"STUDENT_ROSTER_ALREADY_LINKED":"STUDENT_ROSTER_PROVISIONED",rowId,j.school(),Map.of("rosterId",result.rosterId(),"rowNo",((Number)row.get("row_no")).intValue(),"operationId",operation,"rosterVersion",result.rosterVersion()));
        return true;
    }
    private void heartbeat(StudentImportService.Job j) {s.jdbc.update("UPDATE student_import_jobs SET lease_until=? WHERE id=?",utc(s.clock.instant().plusSeconds(120)),j.id());}
    private boolean owns(StudentImportService.Job j,Lease l,Instant now) {return j.state().equals("PROCESSING") && Objects.equals(j.owner(),l.owner()) && j.epoch()==l.epoch() && j.lease()!=null && now.isBefore(j.lease());}
    private void terminal(StudentImportService.Job j,String row,String state,String error,String roster,String effect,String claim,String user,String outcome) {
        s.jdbc.update("UPDATE student_import_rows SET row_state=?,error_codes=?,roster_id=?,provisioning_effect_id=?,claim_id=?,user_id=?,provisioning_outcome=?,version=version+1,next_attempt_at=NULL WHERE id=?",state,error==null?"[]":JSON.writeValueAsString(List.of(error)),roster,effect,claim,user,outcome,row);
        s.jdbc.update("UPDATE student_import_jobs SET version=version+1,lease_until=? WHERE id=?",utc(s.clock.instant().plusSeconds(120)),j.id());
        var updated=s.job(j.id(),true);if(s.project(updated).processedRows()==j.selected()) s.finalizeJob(updated);
    }
    @Transactional public void dependencyFailed(Lease lease,String code,boolean permanent) {
        var j=s.job(lease.jobId(),true);if(!owns(j,lease,s.clock.instant())) return;
        var rows=s.jdbc.queryForList("SELECT id,attempt_count FROM student_import_rows WHERE job_id=? AND selected=true AND row_state IN ('QUEUED','PROCESSING','TRANSIENT_FAILED') ORDER BY row_no,id LIMIT 1 FOR UPDATE",j.id());
        if(rows.isEmpty()) {s.finalizeJob(j);return;}var row=rows.getFirst();String id=row.get("id").toString();
        if(permanent) {terminal(j,id,"PERMANENT_FAILED",code,null,null,null,null,null);return;}
        int attempts=((Number)row.get("attempt_count")).intValue()+1;
        s.jdbc.update("UPDATE student_import_rows SET row_state='TRANSIENT_FAILED',attempt_count=?,next_attempt_at=?,error_codes=JSON_ARRAY('TRANSIENT_DEPENDENCY'),version=version+1 WHERE id=?",attempts,utc(s.clock.instant().plusSeconds(new int[]{1,5,30,30}[Math.min(attempts-1,3)])),id);
        s.jdbc.update("UPDATE student_import_jobs SET version=version+1 WHERE id=?",j.id());
        if(attempts>=4) fail(s.job(j.id(),true),"TRANSIENT_DEPENDENCY");
    }
    private void fail(StudentImportService.Job j,String code) {
        s.jdbc.update("UPDATE student_import_jobs SET state='FAILED',version=version+1,last_error_code=?,finished_at=?,lease_owner=NULL,lease_until=NULL,lease_epoch=lease_epoch+1 WHERE id=?",code,utc(s.clock.instant()),j.id());
        s.audit.workflowFailure(j.initiatedBy(),"STUDENT_IMPORT_FAILED",j.id(),j.school(),Map.of("jobVersion",j.version()+1,"errorClass",code,"checkpointVersion",j.version()+1));
    }
    static Instant time(Object value) {return (value instanceof java.sql.Timestamp t?t.toLocalDateTime():(LocalDateTime)value).toInstant(ZoneOffset.UTC);}
}
