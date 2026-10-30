package vn.utbridora.auth.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.api.ApiDtos.Message;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.organization.*;
import vn.utbridora.auth.security.AppPrincipal;

/** Demo only. T5 owns real enterprise verification; disabled unless explicitly opted-in. */
@RestController @RequestMapping("/api/v1/demo/admin")
@ConditionalOnProperty(prefix="app.demo", name="allow-org-verification", havingValue="true")
public class DemoOrganizationVerificationController {
    private final OrganizationRepository organizations;private final AuditService audit;
    public DemoOrganizationVerificationController(OrganizationRepository organizations,AuditService audit){
        this.organizations=organizations;this.audit=audit;
    }
    @PatchMapping("/organizations/{id}/verify")
    @org.springframework.transaction.annotation.Transactional
    @PreAuthorize("@authorizationPolicy.hasGlobal(authentication, 'organization:admin:create')")
    public Message verify(Authentication authentication,@PathVariable String id) {
        Organization o=organizations.findById(id).orElseThrow(()->new IllegalArgumentException("Organization not found"));
        o.status=Organization.Status.VERIFIED;organizations.save(o);
        audit.write(((AppPrincipal)authentication.getPrincipal()).userId(),"ORG_DEMO_VERIFIED","organization",id,"SUCCESS");
        return new Message("Demo-only verification performed");
    }
}
