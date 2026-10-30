package vn.utbridora.auth.security;

import java.time.Instant;
import java.util.*;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import vn.utbridora.auth.user.*;
import vn.utbridora.auth.organization.*;

@Service
public class AppUserDetailsService implements UserDetailsService {
    private final UserRepository users;
    private final MembershipRepository memberships;
    public AppUserDetailsService(UserRepository users, MembershipRepository memberships) {
        this.users=users; this.memberships=memberships;
    }
    @Override public UserDetails loadUserByUsername(String email) {
        UserAccount user=users.findByEmailIgnoreCase(email.trim()).orElseThrow(()->new UsernameNotFoundException("Invalid credentials"));
        if(user.status != AccountStatus.ACTIVE) throw new DisabledException("Account not active");
        if(user.lockedUntil!=null && user.lockedUntil.isAfter(Instant.now())) throw new LockedException("Temporarily locked");
        var authorities=memberships.findByUserIdAndActiveTrue(user.id).stream()
            .map(m->new SimpleGrantedAuthority("ROLE_"+m.role.name()))
            .distinct().map(a->(org.springframework.security.core.GrantedAuthority)a).toList();
        return new AppPrincipal(user.id,user.email,user.passwordHash,user.securityVersion,authorities);
    }
}
