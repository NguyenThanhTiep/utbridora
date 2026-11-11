package vn.utbridora.auth.modules.cooperation.api;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.cooperation.application.OpportunityService;
import vn.utbridora.auth.modules.cooperation.application.OpportunityService.PublishOpportunity;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page;
import vn.utbridora.auth.modules.cooperation.port.OpportunityPort.OpportunityView;
import static vn.utbridora.auth.operations.WorkflowValues.body;

@RestController
@RequestMapping("/api/v1")
public class OpportunityController {
    private final OpportunityService opportunities;

    public OpportunityController(OpportunityService opportunities) {
        this.opportunities = opportunities;
    }

    @GetMapping("/opportunities")
    public Page<OpportunityView> list(Authentication actor, @RequestParam String termId,
            @RequestParam(required = false) String q, @RequestParam(required = false) String majorCode,
            @RequestParam(required = false) String enterpriseOrgId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return opportunities.listMine(actor,
            new vn.utbridora.auth.modules.cooperation.port.OpportunityPort.Query(
                termId, q, majorCode, enterpriseOrgId, page, size));
    }

    @GetMapping("/opportunities/{id}")
    public OpportunityView read(Authentication actor, @PathVariable String id) {
        return opportunities.readMine(actor, id);
    }

    @PutMapping("/school/cooperations/{id}/publication")
    public OpportunityView publish(Authentication actor, @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key, @RequestBody String request) {
        return opportunities.publish(actor, id, key, body(request, PublishOpportunity.class));
    }
}
