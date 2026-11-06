package vn.utbridora.auth.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import vn.utbridora.auth.user.UserRepository;
import vn.utbridora.auth.audit.AuditService;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder(){ return new BCryptPasswordEncoder(12); }
    @Bean AuthenticationManager authenticationManager(UserDetailsService userDetails,PasswordEncoder encoder) {
        DaoAuthenticationProvider provider=new DaoAuthenticationProvider(userDetails);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    @Bean SecurityContextRepository securityContextRepository(){ return new HttpSessionSecurityContextRepository(); }
    @Bean CsrfTokenRepository csrfTokenRepository(){ return new HttpSessionCsrfTokenRepository(); }
    @Bean SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokens) {
        return new CompositeSessionAuthenticationStrategy(java.util.List.of(
            new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokens)));
    }
    @Bean SecurityFilterChain filterChain(HttpSecurity http, UserRepository users,
            SecurityContextRepository contexts, CsrfTokenRepository csrfRepository, AuditService audit) throws Exception {
        http
           .csrf(csrf->csrf.csrfTokenRepository(csrfRepository))
           .securityContext(ctx->ctx.securityContextRepository(contexts))
           .requestCache(cache->cache.disable())
           .formLogin(form->form.disable())
           .httpBasic(basic->basic.disable())
           .logout(logout->logout.disable())
           .authorizeHttpRequests(auth->auth
               .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
               .requestMatchers(HttpMethod.GET,"/api/v1/auth/csrf").permitAll()
               .requestMatchers(HttpMethod.GET,"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
               .requestMatchers(HttpMethod.POST,"/api/v1/auth/login","/api/v1/auth/password/forgot",
                       "/api/v1/auth/password/reset","/api/v1/auth/invitations/accept").permitAll()
               .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
               .requestMatchers("/api/v1/auth/**", "/api/v1/demo/**").authenticated()
               .anyRequest().denyAll())
           .exceptionHandling(ex->ex
               .authenticationEntryPoint((req,res,error)->res.sendError(HttpServletResponse.SC_UNAUTHORIZED))
               .accessDeniedHandler((req,res,error)->{
                   var authentication=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                   String actor=authentication!=null && authentication.getPrincipal() instanceof AppPrincipal p
                       ? p.userId() : null;
                   // Avoid storing raw URL, IDs, request body or tokens in denial logs.
                   try {audit.securityDenied(actor,"AUTHORIZATION_DENIED");}
                   catch (RuntimeException auditFailure) {
                       org.slf4j.LoggerFactory.getLogger(SecurityConfig.class)
                           .warn("Security audit write failed; access remains denied");
                   }
                   res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                   res.setContentType("application/json");
                   res.getWriter().write("{\"error\":\"ACCESS_DENIED\",\"message\":\"Access denied or invalid CSRF token\"}");
               }))
           .addFilterAfter(new AccountGuardFilter(users), SecurityContextHolderFilter.class);
        return http.build();
    }
}
