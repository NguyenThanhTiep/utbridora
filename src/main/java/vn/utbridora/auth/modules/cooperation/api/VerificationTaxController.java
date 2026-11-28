package vn.utbridora.auth.modules.cooperation.api;

import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.cooperation.application.TaxVerificationAssessmentService;
import vn.utbridora.auth.modules.cooperation.application.VerificationTaxCheckService;

@RestController
@RequestMapping("/api/v1")
public class VerificationTaxController {
    private final TaxVerificationAssessmentService service;
    private final VerificationTaxCheckService checks;
    public VerificationTaxController(TaxVerificationAssessmentService service, VerificationTaxCheckService checks) { this.service = service; this.checks = checks; }
    @GetMapping("/enterprise/verifications/tax-lookup")
    public TaxAssessmentView lookup(Authentication auth, @RequestHeader("X-Organization-Id") String org, @RequestParam String taxCode) {
        return service.lookupForEnterprise(auth, org, taxCode);
    }
    @GetMapping("/school/verifications/{id}/tax-assessment")
    public TaxAssessmentView assess(Authentication auth, @RequestHeader("X-Organization-Id") String org, @PathVariable String id) {
        return service.assessForSchool(auth, org, id);
    }
    @GetMapping("/school/verifications/{id}/tax-check")
    public VerificationTaxCheckView check(Authentication auth, @RequestHeader("X-Organization-Id") String org, @PathVariable String id) {
        return checks.read(auth, org, id, false);
    }
    @PostMapping("/school/verifications/{id}/tax-check")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VerificationTaxCheckView recheck(Authentication auth, @RequestHeader("X-Organization-Id") String org, @PathVariable String id) {
        return checks.read(auth, org, id, true);
    }
}
