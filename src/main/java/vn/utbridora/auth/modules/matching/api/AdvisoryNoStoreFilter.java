package vn.utbridora.auth.modules.matching.api;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Also covers authentication, validation and dependency error responses. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE+10)
public class AdvisoryNoStoreFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String path=request.getServletPath();
        if(path.startsWith("/api/v1/matching/advisories/") || path.matches("/api/v1/matching/runs/[^/]+/advisories")) response.setHeader("Cache-Control","no-store");
        chain.doFilter(request,response);
    }
}
