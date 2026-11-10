package vn.utbridora.auth.bootstrap;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.utbridora.auth.core.PasswordPolicy;
import vn.utbridora.auth.user.*;
import vn.utbridora.auth.organization.*;

@Configuration
public class AdminBootstrap {
    @Bean CommandLineRunner createAdminIfConfigured(UserRepository users,MembershipRepository memberships,
            PasswordEncoder encoder,@Value("${app.bootstrap.admin.email:}") String email,
            @Value("${app.bootstrap.admin.password:}") String password,
            org.springframework.transaction.support.TransactionTemplate transactions,
            org.springframework.jdbc.core.JdbcTemplate jdbc, vn.utbridora.auth.audit.AuditService audit) {
        return args->{
            if(email.isBlank() && password.isBlank()) return;
            if(email.isBlank() || password.isBlank()) throw new IllegalStateException("Set both bootstrap admin credentials");
            PasswordPolicy.requireValid(password);
            String address=email.trim().toLowerCase(Locale.ROOT);
            transactions.executeWithoutResult(transaction -> {
            jdbc.queryForObject("SELECT lock_name FROM core_locks WHERE lock_name='GLOBAL_ADMIN' FOR UPDATE",String.class);
            if(users.existsByEmailIgnoreCase(address)) return;
            UserAccount user=new UserAccount();user.email=address;user.passwordHash=encoder.encode(password);
            user.displayName="Platform Admin";user.status=AccountStatus.ACTIVE;
            users.save(user);
            Membership member=new Membership();member.userId=user.id;member.role=Role.ADMIN;
            memberships.save(member);
            audit.writeScoped(user.id,"ACCOUNT_ACTIVATED","user",user.id,"SUCCESS",null,"BOOTSTRAP");
            audit.writeScoped(user.id,"MEMBERSHIP_GRANTED","membership",member.id,"SUCCESS",null,"BOOTSTRAP");
            });
            // Remove bootstrap credentials from the environment after first successful startup.
        };
    }
}
