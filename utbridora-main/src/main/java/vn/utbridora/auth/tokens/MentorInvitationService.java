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
    private final JdbcTemplate jdbc;private final MentorScopePort workspace;private final UserRepository users;private final MembershipRepository memberships;
    private final TokenRepository tokens;private final EmailService email;private final AuthorizationPolicy core;private final OperationLedger ledger;private final AuditService audit;
    public MentorInvitationService(JdbcTemplate jdbc,MentorScopePort workspace,UserRepository users,MembershipRepository memberships,TokenRepository tokens,EmailService email,AuthorizationPolicy core,OperationLedger ledger,AuditService audit) {this.jdbc=jdbc;this.workspace=workspace;this.users=users;this.memberships=memberships;this.tokens=tokens;this.email=email;this.core=core;this.ledger=ledger;this.audit=audit;}
    @Transactional public MentorInviteDto issue(Authentication auth,String key,MentorInviteRequest request) {
        String address=text(request.email(),254).toLowerCase(Locale.ROOT);if(!address.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw failure(400,"SCHEMA_VALIDATION");text(request.decisionRef(),120);
        var scope=workspace.validateInvite(auth,request.enterpriseOrgId(),request.termId(),request.groupIds());
        return ledger.execute(new OperationLedger.Command(actor(auth),scope.enterpriseOrgId(),"MENTOR_INVITATION_ISSUE",key,request,202),MentorInviteDto.class,()->{
            var user=users.findByEmailForUpdate(address).orElse(null);String state=user==null?"NEW_IDENTITY_PENDING":user.status==AccountStatus.ACTIVE?"MEMBERSHIP_PENDING":"REVIEW_REQUIRED";
            String id=UUID.randomUUID().toString(),tokenId=null;Instant expires=Instant.now().plusSeconds(48*3600);
            if(user==null) {
                if(jdbc.queryForObject("SELECT COUNT(*) FROM one_time_tokens WHERE email=? AND purpose='INVITATION' AND consumed_at IS NULL AND expires_at>UTC_TIMESTAMP(6)",Integer.class,address)>0) state="REVIEW_REQUIRED";
                else {
                    String raw=TokenCodec.generate();var token=new OneTimeToken();token.purpose=OneTimeToken.Purpose.INVITATION;token.email=address;token.inviteRole=Role.ENTERPRISE;token.organizationId=scope.enterpriseOrgId();token.tokenHash=TokenCodec.hash(raw);token.expiresAt=expires;tokens.saveAndFlush(token);tokenId=token.id;
                    org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){email.invite(address,raw);}});
                }
            }
            jdbc.update("INSERT INTO mentor_invitation_refs VALUES(?,?,?,?,?,?,1,?,?,?)",id,address,user==null?null:user.id,tokenId,actor(auth),state,JSON.writeValueAsString(scope),"[]",utc(expires));
            audit.writeScoped(actor(auth),"INVITATION_CREATED","account_invitation",id,"SUCCESS",scope.enterpriseOrgId(),"MENTOR_INVITATION");return read(id).dto();
        },MentorInviteDto::invitationId);
    }
    @Transactional public MentorInviteDto accept(Authentication auth,String id,String key,Accept request) {
        var before=read(uuid(id));if(!actor(auth).equals(before.userId()) || !core.isSelf(auth,before.userId())) throw failure(404,"RESOURCE_NOT_FOUND");version(request.expectedVersion());
        return ledger.execute(new OperationLedger.Command(actor(auth),before.scope().enterpriseOrgId(),"MENTOR_INVITATION_ACCEPT",key,Map.of("id",id,"request",request),200),MentorInviteDto.class,()->{
            var current=read(id);if(current.version()!=request.expectedVersion()) throw failure(409,"VERSION_CONFLICT");if(!"MEMBERSHIP_PENDING".equals(current.state())) throw failure(409,"STATE_CONFLICT");if(!Instant.now().isBefore(current.expires())) throw failure(409,"INVITATION_EXPIRED");
            activate(current,actor(auth));return read(id).dto();
        },MentorInviteDto::invitationId);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY) public void activated(String tokenId,String userId) {
        var ids=jdbc.queryForList("SELECT id FROM mentor_invitation_refs WHERE token_id=?",String.class,uuid(tokenId));
        if(ids.isEmpty()) return;var invite=read(ids.getFirst());if(!invite.state().equals("NEW_IDENTITY_PENDING") || !Instant.now().isBefore(invite.expires())) throw failure(409,"INVITATION_EXPIRED");
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
        var before=read(uuid(id));version(request.expectedVersion());reason(request.reasonCode());
        workspace.validateInvite(auth,before.scope().enterpriseOrgId(),before.scope().termId(),before.scope().groupIds());if(!actor(auth).equals(before.hr())) throw failure(404,"RESOURCE_NOT_FOUND");
        return ledger.execute(new OperationLedger.Command(actor(auth),before.scope().enterpriseOrgId(),"MENTOR_INVITATION_REVOKE",key,Map.of("id",id,"request",request),200),MentorInviteDto.class,()->{
            var invite=read(id);if(invite.version()!=request.expectedVersion() || invite.state().equals("REVOKED")) throw failure(409,"VERSION_CONFLICT");
            if(invite.state().equals("ACCEPTED")) workspace.revokeAccepted(id,invite.scope());
            if(invite.tokenId()!=null) tokens.findById(invite.tokenId()).ifPresent(t->t.consumedAt=Instant.now());
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
