package vn.utbridora.auth.api;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Test route; replace with business-owned endpoints when T5 is integrated. */
@RestController @RequestMapping("/api/v1/demo")
public class DemoScopeController {
    @GetMapping("/organizations/{organizationId}/private")
    @PreAuthorize("@organizationAccess.enterpriseCanAccess(authentication, #organizationId)")
    public Map<String,String> enterprisePrivate(@PathVariable String organizationId) {
        return Map.of("organizationId",organizationId,"result","Access granted by org scope");
    }
}
