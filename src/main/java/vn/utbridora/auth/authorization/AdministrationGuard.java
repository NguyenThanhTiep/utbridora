package vn.utbridora.auth.authorization;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.security.AppPrincipal;

/** Always acquire this database lock BEFORE loading the target user or membership. */
@Component
public class AdministrationGuard {
    private final JdbcTemplate jdbc;
    private final AuthorizationPolicy policy;
    public AdministrationGuard(JdbcTemplate jdbc, AuthorizationPolicy policy) {
        this.jdbc=jdbc; this.policy=policy;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void lockAndCheckActor(String actorId, String permission) {
        jdbc.queryForObject("SELECT lock_name FROM core_locks WHERE lock_name='GLOBAL_ADMIN' FOR UPDATE", String.class);
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !(auth.getPrincipal() instanceof AppPrincipal p)
                || !p.userId().equals(actorId) || !policy.hasGlobal(auth,permission)) {
            throw new AccessDeniedException("Administrator authorization is no longer valid");
        }
    }
}
