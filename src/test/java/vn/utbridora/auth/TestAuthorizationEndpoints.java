package vn.utbridora.auth;

import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Test-only adapter examples. Never included in the production artifact. */
@TestConfiguration(proxyBeanMethods=false)
class TestAuthorizationEndpoints {
    @RestController
    static class ScopeEndpoints {
        record Command(String organizationId) {}
        @PostMapping("/api/v1/demo/test/organizations/{orgId}/command")
        @PreAuthorize("@authorizationPolicy.hasOrganizationForRequest(authentication,#orgId,#body.organizationId(),T(vn.utbridora.auth.user.Role).ENTERPRISE,'cooperation:enterprise:create')")
        Map<String,String> command(@PathVariable String orgId,@RequestBody Command body) {return Map.of("organizationId",orgId);}
        @GetMapping("/api/v1/demo/test/profiles/{id}")
        @PreAuthorize("@authorizationPolicy.canReadStudentProfile(authentication,#id,#organizationId)")
        Map<String,String> profile(@PathVariable String id,@RequestParam String organizationId) {return Map.of("userId",id);}
    }
}
