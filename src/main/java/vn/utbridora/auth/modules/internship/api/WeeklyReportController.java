package vn.utbridora.auth.modules.internship.api;

import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.internship.application.WeeklyReportService;
import vn.utbridora.auth.modules.internship.infrastructure.WeeklyReportCommands;
import static vn.utbridora.auth.modules.internship.api.ReportDtos.*;
import static vn.utbridora.auth.modules.internship.api.Week3RequestValues.*;
import static vn.utbridora.auth.operations.WorkflowValues.actor;

/** CP-WS3 v1.1 HTTP transport using shared session/CSRF and real runtime wiring. */
@RestController
@ConditionalOnProperty(name = HTTP_ENABLED, havingValue = "true")
@RequestMapping("/api/v1/t3/reports")
public final class WeeklyReportController {
    private final WeeklyReportService reports;
    private final WeeklyReportCommands commands;
    public WeeklyReportController(WeeklyReportService reports, WeeklyReportCommands commands) {
        this.reports = Objects.requireNonNull(reports); this.commands = Objects.requireNonNull(commands);
    }
    @PostMapping(consumes = "application/json") @ResponseStatus(HttpStatus.CREATED)
    public ReportDto create(Authentication auth, @RequestHeader(value = "Idempotency-Key", required = false) String key,
                            @RequestBody String body) {
        actor(auth); return commands.create(auth, key(key), ReportJson.create(body));
    }
    @PatchMapping(value = "/{reportId}", consumes = "application/json")
    public ReportDto update(Authentication auth, @PathVariable String reportId,
                            @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody String body) {
        actor(auth); return commands.update(auth, identifier(reportId), key(key), ReportJson.update(body));
    }
    @PostMapping(value = "/{reportId}/submit", consumes = "application/json")
    public ReportDto submit(Authentication auth, @PathVariable String reportId,
                            @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody String body) {
        actor(auth); return commands.submit(auth, identifier(reportId), key(key), ReportJson.submit(body));
    }
    @PostMapping(value = "/{reportId}/reviews", consumes = "application/json")
    public ReportDto review(Authentication auth, @PathVariable String reportId,
                            @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody String body) {
        actor(auth); return commands.review(auth, identifier(reportId), key(key), ReportJson.review(body));
    }
    @PostMapping(value = "/{reportId}/revisions", consumes = "application/json") @ResponseStatus(HttpStatus.CREATED)
    public ReportDto revision(Authentication auth, @PathVariable String reportId,
                              @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody String body) {
        actor(auth); return commands.revision(auth, identifier(reportId), key(key), ReportJson.revision(body));
    }
    @GetMapping("/{reportId}")
    public ReportDto read(Authentication auth, @PathVariable String reportId, @RequestParam MultiValueMap<String, String> parameters) {
        actor(auth); return reports.read(auth, identifier(reportId), ReportJson.readQuery(unique(parameters)));
    }
    @GetMapping
    public ReportPage list(Authentication auth, @RequestParam MultiValueMap<String, String> parameters) {
        actor(auth); return reports.list(auth, ReportJson.query(unique(parameters)));
    }
    @GetMapping("/{reportId}/reviews")
    public ReportReviewDto readReview(Authentication auth, @PathVariable String reportId, @RequestParam MultiValueMap<String, String> parameters) {
        actor(auth); return reports.readReview(auth, identifier(reportId), ReportJson.readQuery(unique(parameters)));
    }
}
