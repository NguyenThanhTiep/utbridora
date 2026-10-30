package vn.utbridora.auth.core;

import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.CredentialsExpiredException;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.user.*;

@Service
public class AccountService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    public AccountService(UserRepository users, PasswordEncoder encoder, AuditService audit){this.users=users;this.encoder=encoder;this.audit=audit;}
    @Transactional
    public void failedLogin(String email) {
        users.findByEmailForUpdate(email.trim()).ifPresent(u->{
            if(u.status!=AccountStatus.ACTIVE) return;
            if(u.lockedUntil!=null && u.lockedUntil.isAfter(Instant.now())) return;
            u.failedAttempts++;
            if(u.failedAttempts>=5) {u.lockedUntil=Instant.now().plusSeconds(15*60);u.failedAttempts=0;}
        });
    }
    @Transactional public void successfulLogin(AppPrincipal principal) {
        UserAccount user=users.findByIdForUpdate(principal.userId()).orElseThrow(()->new CredentialsExpiredException("Account unavailable"));
        requireCurrent(user,principal.securityVersion());
        if(user.lockedUntil!=null && user.lockedUntil.isAfter(Instant.now()))
            throw new CredentialsExpiredException("Account unavailable");
        user.failedAttempts=0;user.lockedUntil=null;
        audit.write(user.id,"LOGIN","user",user.id,"SUCCESS");
    }
    @Transactional public void changePassword(String userId,long securityVersion,String currentPassword,String newPassword) {
        PasswordPolicy.requireValid(newPassword);
        UserAccount user=users.findByIdForUpdate(userId).orElseThrow(()->new IllegalStateException("Account not found"));
        requireCurrent(user,securityVersion);
        if(!encoder.matches(currentPassword,user.passwordHash)) throw new IllegalArgumentException("Current password is incorrect");
        if(encoder.matches(newPassword,user.passwordHash)) throw new IllegalArgumentException("New password must differ from current password");
        user.passwordHash=encoder.encode(newPassword);
        user.securityVersion++;
        audit.write(userId,"PASSWORD_CHANGED","user",userId,"SUCCESS");
    }
    private void requireCurrent(UserAccount user,long version) {
        if(user.status!=AccountStatus.ACTIVE || user.securityVersion!=version)
            throw new CredentialsExpiredException("Session expired or revoked");
    }
}
