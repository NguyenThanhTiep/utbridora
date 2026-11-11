package vn.utbridora.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class DemandSecurityErrorsTest {
    @Test void envelopeHasAllContractFieldsAndSharesRequestId() throws Exception {
        var request=new MockHttpServletRequest(); request.setServletPath("/api/v1/enterprise/demands");
        String id="11111111-1111-4111-8111-111111111111"; request.setAttribute("bridora.request_id",id);
        var response=new MockHttpServletResponse();
        assertTrue(DemandSecurityErrors.applies(request));
        DemandSecurityErrors.write(request,response,403,"CSRF_INVALID","Invalid CSRF");
        var json=JsonMapper.builder().build().readTree(response.getContentAsString());
        assertEquals(403,response.getStatus()); assertEquals(id,response.getHeader("X-Request-ID"));
        assertEquals(id,json.get("requestId").asString()); assertEquals("CSRF_INVALID",json.get("error").asString());
        assertEquals("CSRF_INVALID",json.get("errorCode").asString());
        assertEquals(0,json.get("fieldErrors").size()); assertTrue(json.get("retryAfterSeconds").isNull());
        assertEquals("Invalid CSRF",json.get("message").asString());
    }
    @Test void legacyRoutesAreNotIntercepted() {
        var request=new MockHttpServletRequest();
        for(String path:java.util.List.of("/api/v1/auth/me","/api/v1/admin/users","/api/v1/enterprise/demands-other")) {
            request.setServletPath(path); assertFalse(DemandSecurityErrors.applies(request));
        }
    }
}
