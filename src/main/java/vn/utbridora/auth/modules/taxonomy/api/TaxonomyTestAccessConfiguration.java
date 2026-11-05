package vn.utbridora.auth.modules.taxonomy.api;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.security.AccountGuardFilter;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.user.UserRepository;

/**
 * DEV08 thêm cấu hình truy cập tạm chỉ dùng khi bật cấu hình kiểm thử danh mục kỹ năng.
 * Gỡ cấu hình này sau khi T1 tích hợp permission/capability (quyền và năng lực) TAX chính thức.
 */
@Configuration
@Profile("taxonomy-test-access")
public class TaxonomyTestAccessConfiguration {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain taxonomyTestAccessFilterChain(HttpSecurity http, UserRepository users,
            SecurityContextRepository contexts, CsrfTokenRepository csrfTokens, AuditService audit) throws Exception {
        http
            .securityMatcher("/api/v1/taxonomy/**")
            .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens))
            .securityContext(context -> context.securityContextRepository(contexts))
            .requestCache(cache -> cache.disable())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable())
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                // DEV08: quyền ảo taxonomy:skills:read (đọc danh mục kỹ năng) chỉ có trong cấu hình kiểm thử này; cần phiên hợp lệ.
                // DEV08: quyền ảo taxonomy:skills:write (sửa danh mục kỹ năng) tạm giới hạn ở ADMIN (quản trị viên) tại controller.
                .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, failure) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                .accessDeniedHandler((request, response, failure) -> {
                    var authentication = SecurityContextHolder.getContext().getAuthentication();
                    String actor = authentication != null && authentication.getPrincipal() instanceof AppPrincipal principal
                        ? principal.userId() : null;
                    try {
                        audit.securityDenied(actor, "AUTHORIZATION_DENIED");
                    } catch (RuntimeException auditFailure) {
                        org.slf4j.LoggerFactory.getLogger(TaxonomyTestAccessConfiguration.class)
                            .warn("Không ghi được nhật ký từ chối; yêu cầu vẫn bị chặn");
                    }
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"ACCESS_DENIED\",\"message\":\"Access denied or invalid CSRF token\"}");
                }))
            .addFilterAfter(new AccountGuardFilter(users), SecurityContextHolderFilter.class);
        return http.build();
    }
}
