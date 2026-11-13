package vn.utbridora.auth.tokens;

import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.user.*;
import vn.utbridora.auth.organization.*;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.core.PasswordPolicy;
import vn.utbridora.auth.tokens.OneTimeToken.Purpose;

@Service
public class AccountTokenService {
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<MentorActivationPort> mentorActivation;
    private final TokenRepository tokens; private final UserRepository users;
    private final MembershipRepository memberships; private final OrganizationRepository organizations;
    private final PasswordEncoder encoder; private final EmailService email; private final AuditService audit;
    public AccountTokenService(TokenRepository tokens,UserRepository users,MembershipRepository memberships,
            OrganizationRepository organizations,PasswordEncoder encoder,EmailService email,AuditService audit) {
        this.tokens=tokens;this.users=users;this.memberships=memberships;this.organizations=organizations;
        this.encoder=encoder;this.email=email;this.audit=audit;
    }
    @Transactional
    @PreAuthorize("@authorizationPolicy.hasGlobal(authentication, 'membership:admin:write')")
    public void invite(String senderId,String requestedEmail,Role role,String organizationId) {
        String addr=requestedEmail.trim().toLowerCase(java.util.Locale.ROOT);
        if(users.existsByEmailIgnoreCase(addr)) throw new IllegalStateException("Email already registered: use role-assignment workflow");
        if(role==Role.ADMIN || role==Role.ENTERPRISE || role==Role.SCHOOL_STAFF || role==Role.STUDENT) {
            if(role!=Role.ADMIN && organizationId==null) throw new IllegalArgumentException("Organization is required");
            if(role==Role.ADMIN && organizationId!=null) throw new IllegalArgumentException("Admin has no organization scope");
            if(organizationId!=null){
                var org=organizations.findById(organizationId).orElseThrow(()->new IllegalArgumentException("Organization not found"));
                if((role==Role.ENTERPRISE && org.type!=Organization.Type.ENTERPRISE)
                    || ((role==Role.SCHOOL_STAFF || role==Role.STUDENT) && org.type!=Organization.Type.UNIVERSITY))
                    throw new IllegalArgumentException("Role incompatible with organization type");
            }
        }
        String raw=TokenCodec.generate();
        OneTimeToken token=new OneTimeToken(); token.purpose=Purpose.INVITATION;
        token.tokenHash=TokenCodec.hash(raw);token.email=addr;token.inviteRole=role;
        token.organizationId=organizationId;token.expiresAt=Instant.now().plusSeconds(48*3600);
        tokens.save(token);
        // For production, replace direct mail with a transaction-safe outbox/queue.
        email.invite(addr,raw);
        audit.write(senderId,"INVITATION_CREATED","user",null,"SUCCESS");
    }
    @Transactional
    public void acceptInvitation(String raw,String displayName,String password) {
        PasswordPolicy.requireValid(password);
        var candidate=tokens.findByTokenHashAndPurpose(TokenCodec.hash(raw),Purpose.INVITATION)
            .orElseThrow(()->new IllegalArgumentException("Invalid or expired token"));
        var mentor=mentorActivation==null?null:mentorActivation.getIfAvailable();
        if(mentor!=null) mentor.lockPendingIdentity(candidate.email);
        var existing=users.findByEmailForUpdate(candidate.email).orElse(null);
        OneTimeToken token=valid(raw,Purpose.INVITATION);
        // A request may have loaded this entity before waiting for the claim.
        // Check the current database row even when JPA still holds an old token.
        Instant consumed=Instant.now();
        if(tokens.consumeIfUsable(token.id,consumed)!=1) throw new IllegalArgumentException("Invalid or expired token");
        token.consumedAt=consumed;
        if(existing!=null && (existing.status!=AccountStatus.PENDING_ACTIVATION || mentor==null
            || !mentor.permitsPendingIdentity(token.id,token.email,existing.id))) throw new IllegalStateException("Invitation no longer valid");
        UserAccount user=existing==null?new UserAccount():existing;user.email=token.email;user.passwordHash=encoder.encode(password);
        user.displayName=displayName.trim();user.status=AccountStatus.ACTIVE;
        users.save(user);
        Membership member=memberships.findByUserIdAndOrganizationIdAndRole(user.id,token.organizationId,token.inviteRole).orElseGet(Membership::new);member.active=true;member.userId=user.id;member.role=token.inviteRole;
        member.organizationId=token.organizationId;memberships.save(member);
        users.flush();memberships.flush();
        if(mentorActivation!=null) mentorActivation.ifAvailable(port->port.activated(token.id,user.id));
        audit.write(user.id,"ACCOUNT_ACTIVATED","user",user.id,"SUCCESS");
    }
    @Transactional
    public void forgotPassword(String requestedEmail) {
        String emailAddr=requestedEmail.trim().toLowerCase(java.util.Locale.ROOT);
        users.findByEmailForUpdate(emailAddr).filter(u->u.status==AccountStatus.ACTIVE).ifPresent(u->{
            for(OneTimeToken old:tokens.findByUserIdAndPurposeAndConsumedAtIsNull(u.id,Purpose.PASSWORD_RESET)) old.consumedAt=Instant.now();
            String raw=TokenCodec.generate();
            OneTimeToken token=new OneTimeToken();token.userId=u.id;token.email=u.email;
            token.purpose=Purpose.PASSWORD_RESET;token.tokenHash=TokenCodec.hash(raw);
            token.expiresAt=Instant.now().plusSeconds(30*60);
            tokens.save(token);
            email.reset(u.email,raw);
        });
    }
    @Transactional
    public void resetPassword(String raw,String newPassword) {
        PasswordPolicy.requireValid(newPassword);
        // All reset issuance/consumption locks the USER before any token to prevent lost updates/deadlocks.
        OneTimeToken candidate=tokens.findByTokenHashAndPurpose(TokenCodec.hash(raw),Purpose.PASSWORD_RESET)
            .orElseThrow(()->new IllegalArgumentException("Invalid or expired token"));
        UserAccount user=users.findByIdForUpdate(candidate.userId).orElseThrow(()->new IllegalArgumentException("Invalid or expired token"));
        OneTimeToken token=valid(raw,Purpose.PASSWORD_RESET);
        // Atomic predicate also protects against a repeatable-read snapshot loaded before the user lock.
        Instant consumed=Instant.now();
        if(tokens.consumeIfUsable(token.id,consumed)!=1) throw new IllegalArgumentException("Invalid or expired token");
        token.consumedAt=consumed;
        if(user.status!=AccountStatus.ACTIVE) throw new IllegalArgumentException("Invalid or expired token");
        user.passwordHash=encoder.encode(newPassword);
        user.securityVersion++;
        user.failedAttempts=0;user.lockedUntil=null;
        for(OneTimeToken t:tokens.findByUserIdAndPurposeAndConsumedAtIsNull(user.id,Purpose.PASSWORD_RESET)) t.consumedAt=Instant.now();
        audit.write(user.id,"PASSWORD_RESET","user",user.id,"SUCCESS");
    }
    private OneTimeToken valid(String raw,Purpose purpose) {
        OneTimeToken token=tokens.findForUpdate(TokenCodec.hash(raw),purpose)
            .orElseThrow(()->new IllegalArgumentException("Invalid or expired token"));
        requireUsable(token);
        return token;
    }
    private void requireUsable(OneTimeToken token) {
        if(token.consumedAt!=null || !token.expiresAt.isAfter(Instant.now()))
            throw new IllegalArgumentException("Invalid or expired token");
    }
}
