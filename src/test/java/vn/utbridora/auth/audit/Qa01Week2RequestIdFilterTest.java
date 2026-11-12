package vn.utbridora.auth.audit;

import java.util.UUID;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Qa01Week2RequestIdFilterTest {
    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.remove("requestId");
    }

    @Test
    void replacesClientRequestIdAndSharesServerIdWithDownstream() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        String clientId = UUID.randomUUID().toString();
        request.addHeader("X-Request-ID", clientId);

        filter.doFilter(request, response, (downstreamRequest, downstreamResponse) -> {
            String serverId = response.getHeader("X-Request-ID");
            assertNotNull(serverId);
            assertDoesNotThrow(() -> { UUID.fromString(serverId); });
            assertNotEquals(clientId, serverId);
            assertEquals(serverId, downstreamRequest.getAttribute("bridora.request_id"));
            assertEquals(serverId, MDC.get("requestId"));
            response.setStatus(204);
        });

        assertEquals(204, response.getStatus());
        assertNull(MDC.get("requestId"));
    }

    @Test
    void clearsRequestIdWhenDownstreamThrows() {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var downstreamFailure = new ServletException("synthetic chain failure");

        var failure = assertThrows(ServletException.class, () ->
            filter.doFilter(request, response, (downstreamRequest, downstreamResponse) -> {
                assertNotNull(MDC.get("requestId"));
                assertEquals(response.getHeader("X-Request-ID"), MDC.get("requestId"));
                throw downstreamFailure;
            }));

        assertSame(downstreamFailure, failure);
        assertNull(MDC.get("requestId"));
    }
}
