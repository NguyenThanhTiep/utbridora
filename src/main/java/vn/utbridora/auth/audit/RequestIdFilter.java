package vn.utbridora.auth.audit;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component @Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        // Do not trust inbound request IDs as audit evidence.
        String id=UUID.randomUUID().toString();
        request.setAttribute("bridora.request_id",id);
        response.setHeader("X-Request-ID",id);
        org.slf4j.MDC.put("requestId",id);
        try { chain.doFilter(request,response); }
        finally { org.slf4j.MDC.remove("requestId"); }
    }
}
