package vn.utbridora.auth.api;

import java.util.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.core.context.*;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.EffectivePermissionService;
import vn.utbridora.auth.core.AccountService;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.user.*;
import vn.utbridora.auth.organization.*;
import vn.utbridora.auth.tokens.AccountTokenService;
import vn.utbridora.auth.api.ApiDtos.*;

@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager authManager; private final SecurityContextRepository contexts;
    private final CsrfTokenRepository csrfTokens; private final AccountService accounts;
    private final UserRepository users; private final MembershipRepository memberships;
    private final AccountTokenService tokens; private final AuditService audit;
    private final EffectivePermissionService effectivePermissions;
    private final SessionAuthenticationStrategy sessionStrategy;
    public AuthController(AuthenticationManager authManager,SecurityContextRepository contexts,
            CsrfTokenRepository csrfTokens,AccountService accounts,UserRepository users,
            MembershipRepository memberships,AccountTokenService tokens,AuditService audit,
            EffectivePermissionService effectivePermissions, SessionAuthenticationStrategy sessionStrategy) {
        this.authManager=authManager;this.contexts=contexts;this.csrfTokens=csrfTokens;
        this.accounts=accounts;this.users=users;this.memberships=memberships;
        this.tokens=tokens;this.audit=audit;this.effectivePermissions=effectivePermissions;
        this.sessionStrategy=sessionStrategy;
    }
    @GetMapping("/csrf")
    public Map<String,String> csrf(HttpServletRequest request) {
        CsrfToken token=(CsrfToken)request.getAttribute("_csrf");
        if(token==null) throw new IllegalStateException("CSRF filter is not configured");
        return Map.of("headerName",token.getHeaderName(),"token",token.getToken());
    }
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody Login body,HttpServletRequest request,HttpServletResponse response) {
        try {
            if(body.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
                throw new BadCredentialsException("Invalid credentials");
            Authentication authentication=authManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(body.email().trim().toLowerCase(Locale.ROOT),body.password()));
            AppPrincipal principal=(AppPrincipal)authentication.getPrincipal();
            accounts.successfulLogin(principal);
            sessionStrategy.onAuthentication(authentication,request,response);
            SecurityContext context=SecurityContextHolder.createEmptyContext();context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contexts.saveContext(context,request,response); // mandatory for manually-authenticated requests
            return ResponseEntity.ok(meView(principal.userId()));
        } catch(AuthenticationException error) {
            accounts.failedLogin(body.email());
            audit.write(null,"LOGIN","user",null,"FAILURE");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error","INVALID_CREDENTIALS","message","Invalid credentials or account unavailable"));
        }
    }
    @PostMapping("/logout")
    public ResponseEntity<?> logout(Authentication auth,HttpServletRequest request,HttpServletResponse response) {
        if(auth!=null && auth.getPrincipal() instanceof AppPrincipal p)
            audit.write(p.userId(),"LOGOUT","user",p.userId(),"SUCCESS");
        new SecurityContextLogoutHandler().logout(request,response,auth);
        // Spring Session expires its configured cookie with the same path/security attributes.
        return ResponseEntity.ok(new Message("Logged out"));
    }
    @GetMapping("/me")
    public Me me(Authentication auth){ return meView(principal(auth).userId()); }
    @GetMapping("/me/permissions")
    public List<EffectivePermissionService.PermissionGrant> permissions(Authentication auth){
        return effectivePermissions.forUser(principal(auth).userId());
    }
    @PostMapping("/password/change")
    public Message changePassword(Authentication auth,@Valid @RequestBody ChangePassword body,HttpServletRequest request) {
        AppPrincipal p=principal(auth);
        accounts.changePassword(p.userId(),p.securityVersion(),body.currentPassword(),body.newPassword());
        HttpSession session=request.getSession(false);if(session!=null)session.invalidate();
        SecurityContextHolder.clearContext();
        return new Message("Password changed; please sign in again");
    }
    @PostMapping("/password/forgot")
    public Message forgot(@Valid @RequestBody ForgotPassword body) {
        try { tokens.forgotPassword(body.email()); }
        catch(org.springframework.mail.MailException deliveryFailure) {
            // Preserve the same response for unknown accounts and SMTP failure. Never log mail content.
            org.slf4j.LoggerFactory.getLogger(AuthController.class).warn("Password recovery mail delivery failed");
        }
        return new Message("If the address exists, a reset email will be sent");
    }
    @PostMapping("/password/reset")
    public Message reset(@Valid @RequestBody ResetPassword body) {
        tokens.resetPassword(body.token(),body.newPassword());
        return new Message("Password reset completed; sign in again");
    }
    @PostMapping("/invitations/accept")
    public Message accept(@Valid @RequestBody AcceptInvitation body) {
        tokens.acceptInvitation(body.token(),body.displayName(),body.password());
        return new Message("Account activated; please sign in");
    }
    private AppPrincipal principal(Authentication auth) {
        if(auth==null || !(auth.getPrincipal() instanceof AppPrincipal p)) throw new IllegalStateException("Authentication required");
        return p;
    }
    private Me meView(String id) {
        UserAccount user=users.findById(id).orElseThrow();
        List<MembershipView> ms=memberships.findByUserIdAndActiveTrue(id).stream()
            .map(m->new MembershipView(m.role,m.organizationId)).toList();
        return new Me(user.id,user.email,user.displayName,ms);
    }
}
