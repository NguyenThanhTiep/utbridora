package vn.utbridora.auth.modules.matching.api;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.matching.application.MatchingService;
import static vn.utbridora.auth.operations.WorkflowValues.body;

@RestController @RequestMapping("/api/v1/matching")
public class MatchingController {
    private final MatchingService service;
    public MatchingController(MatchingService service) {this.service=service;}
    @PostMapping("/evaluate") public MatchingService.Evaluated evaluate(Authentication auth,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return service.evaluate(auth,key,body(data,MatchingService.Evaluate.class));}
    @GetMapping("/runs/{id}/details") public MatchingService.Details details(Authentication auth,@PathVariable String id,@RequestParam String overallStatus,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return service.details(auth,id,overallStatus,page,size);}
}
