package vn.utbridora.auth.modules.matching.api;

import org.springframework.security.core.Authentication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import vn.utbridora.auth.modules.matching.application.MatchingService;
import vn.utbridora.auth.operations.OperationFailure;
import static vn.utbridora.auth.operations.WorkflowValues.body;

@RestController @RequestMapping("/api/v1/matching")
public class MatchingController {
    public record ProtectedChart(List<String> eligibleBands) {}
    private final MatchingService service;
    public MatchingController(MatchingService service) {this.service=service;}
    @PostMapping("/evaluate") public MatchingService.Evaluated evaluate(Authentication auth,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {
        var input=body(data,MatchingService.Evaluate.class);
        try {return service.evaluate(auth,key,input);}
        catch(OperationFailure failure) {
            if("PRIVACY_RELEASE_LOCKED".equals(failure.code)) service.auditPrivacyReleaseBlocked(auth,input.demandId());
            throw failure;
        }
    }
    @GetMapping("/runs/{id}/details") public MatchingService.Details details(Authentication auth,@PathVariable String id,@RequestParam String overallStatus,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return service.details(auth,id,overallStatus,page,size);}
    @GetMapping("/runs/{id}/summary") public MatchingService.Evaluated summary(Authentication auth,@PathVariable String id) {
        return new MatchingService.Evaluated("ENTERPRISE",null,service.protectedRelease(auth,id));
    }
    @GetMapping("/runs/{id}/summary.csv") public ResponseEntity<byte[]> summaryCsv(Authentication auth,@PathVariable String id) {
        byte[] body=service.protectedReleaseCsv(auth,id).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(new MediaType("text","csv",StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=matching-summary.csv").body(body);
    }
    @GetMapping("/runs/{id}/summary.chart") public ProtectedChart summaryChart(Authentication auth,@PathVariable String id) {
        return new ProtectedChart(List.of(service.protectedRelease(auth,id).eligibleBand()));
    }
}
