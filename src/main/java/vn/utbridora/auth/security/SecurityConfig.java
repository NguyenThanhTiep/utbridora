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
               .requestMatchers(HttpMethod.GET,"/api/v1/taxonomy/**").authenticated()
               .requestMatchers(HttpMethod.POST,"/api/v1/taxonomy/categories","/api/v1/taxonomy/skills","/api/v1/taxonomy/skills/resolve","/api/v1/taxonomy/skills/*/aliases").authenticated()
               .requestMatchers(HttpMethod.PATCH,"/api/v1/taxonomy/categories/*","/api/v1/taxonomy/skills/*").authenticated()
               .requestMatchers(HttpMethod.DELETE,"/api/v1/taxonomy/skills/*/aliases/*").authenticated()
               .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
               .requestMatchers("/api/v1/auth/**", "/api/v1/demo/**").authenticated()
               .requestMatchers(HttpMethod.GET,"/api/v1/terms","/api/v1/terms/*").authenticated()
               .requestMatchers(HttpMethod.GET,"/api/v1/t3/**").authenticated()
               .requestMatchers(HttpMethod.POST,"/api/v1/t3/**").authenticated()
               // T2-01 own student profile (BC11); the VERIFIED school scope is checked in StudentProfileService.
               .requestMatchers(HttpMethod.GET,"/api/v1/students/me/profile","/api/v1/students/me/catalogs").authenticated()
               .requestMatchers(HttpMethod.PUT,"/api/v1/students/me/profile").authenticated()
               .requestMatchers(HttpMethod.GET,"/api/v1/enterprise/demands").authenticated()
               .requestMatchers(HttpMethod.POST,"/api/v1/enterprise/demands").authenticated()
               .requestMatchers(HttpMethod.PATCH,"/api/v1/enterprise/demands/{id}").authenticated()
               .requestMatchers(HttpMethod.GET,"/api/v1/enterprise/verifications","/api/v1/enterprise/verifications/*","/api/v1/school/verifications","/api/v1/school/verifications/*").authenticated()
               .requestMatchers(HttpMethod.POST,"/api/v1/enterprise/verifications").authenticated()
               .requestMatchers(HttpMethod.PATCH,"/api/v1/enterprise/verifications/*","/api/v1/enterprise/verifications/*/submit","/api/v1/school/verifications/*/decision").authenticated()
               .requestMatchers(HttpMethod.POST,"/api/v1/school/terms/*/delegations").authenticated()
               .requestMatchers(HttpMethod.GET,"/api/v1/invitations/me","/api/v1/invitations/*","/api/v1/students/me/consents","/api/v1/students/*/profile","/api/v1/school/terms/*/profile-sharing-policy").authenticated()
               .requestMatchers(HttpMethod.POST,"/api/v1/invitations/*/decision","/api/v1/invitations/*/withdraw","/api/v1/students/me/consents","/api/v1/students/me/consents/*/revoke","/api/v1/school/proposals/*/shares","/api/v1/school/profile-shares/*/revoke","/api/v1/school/student-eligibilities/decisions").authenticated()
               .requestMatchers(HttpMethod.PUT,"/api/v1/school/terms/*/profile-sharing-policy").authenticated()
               .requestMatchers(HttpMethod.GET,"/api/v1/enterprise/cooperations/*").authenticated()
               .requestMatchers(HttpMethod.POST,"/api/v1/matching/evaluate","/api/v1/matching/runs/*/nominate","/api/v1/enterprise/cooperations","/api/v1/enterprise/demands/submit-with-cooperation","/api/v1/school/proposals/*/invite").authenticated()
               .requestMatchers(HttpMethod.GET,"/api/v1/matching/runs/*/details","/api/v1/enterprise/cooperations","/api/v1/school/cooperations","/api/v1/school/cooperations/*","/api/v1/school/cooperations/*/submission-history","/api/v1/school/cooperations/*/capacity","/api/v1/school/cooperations/*/proposals").authenticated()
               // DEV08 bổ sung: yêu cầu đăng nhập cho các route JSON, CSV và biểu đồ của release matching; MatchingService tiếp tục kiểm tra phạm vi tổ chức.
               .requestMatchers(HttpMethod.GET,"/api/v1/matching/runs/*/summary","/api/v1/matching/runs/*/summary.csv","/api/v1/matching/runs/*/summary.chart").authenticated()
               .requestMatchers(HttpMethod.PATCH,"/api/v1/enterprise/cooperations/*/submit","/api/v1/enterprise/cooperations/*/reopen","/api/v1/school/cooperations/*/decision").authenticated()
               .requestMatchers(HttpMethod.DELETE,"/api/v1/school/delegations/*","/api/v1/t3/context-assignments/*").authenticated()
               .requestMatchers(HttpMethod.PATCH,"/api/v1/t3/assignments/*/status").authenticated()
               .anyRequest().denyAll())
           .exceptionHandling(ex->ex
               .authenticationEntryPoint((req,res,error)->{
                   if(DemandSecurityErrors.applies(req)) DemandSecurityErrors.write(req,res,401,"SESSION_EXPIRED","Phiên đăng nhập không hợp lệ.");
                   else res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
               })
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
                   if(DemandSecurityErrors.applies(req)) {
                       boolean csrf=error instanceof org.springframework.security.web.csrf.CsrfException;
                       DemandSecurityErrors.write(req,res,403,csrf?"CSRF_INVALID":"ACCESS_DENIED",
                           csrf?"Mã bảo vệ không hợp lệ.":"Bạn không có quyền thực hiện thao tác.");
                       return;
                   }
                   res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                   res.setContentType("application/json");
                   res.getWriter().write("{\"error\":\"ACCESS_DENIED\",\"message\":\"Access denied or invalid CSRF token\"}");
               }))
           .addFilterAfter(new AccountGuardFilter(users), SecurityContextHolderFilter.class);
        return http.build();
    }
}
