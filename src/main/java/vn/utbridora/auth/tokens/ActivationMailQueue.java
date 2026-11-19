package vn.utbridora.auth.tokens;

import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.audit.AuditService;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** Private durable transport. No SMTP in these transactions, no token in public/domain records. */
@Service
public class ActivationMailQueue {
    public record Dispatch(String id,String messageId,String claimId,long generation,String email,
        ActivationMailCipher.Encrypted payload,String slot,String owner,int attempt,String operation,String initiatedBy,String school) {}
    private final JdbcTemplate jdbc;private final InvitationClaims claims;private final AuditService audit;private final Clock clock;
    private final int perMinute;
    public ActivationMailQueue(JdbcTemplate jdbc,InvitationClaims claims,AuditService audit,@Qualifier("onboardingClock") Clock clock,
        @Value("${app.onboarding.mail.dispatch-per-minute:60}") int perMinute) {
        if(perMinute<5 || perMinute>300) throw new IllegalArgumentException("Activation dispatch limit must be 5..300");
        this.jdbc=jdbc;this.claims=claims;this.audit=audit;this.clock=clock;this.perMinute=perMinute;
    }
    @Transactional public Dispatch claim(String owner) {
        Instant now=clock.instant();
        // All dispatcher transactions lock budget/slot before claim/mail; two global slots across instances.
        for(String key:List.of("activation-global","activation-slot-1","activation-slot-2")) jdbc.update("INSERT INTO mail_dispatch_budget(budget_key,window_start) VALUES(?,?) ON DUPLICATE KEY UPDATE budget_key=budget_key",key,utc(now));
        var budget=jdbc.queryForMap("SELECT * FROM mail_dispatch_budget WHERE budget_key='activation-global' FOR UPDATE");
        if(!now.isBefore(time(budget.get("window_start")).plusSeconds(60))) {jdbc.update("UPDATE mail_dispatch_budget SET window_start=?,attempts=0,version=version+1 WHERE budget_key='activation-global'",utc(now));budget.put("attempts",0);}
        var slots=jdbc.queryForList("SELECT budget_key FROM mail_dispatch_budget WHERE budget_key IN ('activation-slot-1','activation-slot-2') AND (lease_until IS NULL OR lease_until<=?) ORDER BY budget_key LIMIT 1 FOR UPDATE SKIP LOCKED",String.class,utc(now));
        if(slots.isEmpty() || ((Number)budget.get("attempts")).intValue()>=perMinute) return null;
        var candidates=jdbc.queryForList("SELECT id,claim_id FROM activation_mail_outbox WHERE (state IN ('QUEUED','RETRY_WAIT') AND (next_attempt_at IS NULL OR next_attempt_at<=?)) OR (state='SENDING' AND lease_until<=?) ORDER BY created_at,id LIMIT 20",utc(now),utc(now));
        for(var candidate:candidates) {
            var claim=claims.lock(candidate.get("claim_id").toString());
            var status=jdbc.queryForList("SELECT status FROM users WHERE email=? FOR UPDATE",String.class,claim.email());
            var row=jdbc.queryForMap("SELECT * FROM activation_mail_outbox WHERE id=? FOR UPDATE",candidate.get("id"));
            String state=row.get("state").toString();
            if(!Set.of("QUEUED","RETRY_WAIT","SENDING").contains(state) || state.equals("SENDING") && row.get("lease_until")!=null && now.isBefore(time(row.get("lease_until")))) continue;
            if(!claim.state().equals("PENDING") || !status.isEmpty() && !status.getFirst().equals("PENDING_ACTIVATION")) {
                terminal(row,"CANCELLED");
                if(!status.isEmpty() && !status.getFirst().equals("ACTIVE")) jdbc.update("UPDATE invitation_claims SET claim_state='REVIEW_REQUIRED',version=version+1,updated_at=? WHERE id=?",utc(now),claim.id());
                continue;
            }
            if(((Number)row.get("generation")).longValue()!=claim.generation() || !Objects.equals(row.get("token_id"),claim.token())) {terminal(row,"SUPERSEDED");continue;}
            var token=jdbc.queryForMap("SELECT * FROM one_time_tokens WHERE id=? FOR UPDATE",claim.token());
            if(token.get("consumed_at")!=null || token.get("revoked_at")!=null) {terminal(row,"CANCELLED");continue;}
            if(!now.isBefore(time(token.get("expires_at")))) {
                String operation=UUID.nameUUIDFromBytes(("activation-rotation|"+claim.id()+"|"+claim.generation()).getBytes(StandardCharsets.UTF_8)).toString();
                claims.issueRoster(claim,operation,(String)row.get("initiated_by"),"EXPIRED_BEFORE_DELIVERY");continue;
            }
            int attempts=((Number)row.get("attempt_count")).intValue();
            if(attempts>=4) {terminal(row,"FAILED");failureAudit(row,claim,"DELIVERY_UNKNOWN",true);continue;}
            if(state.equals("SENDING")) {
                jdbc.update("UPDATE activation_mail_outbox SET state='RETRY_WAIT',lease_owner=NULL,lease_until=NULL,next_attempt_at=?,version=version+1,last_error_class='DELIVERY_UNKNOWN' WHERE id=?",utc(now.plusSeconds(new int[]{5,30,120}[Math.min(attempts-1,2)])),row.get("id"));continue;
            }
            String slot=slots.getFirst();int attempt=attempts+1;
            jdbc.update("UPDATE mail_dispatch_budget SET lease_owner=?,lease_until=?,version=version+1 WHERE budget_key=?",owner,utc(now.plusSeconds(60)),slot);
            jdbc.update("UPDATE mail_dispatch_budget SET attempts=attempts+1,version=version+1 WHERE budget_key='activation-global'");
            jdbc.update("UPDATE activation_mail_outbox SET state='SENDING',attempt_count=?,lease_owner=?,lease_until=?,next_attempt_at=NULL,version=version+1 WHERE id=?",attempt,owner,utc(now.plusSeconds(60)),row.get("id"));
            return new Dispatch(row.get("id").toString(),row.get("message_id").toString(),claim.id(),claim.generation(),claim.email(),new ActivationMailCipher.Encrypted((byte[])row.get("encrypted_payload"),(String)row.get("key_id"),(byte[])row.get("nonce"),(byte[])row.get("tag")),slot,owner,attempt,row.get("operation_id").toString(),(String)row.get("initiated_by"),claim.school());
        }
        return null;
    }
    @Transactional public void complete(Dispatch d,String error,boolean permanent) {
        var slot=jdbc.queryForMap("SELECT * FROM mail_dispatch_budget WHERE budget_key=? FOR UPDATE",d.slot());
        var claim=claims.lock(d.claimId());var rows=jdbc.queryForList("SELECT * FROM activation_mail_outbox WHERE id=? AND state='SENDING' AND lease_owner=? AND attempt_count=? FOR UPDATE",d.id(),d.owner(),d.attempt());
        if(rows.isEmpty()) return;var row=rows.getFirst();Instant now=clock.instant();
        if(!Objects.equals(slot.get("lease_owner"),d.owner()) || row.get("lease_until")==null || !now.isBefore(time(row.get("lease_until")))) return;
        if(claim.generation()!=d.generation() || !claim.state().equals("PENDING")) terminal(row,"SUPERSEDED");
        else if(error==null) {
            terminal(row,"SENT");jdbc.update("UPDATE activation_mail_outbox SET sent_at=? WHERE id=?",utc(now),d.id());
            audit.workflow(d.initiatedBy(),"ACTIVATION_EMAIL_SENT",d.id(),d.school(),Map.of("messageId",d.messageId(),"attemptNo",d.attempt(),"operationId",d.operation()));
        } else {
            if(!Set.of("SMTP_TEMPORARY","SMTP_PERMANENT","TRANSPORT_KEY_UNAVAILABLE").contains(error)) error="SMTP_TEMPORARY";
            boolean done=permanent || d.attempt()>=4;
            if(done) terminal(row,"FAILED");
            else jdbc.update("UPDATE activation_mail_outbox SET state='RETRY_WAIT',next_attempt_at=?,lease_owner=NULL,lease_until=NULL,version=version+1 WHERE id=?",utc(now.plusSeconds(new int[]{5,30,120}[d.attempt()-1])),d.id());
            jdbc.update("UPDATE activation_mail_outbox SET last_error_class=? WHERE id=?",error,d.id());failureAudit(row,claim,error,done);
        }
        jdbc.update("UPDATE mail_dispatch_budget SET lease_owner=NULL,lease_until=NULL,version=version+1 WHERE budget_key=? AND lease_owner=?",d.slot(),d.owner());
    }
    private void failureAudit(Map<String,Object> row,InvitationClaims.Claim claim,String error,boolean terminal) {
        audit.workflowFailure((String)row.get("initiated_by"),"ACTIVATION_EMAIL_FAILED",row.get("id").toString(),claim.school(),Map.of("messageId",row.get("message_id"),"attemptNo",row.get("attempt_count"),"errorClass",error,"terminal",terminal,"operationId",row.get("operation_id")));
    }
    private void terminal(Map<String,Object> row,String state) {
        jdbc.update("UPDATE activation_mail_outbox SET state=?,encrypted_payload=NULL,key_id=NULL,nonce=NULL,tag=NULL,lease_owner=NULL,lease_until=NULL,version=version+1 WHERE id=?",state,row.get("id"));
    }
    private static Instant time(Object value) {return (value instanceof java.sql.Timestamp t?t.toLocalDateTime():(LocalDateTime)value).toInstant(ZoneOffset.UTC);}
}
