package vn.utbridora.auth.modules.cooperation.api;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.cooperation.application.DemandReferenceService;
import vn.utbridora.auth.modules.cooperation.application.DemandReferenceService.*;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page;

@RestController
@RequestMapping("/api/v1/enterprise/demand-references")
public class DemandReferenceController {
    private final DemandReferenceService references;
    public DemandReferenceController(DemandReferenceService references) {this.references=references;}
    @GetMapping("/schools")
    public Page<SchoolOption> schools(Authentication actor,@RequestHeader("X-Organization-Id") String enterprise,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {
        return references.schools(actor,enterprise,page,size);
    }
    @GetMapping("/terms")
    public Page<TermOption> terms(Authentication actor,@RequestHeader("X-Organization-Id") String enterprise,@RequestParam String schoolOrgId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {
        return references.terms(actor,enterprise,schoolOrgId,page,size);
    }
    @GetMapping("/academic-catalog")
    public AcademicOption academic(Authentication actor,@RequestHeader("X-Organization-Id") String enterprise,@RequestParam String schoolOrgId,@RequestParam String termId) {
        return references.academic(actor,enterprise,schoolOrgId,termId);
    }
}
