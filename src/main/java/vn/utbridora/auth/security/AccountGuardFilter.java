package vn.utbridora.auth.security;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.utbridora.auth.user.*;

// Re-checks revocation on every authenticated request. Cache this check only with explicit revocation design.
public final class AccountGuardFilter extends OncePerRequestFilter {
    private final UserRepository users;
    public AccountGuardFilter(UserRepository users){ this.users=users; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        Authentication auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null && auth.isAuthenticated() && auth.getPrincipal() instanceof AppPrincipal principal) {
            boolean allowed=users.findById(principal.userId()).map(user->
                    user.status==AccountStatus.ACTIVE && user.securityVersion==principal.securityVersion()
                    && (user.lockedUntil==null || !user.lockedUntil.isAfter(java.time.Instant.now())))
                .orElse(false);
            if(!allowed) {
                HttpSession session=request.getSession(false);
                if(session!=null) session.invalidate();
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session expired or revoked");
                return;
            }
        }
        chain.doFilter(request,response);
    }
}
