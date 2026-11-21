package vn.utbridora.auth.tokens;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.audit.AuditService;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** T1 identity mutex shared by legacy/Mentor/roster workflows. Always lock this before the identity. */
@Service
@Transactional(propagation=Propagation.MANDATORY)
public class InvitationClaims {
    public record Claim(String id,String email,String context,String school,String roster,String invitation,String expectedUser,
        String user,String state,long version,long generation,String token,Instant lastIssued) {}
    private final JdbcTemplate jdbc;private final ActivationMailCipher cipher;private final Clock clock;private final AuditService audit;
    public InvitationClaims(JdbcTemplate jdbc,ActivationMailCipher cipher,@Qualifier("onboardingClock") Clock clock,AuditService audit) {this.jdbc=jdbc;this.cipher=cipher;this.clock=clock;this.audit=audit;}
    public Claim lockEmail(String address) {
        String email=canonical(address);
        jdbc.update("INSERT INTO invitation_claims(id,email_canonical,context_kind,claim_state,generation,created_at,updated_at) VALUES(?,?,'LEGACY','PENDING',0,?,?) ON DUPLICATE KEY UPDATE id=id",UUID.randomUUID().toString(),email,utc(clock.instant()),utc(clock.instant()));
        return find("email_canonical",email,true).orElseThrow();
    }
    public Optional<Claim> inspectEmail(String address) {return find("email_canonical",canonical(address),false);}
    public Claim lock(String id) {return find("id",uuid(id),true).orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));}
    private Optional<Claim> find(String column,String value,boolean lock) {
        if(!Set.of("id","email_canonical").contains(column)) throw new IllegalArgumentException();
        return jdbc.query("SELECT * FROM invitation_claims WHERE "+column+"=?"+(lock?" FOR UPDATE":""),(r,n)->new Claim(r.getString("id"),r.getString("email_canonical"),r.getString("context_kind"),r.getString("school_org_id"),r.getString("roster_id"),r.getString("invitation_ref_id"),r.getString("expected_user_id"),r.getString("user_id"),r.getString("claim_state"),r.getLong("version"),r.getLong("generation"),r.getString("current_token_id"),instant(r,"last_issued_at")),value).stream().findFirst();
    }
    public boolean permitsRoster(Claim c,String school,String roster) {
        return c.generation()==0 && c.token()==null && c.state().equals("PENDING")
            || c.context().equals("ROSTER") && c.state().equals("PENDING") && Objects.equals(c.school(),school) && Objects.equals(c.roster(),roster);
    }
    public Claim attachRoster(Claim c,String school,String roster,String expectedUser) {
        if(!permitsRoster(c,school,roster)) throw failure(409,"IDENTITY_CONFLICT");
        if(!c.context().equals("ROSTER")) jdbc.update("UPDATE invitation_claims SET context_kind='ROSTER',school_org_id=?,roster_id=?,expected_user_id=?,version=version+1,updated_at=? WHERE id=?",school,roster,expectedUser,utc(clock.instant()),c.id());
        return lock(c.id());
    }
    public Claim issueRoster(Claim before,String operation,String initiatedBy,String reason) {
        cipher.requireReady();var c=lock(before.id());
        if(!c.context().equals("ROSTER") || !c.state().equals("PENDING") || c.generation()>=9_007_199_254_740_991L) throw failure(409,"ACTIVATION_NOT_PENDING");
        Instant now=clock.instant();
        if(c.token()!=null) {
            jdbc.update("UPDATE one_time_tokens SET revoked_at=?,revocation_reason='TOKEN_REPLACED' WHERE id=? AND consumed_at IS NULL AND revoked_at IS NULL",utc(now),c.token());
            jdbc.update("UPDATE activation_mail_outbox SET state='SUPERSEDED',encrypted_payload=NULL,key_id=NULL,nonce=NULL,tag=NULL,lease_owner=NULL,lease_until=NULL,version=version+1 WHERE claim_id=? AND state IN ('QUEUED','RETRY_WAIT','SENDING')",c.id());
            audit.workflow(initiatedBy,"ACCOUNT_INVITATION_REVOKED",c.id(),c.school(),Map.of("purpose","INVITATION","reasonCode","TOKEN_REPLACED","operationId",uuid(operation)));
        }
        long generation=c.generation()+1;String tokenId=UUID.randomUUID().toString(),mailId=UUID.randomUUID().toString(),raw=TokenCodec.generate();
        var sealed=cipher.encrypt(raw,c.id(),generation,mailId);
        jdbc.update("INSERT INTO one_time_tokens(id,token_hash,purpose,user_id,email,invite_role,organization_id,expires_at,created_at,issued_at,claim_id,generation) VALUES(?,?,'INVITATION',?,?,'STUDENT',?,?,?,?,?,?)",tokenId,TokenCodec.hash(raw),c.expectedUser(),c.email(),c.school(),utc(now.plus(Duration.ofHours(48))),utc(now),utc(now),c.id(),generation);
        jdbc.update("INSERT INTO activation_mail_outbox(id,message_id,claim_id,token_id,generation,template_code,state,issuance_reason,operation_id,initiated_by,encrypted_payload,key_id,nonce,tag,created_at) VALUES(?,?,?,?,?,'STUDENT_ACTIVATION_V1','QUEUED',?,?,?,?,?,?,?,?)",mailId,mailId,c.id(),tokenId,generation,reason,uuid(operation),initiatedBy,sealed.ciphertext(),sealed.keyId(),sealed.nonce(),sealed.tag(),utc(now));
        jdbc.update("UPDATE invitation_claims SET current_token_id=?,generation=?,version=version+1,last_issued_at=?,updated_at=? WHERE id=?",tokenId,generation,utc(now),utc(now),c.id());
        return lock(c.id());
    }
    public void forbidPendingRosterTakeover(String email) {
        var c=lockEmail(email);
        if(c.context().equals("ROSTER") && c.state().equals("PENDING")) throw failure(409,"ACTIVATION_NOT_PENDING");
    }
    public void registerNonRoster(OneTimeToken token,String context,String invitation,String school) {
        if(!Set.of("LEGACY","MENTOR").contains(context)) throw new IllegalArgumentException();
        var c=lockEmail(token.email);
        if(c.context().equals("ROSTER") && c.state().equals("PENDING")) throw failure(409,"ACTIVATION_NOT_PENDING");
        if(c.state().equals("REVIEW_REQUIRED")) throw failure(409,"ACTIVATION_NOT_PENDING");
        if(c.generation()>0 && c.state().equals("PENDING") && !c.context().equals(context)) throw failure(409,"ACTIVATION_NOT_PENDING");
        long generation=c.generation()+1;
        if(c.token()!=null) jdbc.update("UPDATE one_time_tokens SET revoked_at=?,revocation_reason='TOKEN_REPLACED' WHERE id=? AND consumed_at IS NULL AND revoked_at IS NULL",utc(clock.instant()),c.token());
        token.claimId=c.id();token.generation=generation;if(token.issuedAt==null) token.issuedAt=clock.instant();
        jdbc.update("UPDATE one_time_tokens SET claim_id=?,generation=?,issued_at=? WHERE id=?",c.id(),generation,utc(token.issuedAt),token.id);
        jdbc.update("UPDATE invitation_claims SET context_kind=?,school_org_id=?,roster_id=NULL,invitation_ref_id=?,expected_user_id=?,user_id=NULL,claim_state='PENDING',generation=?,version=version+1,current_token_id=?,last_issued_at=?,updated_at=? WHERE id=?",context,school,invitation,token.userId,generation,token.id,utc(token.issuedAt),utc(clock.instant()),c.id());
    }
    /** Acceptance uses READ_COMMITTED: the claim mutex fences all invitation writers while this
     * current, nonlocking read preserves claim -> user -> roster -> token mutation lock order. */
    public void requireCurrentToken(String hash) {
        var candidate=jdbc.queryForList("SELECT claim_id,email FROM one_time_tokens WHERE token_hash=? AND purpose='INVITATION'",hash);
        if(candidate.isEmpty()) throw failure(422,"TOKEN_INVALID");
        Claim c=candidate.getFirst().get("claim_id")==null?lockEmail(candidate.getFirst().get("email").toString()):lock(candidate.getFirst().get("claim_id").toString());
        var tokens=jdbc.queryForList("SELECT id,claim_id,consumed_at,revoked_at,expires_at FROM one_time_tokens WHERE token_hash=? AND purpose='INVITATION'",hash);
        if(tokens.isEmpty()) throw failure(422,"TOKEN_INVALID");var t=tokens.getFirst();
        if(t.get("consumed_at")!=null) throw failure(409,"TOKEN_ALREADY_USED");
        if(t.get("revoked_at")!=null || t.get("claim_id")!=null && !Objects.equals(c.token(),t.get("id"))) throw failure(409,"TOKEN_REVOKED");
        var expires=t.get("expires_at") instanceof java.sql.Timestamp ts?ts.toLocalDateTime():(java.time.LocalDateTime)t.get("expires_at");
        if(!clock.instant().isBefore(expires.toInstant(ZoneOffset.UTC))) throw failure(422,"TOKEN_EXPIRED");
        if(t.get("claim_id")!=null && !c.state().equals("PENDING")) throw failure(409,"ACTIVATION_NOT_PENDING");
    }
    public void activated(String token,String user) {
        jdbc.update("UPDATE invitation_claims SET claim_state='ACTIVATED',user_id=?,version=version+1,updated_at=? WHERE current_token_id=? AND claim_state='PENDING'",user,utc(clock.instant()),token);
        jdbc.update("UPDATE activation_mail_outbox m JOIN one_time_tokens t ON t.id=m.token_id SET m.state='CANCELLED',m.encrypted_payload=NULL,m.key_id=NULL,m.nonce=NULL,m.tag=NULL,m.version=m.version+1 WHERE t.id=? AND m.state IN ('QUEUED','RETRY_WAIT')",token);
    }
    public static String canonical(String email) {return email==null?"":email.trim().toLowerCase(Locale.ROOT);}
}
