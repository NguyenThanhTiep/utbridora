package vn.utbridora.auth.tokens;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.organization.*;
import vn.utbridora.auth.operations.OperationLedger;
import vn.utbridora.auth.user.*;
import static vn.utbridora.auth.operations.WorkflowValues.*;
@Service
public class MentorInvitationService implements MentorActivationPort {
    public record MentorInviteRequest(String email,String enterpriseOrgId,List<String> groupIds,String termId,String decisionRef) {}
    public record MentorInviteDto(String invitationId,long version,String state,String enterpriseOrgId,List<String> groupIds,String termId,Instant expiresAt) {}
    public record Accept(Long expectedVersion) {}
    public record Revoke(Long expectedVersion,String reasonCode) {}
    private record Stored(String id,String email,String userId,String tokenId,String hr,String state,long version,MentorScopePort.InviteScope scope,List<String> assignments,Instant expires) {
        MentorInviteDto dto() {return new MentorInviteDto(id,version,state,scope.enterpriseOrgId(),scope.groupIds(),scope.termId(),state.equals("REVIEW_REQUIRED")?null:expires);}
    }
    private java.time.Clock clock=java.time.Clock.systemUTC();
    private final JdbcTemplate jdbc;private final MentorScopePort workspace;private final UserRepository users;private final MembershipRepository memberships;
    private final TokenRepository tokens;private final EmailService email;private final AuthorizationPolicy core;private final OperationLedger ledger;private final AuditService audit;
    public MentorInvitationService(JdbcTemplate jdbc,MentorScopePort workspace,UserRepository users,MembershipRepository memberships,TokenRepository tokens,EmailService email,AuthorizationPolicy core,OperationLedger ledger,AuditService audit) {this.jdbc=jdbc;this.workspace=workspace;this.users=users;this.memberships=memberships;this.tokens=tokens;this.email=email;this.core=core;this.ledger=ledger;this.audit=audit;}
    @Transactional public MentorInviteDto issue(Authentication auth,String key,MentorInviteRequest request) {
        String address=text(request.email(),254).toLowerCase(Locale.ROOT);if(!address.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw failure(400,"SCHEMA_VALIDATION");text(request.decisionRef(),120);
        var scope=workspace.validateInvite(auth,request.enterpriseOrgId(),request.termId(),request.groupIds());
        return ledger.execute(new OperationLedger.Command(actor(auth),scope.enterpriseOrgId(),"MENTOR_INVITATION_ISSUE",key,request,202),MentorInviteDto.class,()->{
            lockPendingIdentity(address);
            var user=users.findByEmailForUpdate(address).orElse(null);
            String claim=jdbc.queryForObject("SELECT invitation_id FROM mentor_onboarding_claims WHERE email=? FOR UPDATE",String.class,address);
            var pending=claim==null?null:read(claim);
            boolean same=pending!=null && Set.of("NEW_IDENTITY_PENDING","ACCOUNT_PENDING").contains(pending.state()) && sameScope(scope,pending.scope());
            if(user!=null && user.status!=AccountStatus.ACTIVE && user.status!=AccountStatus.PENDING_ACTIVATION)
                return review(auth,address,user.id,scope);
            if(user!=null && user.status==AccountStatus.ACTIVE) {
                String id=UUID.randomUUID().toString();Instant expiry=clock.instant().plusSeconds(48*3600);
                jdbc.update("INSERT INTO mentor_invitation_refs VALUES(?,?,?,?,?,?,1,?,?,?)",id,address,user.id,null,actor(auth),"MEMBERSHIP_PENDING",JSON.writeValueAsString(scope),"[]",utc(expiry));
                audit.writeScoped(actor(auth),"INVITATION_CREATED","account_invitation",id,"SUCCESS",scope.enterpriseOrgId(),"MENTOR_INVITATION");return read(id).dto();
            }
            if((pending!=null && !same) || user!=null && !same) return review(auth,address,user==null?null:user.id,scope);
            if(!same && jdbc.queryForObject("SELECT COUNT(*) FROM one_time_tokens WHERE email=? AND purpose='INVITATION' AND consumed_at IS NULL AND expires_at>?",Integer.class,address,utc(clock.instant()))>0)
                return review(auth,address,null,scope);
            Instant now=clock.instant();
            if(same) enforceReissueLimit(address,now);
            String id=same?pending.id():UUID.randomUUID().toString();
            if(same && pending.tokenId()!=null) tokens.findById(pending.tokenId()).ifPresent(old->{old.consumedAt=now;tokens.saveAndFlush(old);});
            String raw=TokenCodec.generate();var token=new OneTimeToken();token.purpose=OneTimeToken.Purpose.INVITATION;
            token.email=address;token.userId=user==null?null:user.id;token.inviteRole=Role.ENTERPRISE;token.organizationId=scope.enterpriseOrgId();
            token.tokenHash=TokenCodec.hash(raw);token.expiresAt=now.plusSeconds(48*3600);tokens.saveAndFlush(token);
            String state=same?"ACCOUNT_PENDING":"NEW_IDENTITY_PENDING";
            if(same) {
                jdbc.update("UPDATE mentor_invitation_refs SET token_id=?,user_id=?,state=?,version=version+1,expires_at=? WHERE id=?",token.id,user==null?null:user.id,state,utc(token.expiresAt),id);
                jdbc.update("INSERT INTO mentor_manual_reissues(id,email,issued_at) VALUES(?,?,?)",UUID.randomUUID().toString(),address,utc(now));
                audit.workflow(actor(auth),"ACTIVATION_INVITATION_REISSUED",id,scope.enterpriseOrgId(),Map.of("reasonCode","MENTOR_REISSUE","expiresAt",token.expiresAt.toString()));
            } else {
                jdbc.update("INSERT INTO mentor_invitation_refs VALUES(?,?,?,?,?,?,1,?,?,?)",id,address,null,token.id,actor(auth),state,JSON.writeValueAsString(scope),"[]",utc(token.expiresAt));
                audit.writeScoped(actor(auth),"INVITATION_CREATED","account_invitation",id,"SUCCESS",scope.enterpriseOrgId(),"MENTOR_INVITATION");
            }
            jdbc.update("UPDATE mentor_onboarding_claims SET invitation_id=?,last_issued_at=? WHERE email=?",id,utc(now),address);
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){
                try {email.invite(address,raw);} catch(org.springframework.mail.MailException unavailable) {
                    org.slf4j.LoggerFactory.getLogger(MentorInvitationService.class).warn("Mentor invitation mail unavailable; authorized reissue remains available");
                }
            }});
            return read(id).dto();
        },MentorInviteDto::invitationId);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void lockPendingIdentity(String address) {
        jdbc.update("INSERT INTO mentor_onboarding_claims(email,invitation_id,last_issued_at) VALUES(?,NULL,NULL) ON DUPLICATE KEY UPDATE email=email",address);
        jdbc.queryForList("SELECT email FROM mentor_onboarding_claims WHERE email=? FOR UPDATE",String.class,address);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public boolean permitsPendingIdentity(String token,String address,String user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM mentor_onboarding_claims c JOIN mentor_invitation_refs r ON r.id=c.invitation_id WHERE c.email=? AND r.email=? AND r.token_id=? AND r.user_id=? AND r.state='ACCOUNT_PENDING'",Integer.class,address,address,token,user)==1;
    }
    private MentorInviteDto review(Authentication auth,String address,String user,MentorScopePort.InviteScope scope) {
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO mentor_invitation_refs VALUES(?,?,?,?,?,?,1,?,?,?)",id,address,user,null,actor(auth),"REVIEW_REQUIRED",JSON.writeValueAsString(scope),"[]",utc(clock.instant().plusSeconds(48*3600)));
        audit.writeScoped(actor(auth),"INVITATION_CREATED","account_invitation",id,"SUCCESS",scope.enterpriseOrgId(),"MENTOR_REVIEW_REQUIRED");return read(id).dto();
    }
    private static boolean sameScope(MentorScopePort.InviteScope a,MentorScopePort.InviteScope b) {
        return a.hrAssignmentId().equals(b.hrAssignmentId()) && a.hrUserId().equals(b.hrUserId()) && a.enterpriseOrgId().equals(b.enterpriseOrgId())
            && a.schoolOrgId().equals(b.schoolOrgId()) && a.termId().equals(b.termId()) && new HashSet<>(a.groupIds()).equals(new HashSet<>(b.groupIds()));
    }
    private void enforceReissueLimit(String address,Instant now) {
        Instant issued=jdbc.queryForObject("SELECT last_issued_at FROM mentor_onboarding_claims WHERE email=? FOR UPDATE",(r,n)->instant(r,"last_issued_at"),address);
        var manual=jdbc.query("SELECT issued_at FROM mentor_manual_reissues WHERE email=? AND issued_at>? ORDER BY issued_at FOR UPDATE",(r,n)->instant(r,"issued_at"),address,utc(now.minusSeconds(86400)));
        Instant ready=issued==null?now:issued.plusSeconds(300);
        if(manual.size()>=3 && manual.get(manual.size()-3).plusSeconds(86400).isAfter(ready)) ready=manual.get(manual.size()-3).plusSeconds(86400);
        if(now.isBefore(ready)) throw new vn.utbridora.auth.operations.OperationFailure(429,"RESEND_RATE_LIMITED","Chưa thể cấp lại lời mời. Vui lòng thử sau.",(int)Math.max(1,(java.time.Duration.between(now,ready).toMillis()+999)/1000));
    }
    @Transactional public MentorInviteDto accept(Authentication auth,String id,String key,Accept request) {
        var before=read(uuid(id));if(!actor(auth).equals(before.userId()) || !core.isSelf(auth,before.userId())) throw failure(404,"RESOURCE_NOT_FOUND");version(request.expectedVersion());
        return ledger.execute(new OperationLedger.Command(actor(auth),before.scope().enterpriseOrgId(),"MENTOR_INVITATION_ACCEPT",key,Map.of("id",id,"request",request),200),MentorInviteDto.class,()->{
            var current=read(id);if(current.version()!=request.expectedVersion()) throw failure(409,"VERSION_CONFLICT");if(!"MEMBERSHIP_PENDING".equals(current.state())) throw failure(409,"STATE_CONFLICT");if(!clock.instant().isBefore(current.expires())) throw failure(409,"INVITATION_EXPIRED");
            activate(current,actor(auth));return read(id).dto();
        },MentorInviteDto::invitationId);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY) public void activated(String tokenId,String userId) {
        var ids=jdbc.queryForList("SELECT id FROM mentor_invitation_refs WHERE token_id=?",String.class,uuid(tokenId));
        if(ids.isEmpty()) return;var invite=read(ids.getFirst());if(!Set.of("NEW_IDENTITY_PENDING","ACCOUNT_PENDING").contains(invite.state()) || !clock.instant().isBefore(invite.expires())) throw failure(409,"INVITATION_EXPIRED");
        var user=users.findById(userId).orElseThrow();if(!user.email.equals(invite.email())) throw failure(403,"ACCESS_DENIED");activate(invite,userId);
    }
    private void activate(Stored invite,String user) {
        var membership=memberships.findByUserIdAndOrganizationIdAndRole(user,invite.scope().enterpriseOrgId(),Role.ENTERPRISE).orElseGet(Membership::new);
        membership.userId=user;membership.organizationId=invite.scope().enterpriseOrgId();membership.role=Role.ENTERPRISE;membership.active=true;memberships.saveAndFlush(membership);
        var assignments=workspace.activateAccepted(invite.id(),user,invite.scope());
        jdbc.update("UPDATE mentor_invitation_refs SET user_id=?,state='ACCEPTED',version=version+1,assignment_ids=? WHERE id=?",user,JSON.writeValueAsString(assignments),invite.id());
        audit.writeScoped(user,"MENTOR_INVITATION_ACCEPTED","account_invitation",invite.id(),"SUCCESS",invite.scope().enterpriseOrgId(),"MENTOR_OWNER_ACCEPTED");
    }
    @Transactional public MentorInviteDto revoke(Authentication auth,String id,String key,Revoke request) {
        uuid(id);String address=jdbc.queryForObject("SELECT email FROM mentor_invitation_refs WHERE id=?",String.class,id);lockPendingIdentity(address);
        var before=read(id);version(request.expectedVersion());reason(request.reasonCode());
        workspace.validateInvite(auth,before.scope().enterpriseOrgId(),before.scope().termId(),before.scope().groupIds());if(!actor(auth).equals(before.hr())) throw failure(404,"RESOURCE_NOT_FOUND");
        return ledger.execute(new OperationLedger.Command(actor(auth),before.scope().enterpriseOrgId(),"MENTOR_INVITATION_REVOKE",key,Map.of("id",id,"request",request),200),MentorInviteDto.class,()->{
            var invite=read(id);if(invite.version()!=request.expectedVersion() || invite.state().equals("REVOKED")) throw failure(409,"VERSION_CONFLICT");
            if(invite.state().equals("ACCEPTED")) workspace.revokeAccepted(id,invite.scope());
            if(invite.tokenId()!=null) tokens.findById(invite.tokenId()).ifPresent(t->t.consumedAt=clock.instant());
            jdbc.update("UPDATE mentor_invitation_refs SET state='REVOKED',version=version+1 WHERE id=?",id);audit.writeScoped(actor(auth),"ACCOUNT_INVITATION_REVOKED","account_invitation",id,"SUCCESS",invite.scope().enterpriseOrgId(),request.reasonCode());return read(id).dto();
        },MentorInviteDto::invitationId);
    }
    @Transactional public vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page<MentorInviteDto> mine(Authentication auth,int page,int size) {
        page(page,size);if(!core.isSelf(auth,actor(auth))) throw failure(401,"SESSION_EXPIRED");
        var ids=jdbc.queryForList("SELECT id FROM mentor_invitation_refs WHERE user_id=? ORDER BY id",String.class,actor(auth));int from=Math.min(page*size,ids.size());
        return new vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page<>(ids.subList(from,Math.min(from+size,ids.size())).stream().map(this::read).map(Stored::dto).toList(),page,size,ids.size(),(ids.size()+size-1)/size);
    }
    private Stored read(String id) {return jdbc.query("SELECT * FROM mentor_invitation_refs WHERE id=? FOR UPDATE",(r,n)->new Stored(r.getString("id"),r.getString("email"),r.getString("user_id"),r.getString("token_id"),r.getString("hr_user_id"),r.getString("state"),r.getLong("version"),JSON.readValue(r.getString("scope_json"),MentorScopePort.InviteScope.class),List.of(JSON.readValue(r.getString("assignment_ids"),String[].class)),instant(r,"expires_at")),id).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));}
}
