package vn.utbridora.auth.tokens;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.contracts.onboarding.AccountProvisioningPort.RosterActivationProof;
import vn.utbridora.auth.modules.student.port.StudentRosterProvisionPort;
import vn.utbridora.auth.user.*;
import vn.utbridora.auth.organization.*;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** Identity and T2 binding commit together. Events only reconcile an already bound identity. */
@Service
@Transactional(propagation=Propagation.MANDATORY)
public class RosterAccountActivation {
    private final InvitationClaims claims;private final StudentRosterProvisionPort rosters;private final UserRepository users;
    private final MembershipRepository memberships;private final TokenRepository tokens;private final PasswordEncoder passwords;
    private final AuditService audit;private final JdbcTemplate jdbc;private final Clock clock;
    public RosterAccountActivation(InvitationClaims claims,StudentRosterProvisionPort rosters,UserRepository users,
        MembershipRepository memberships,TokenRepository tokens,PasswordEncoder passwords,AuditService audit,JdbcTemplate jdbc,
        @Qualifier("onboardingClock") Clock clock) {this.claims=claims;this.rosters=rosters;this.users=users;this.memberships=memberships;this.tokens=tokens;this.passwords=passwords;this.audit=audit;this.jdbc=jdbc;this.clock=clock;}
    public boolean accept(OneTimeToken candidate,String displayName,String password) {
        var claim=claims.lockEmail(candidate.email);
        if(!claim.context().equals("ROSTER")) return false;
        claims.requireCurrentToken(candidate.tokenHash);
        var existing=users.findByEmailForUpdate(claim.email()).orElse(null);
        if(existing!=null && (existing.status!=AccountStatus.PENDING_ACTIVATION || !existing.id.equals(claim.expectedUser())) || existing==null && claim.expectedUser()!=null) throw failure(409,"ACTIVATION_NOT_PENDING");
        var proof=new RosterActivationProof(claim.id(),candidate.tokenHash);
        var roster=rosters.readForActivation(proof,claim.roster());
        if(displayName==null) {
            var names=jdbc.queryForList("SELECT JSON_UNQUOTE(JSON_EXTRACT(normalized_snapshot,'$.fullName')) FROM student_import_rows WHERE claim_id=? ORDER BY job_id,row_no LIMIT 1",String.class,claim.id());
            if(names.isEmpty()) throw failure(409,"ACTIVATION_NOT_PENDING");displayName=names.getFirst();
        }
        if(displayName.isBlank() || displayName.codePointCount(0,displayName.length())>150) throw failure(400,"SCHEMA_VALIDATION");
        var user=existing==null?new UserAccount():existing;user.email=claim.email();user.displayName=displayName.trim();
        user.passwordHash=passwords.encode(password);user.status=AccountStatus.ACTIVE;users.saveAndFlush(user);
        var membership=memberships.findByUserIdAndOrganizationIdAndRole(user.id,claim.school(),Role.STUDENT).orElseGet(Membership::new);
        membership.userId=user.id;membership.organizationId=claim.school();membership.role=Role.STUDENT;membership.active=true;memberships.saveAndFlush(membership);
        String operation=UUID.randomUUID().toString();
        var binding=rosters.bindActivatedUser(proof,new StudentRosterProvisionPort.BindActivatedUser(roster.rosterId(),user.id,roster.rosterVersion(),operation));
        claims.requireCurrentToken(candidate.tokenHash);
        if(tokens.consumeIfUsable(candidate.id,clock.instant())!=1) throw failure(409,"TOKEN_ALREADY_USED");
        candidate.consumedAt=clock.instant();claims.activated(candidate.id,user.id);
        var activated=claims.lock(claim.id());String eventId=UUID.randomUUID().toString();
        var event=new LinkedHashMap<String,Object>();event.put("eventId",eventId);event.put("eventType","StudentAccountActivated");event.put("schemaVersion",1);
        event.put("aggregateId",user.id);event.put("aggregateVersion",activated.version());event.put("userId",user.id);event.put("rosterId",binding.rosterId());
        event.put("rosterVersion",binding.rosterVersion());event.put("schoolOrgId",claim.school());event.put("termIds",binding.linkedTermIds());event.put("occurredAt",clock.instant());event.put("traceId",operation);
        jdbc.update("INSERT INTO student_activation_outbox(event_id,aggregate_id,aggregate_version,roster_id,payload) VALUES(?,?,?,?,?)",eventId,user.id,activated.version(),binding.rosterId(),JSON.writeValueAsString(event));
        audit.write(user.id,"ACCOUNT_ACTIVATED","user",user.id,"SUCCESS");return true;
    }
}
