package vn.utbridora.auth.onboarding;

import java.time.*;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.operations.*;
import static vn.utbridora.auth.operations.WorkflowValues.*;
import static vn.utbridora.auth.onboarding.StudentImportContracts.*;

@Service
public class StudentActivationResend {
    private final StudentImportService s;
    public StudentActivationResend(StudentImportService service) {s=service;}
    @Transactional public ResendAccepted resend(Authentication auth,String id,String rowId,String key,ResendActivation in) {
        var j=s.authorized(auth,id);uuid(rowId);uuid(key);version(in.expectedRowVersion());version(in.expectedInvitationVersion());
        var rows=s.jdbc.queryForList("SELECT claim_id FROM student_import_rows WHERE id=? AND job_id=?",rowId,id);
        if(rows.isEmpty()) throw failure(404,"RESOURCE_NOT_FOUND");
        return s.ledger.execute(new OperationLedger.Command(actor(auth),j.school(),"STUDENT_ACTIVATION_RESEND",key,Map.of("id",id,"rowId",rowId,"request",in),202),ResendAccepted.class,()->{
            if(rows.getFirst().get("claim_id")==null) throw failure(409,"ACTIVATION_NOT_PENDING");
            var claim=s.claims.lock(rows.getFirst().get("claim_id").toString());
            var account=s.jdbc.queryForList("SELECT status FROM users WHERE email=? FOR UPDATE",String.class,claim.email());
            if(claim.state().equals("ACTIVATED") || !account.isEmpty() && account.getFirst().equals("ACTIVE")) throw failure(409,"ACCOUNT_ALREADY_ACTIVE");
            if(!claim.context().equals("ROSTER") || !claim.state().equals("PENDING") || !account.isEmpty() && !account.getFirst().equals("PENDING_ACTIVATION")) throw failure(409,"ACTIVATION_NOT_PENDING");
            var row=s.jdbc.queryForMap("SELECT row_state,version FROM student_import_rows WHERE id=? AND job_id=? FOR UPDATE",rowId,id);
            if(!row.get("row_state").equals("PROVISIONED")) throw failure(409,"ACTIVATION_NOT_PENDING");
            long rowVersion=((Number)row.get("version")).longValue();
            if(rowVersion!=in.expectedRowVersion() || claim.version()!=in.expectedInvitationVersion()) throw failure(409,"STATE_CONFLICT");
            Instant now=s.clock.instant();
            if(s.jdbc.queryForObject("SELECT COUNT(*) FROM activation_mail_outbox WHERE claim_id=? AND state='SENDING' AND lease_until>?",Integer.class,claim.id(),utc(now))>0) throw failure(409,"MAIL_SEND_IN_PROGRESS");
            Instant ready=claim.lastIssued()==null?now:claim.lastIssued().plusSeconds(300);
            var manual=s.jdbc.query("SELECT created_at FROM activation_mail_outbox WHERE claim_id=? AND issuance_reason='MANUAL_RESEND' AND created_at>? ORDER BY created_at FOR UPDATE",(r,n)->instant(r,"created_at"),claim.id(),utc(now.minusSeconds(86400)));
            if(manual.size()>=3 && manual.get(manual.size()-3).plusSeconds(86400).isAfter(ready)) ready=manual.get(manual.size()-3).plusSeconds(86400);
            if(now.isBefore(ready)) throw new OperationFailure(429,"RESEND_RATE_LIMITED","Vui lòng chờ trước khi gửi lại thư kích hoạt.",(int)Math.max(1,(Duration.between(now,ready).toMillis()+999)/1000));
            var issued=s.claims.issueRoster(claim,key,actor(auth),"MANUAL_RESEND");
            String mail=s.jdbc.queryForObject("SELECT id FROM activation_mail_outbox WHERE claim_id=? AND generation=?",String.class,issued.id(),issued.generation());
            s.audit.workflow(actor(auth),"ACTIVATION_INVITATION_REISSUED",claim.id(),j.school(),Map.of("rowId",rowId,"operationId",uuid(key),"reasonCode","MANUAL_RESEND","expiresAt",now.plusSeconds(172800).toString()));
            return new ResendAccepted(rowId,rowVersion,issued.version(),mail,"QUEUED",now);
        },ResendAccepted::mailId);
    }
}
