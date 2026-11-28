package vn.utbridora.auth.modules.matching.api;

import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.matching.application.SchoolMatchingReportService;

@RestController
@RequestMapping("/api/v1/school/matching-reports")
public class SchoolMatchingReportController {
    private final SchoolMatchingReportService service;
    public SchoolMatchingReportController(SchoolMatchingReportService service) {this.service=service;}
    @GetMapping
    public SchoolMatchingReportService.Report report(Authentication auth,@RequestHeader("X-Organization-Id") String school,
            @RequestParam(required=false) String termId,@RequestParam(required=false) String q,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) {
        return service.report(auth,school,termId,q,page,size);
    }
    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> export(Authentication auth,@RequestHeader("X-Organization-Id") String school,
            @RequestParam(required=false) String termId,@RequestParam(required=false) String q) {
        return ResponseEntity.ok().contentType(new MediaType("text","csv",StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=bao-cao-doi-chieu.csv")
            .cacheControl(CacheControl.noStore()).body(service.export(auth,school,termId,q).getBytes(StandardCharsets.UTF_8));
    }
}
