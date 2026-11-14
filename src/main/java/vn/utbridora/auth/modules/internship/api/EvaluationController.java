package vn.utbridora.auth.modules.internship.api;

import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.internship.application.EvaluationService;
import vn.utbridora.auth.modules.internship.infrastructure.WorkspaceRuntime.Page;
import static vn.utbridora.auth.modules.internship.api.EvaluationDtos.*;
import static vn.utbridora.auth.modules.internship.api.Week3RequestValues.*;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** CP-WS4 mutations and additive Mentor-only reload/history reads. No publish/student-result API. */
@RestController
@RequestMapping("/api/v1/t3")
public class EvaluationController {
    private final EvaluationService evaluations;
    public EvaluationController(EvaluationService evaluations) { this.evaluations=evaluations; }
    @GetMapping("/rubric-templates")
    public Page<RubricTemplate> templates(Authentication auth, @RequestParam MultiValueMap<String,String> query) {
        actor(auth); var q=query(query,Set.of("termId","page","size"));
        return evaluations.templates(auth,identifier(q.get("termId")),integer(q,"page",0),integer(q,"size",25));
    }
    @PostMapping("/evaluations") @ResponseStatus(HttpStatus.CREATED)
    public EvaluationDto create(Authentication auth, @RequestHeader("Idempotency-Key") String key, @RequestBody String body) {
        actor(auth); return evaluations.create(auth,key(key).toString(),EvaluationJson.create(body));
    }
    @PutMapping("/evaluations/{id}")
    public EvaluationDto update(Authentication auth, @PathVariable String id, @RequestHeader("Idempotency-Key") String key, @RequestBody String body) {
        actor(auth); return evaluations.update(auth,identifier(id),key(key).toString(),EvaluationJson.update(body));
    }
    @PostMapping("/evaluations/{id}/submit")
    public EvaluationDto submit(Authentication auth, @PathVariable String id, @RequestHeader("Idempotency-Key") String key, @RequestBody String body) {
        actor(auth); return evaluations.submit(auth,identifier(id),key(key).toString(),EvaluationJson.submit(body));
    }
    @GetMapping("/evaluations/{id}")
    public EvaluationDto read(Authentication auth, @PathVariable String id, @RequestParam MultiValueMap<String,String> query) {
        actor(auth); var q=query(query,Set.of("version")); Long version=null;
        if (q.containsKey("version")) {
            try { if (!q.get("version").matches("[0-9]+")) throw new IllegalArgumentException(); version=Long.parseLong(q.get("version")); }
            catch (IllegalArgumentException invalid) { throw failure(400,"SCHEMA_VALIDATION"); }
            if (version<1 || version>9_007_199_254_740_991L) throw failure(400,"SCHEMA_VALIDATION");
        }
        return evaluations.read(auth,identifier(id),version);
    }
    @GetMapping("/evaluations/{id}/history")
    public Page<EvaluationDto> history(Authentication auth, @PathVariable String id, @RequestParam MultiValueMap<String,String> query) {
        actor(auth); var q=query(query,Set.of("page","size"));
        return evaluations.history(auth,identifier(id),integer(q,"page",0),integer(q,"size",25));
    }
    @GetMapping("/evaluations")
    public ResponseEntity<EvaluationDto> forCase(Authentication auth, @RequestParam MultiValueMap<String,String> query) {
        actor(auth); var q=query(query,Set.of("caseId"));
        var value=evaluations.forCase(auth,identifier(q.get("caseId")));
        return value==null?ResponseEntity.noContent().build():ResponseEntity.ok(value);
    }
    private static Map<String,String> query(MultiValueMap<String,String> query, Set<String> allowed) {
        var q=unique(query);
        if (!allowed.containsAll(q.keySet())) throw failure(400,"SCHEMA_VALIDATION");
        return q;
    }
}
