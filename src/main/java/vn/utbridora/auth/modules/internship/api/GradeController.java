package vn.utbridora.auth.modules.internship.api;

import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.modules.internship.application.GradeService;
import vn.utbridora.auth.modules.internship.infrastructure.WorkspaceRuntime.Page;
import static vn.utbridora.auth.modules.internship.api.GradeDtos.*;
import static vn.utbridora.auth.modules.internship.api.EvaluationDtos.*;
import static vn.utbridora.auth.modules.internship.api.Week3RequestValues.*;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@RestController
@RequestMapping("/api/v1/t3")
public class GradeController {
    private final GradeService grades;
    private static final JsonMapper STRICT=JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY,DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public GradeController(GradeService grades){this.grades=grades;}
    @ModelAttribute void privateResponse(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","private, no-store");}
    @PostMapping("/evaluations/{id}/publish")
    public EvaluationDto publish(Authentication auth,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String body){return grades.publish(auth,identifier(id),key,parse(body,PublishEvaluation.class));}
    @PostMapping("/evaluations/{id}/revisions") @ResponseStatus(HttpStatus.CREATED)
    public EvaluationDto revision(Authentication auth,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String body){return grades.revision(auth,identifier(id),key,parse(body,CreateEvaluationRevision.class));}
    @GetMapping("/schools/{school}/terms/{term}/result-visibility")
    public SchoolResultVisibilityDto school(Authentication auth,@PathVariable String school,@PathVariable String term,@RequestParam MultiValueMap<String,String> q){query(q,Set.of());return grades.schoolPolicy(auth,identifier(school),identifier(term));}
    @PutMapping("/schools/{school}/terms/{term}/result-visibility")
    public SchoolResultVisibilityDto setSchool(Authentication auth,@PathVariable String school,@PathVariable String term,@RequestHeader("Idempotency-Key") String key,@RequestBody String body,@RequestParam MultiValueMap<String,String> q){query(q,Set.of());return grades.setSchool(auth,identifier(school),identifier(term),key,parse(body,SetVisibility.class));}
    @GetMapping("/organizations/{enterprise}/grade-visibility")
    public GradeVisibilityDto hr(Authentication auth,@PathVariable String enterprise,@RequestParam MultiValueMap<String,String> query){var q=query(query,Set.of("termId","caseId"));return grades.hrPolicy(auth,identifier(enterprise),identifier(q.get("termId")),optional(q,"caseId"));}
    @PutMapping("/organizations/{enterprise}/grade-visibility")
    public GradeVisibilityDto setHr(Authentication auth,@PathVariable String enterprise,@RequestParam MultiValueMap<String,String> query,@RequestHeader("Idempotency-Key") String key,@RequestBody String body){var q=query(query,Set.of("termId","caseId"));return grades.setHr(auth,identifier(enterprise),identifier(q.get("termId")),optional(q,"caseId"),key,parse(body,SetVisibility.class));}
    @GetMapping("/me/results")
    public StudentResultDto result(Authentication auth,@RequestParam MultiValueMap<String,String> query){var q=query(query,Set.of("caseId"));return grades.result(auth,identifier(q.get("caseId")));}
    @GetMapping("/school/evaluations")
    public Page<EvaluationDto> list(Authentication auth,@RequestParam MultiValueMap<String,String> query){
        var q=query(query,Set.of("termId","cohortId","state","page","size"));EvaluationState state=null;
        try{if(q.containsKey("state"))state=EvaluationState.valueOf(q.get("state"));}catch(IllegalArgumentException e){throw failure(400,"SCHEMA_VALIDATION");}
        return grades.schoolList(auth,identifier(q.get("termId")),optional(q,"cohortId"),state,integer(q,"page",0),integer(q,"size",25));
    }
    private static UUID optional(Map<String,String> q,String key){return q.containsKey(key)?identifier(q.get(key)):null;}
    private static Map<String,String> query(MultiValueMap<String,String> q,Set<String> allowed){var map=unique(q);if(!allowed.containsAll(map.keySet()))throw failure(400,"SCHEMA_VALIDATION");return map;}
    static <T> T parse(String body,Class<T> type){
        try{if(body==null || body.length()>10000)throw failure(400,"SCHEMA_VALIDATION");var n=STRICT.readTree(body);
            if(n==null || !n.isObject())throw failure(400,"SCHEMA_VALIDATION");
            for(var field:type.getRecordComponents())if(!n.hasNonNull(field.getName()))throw failure(400,"SCHEMA_VALIDATION");
            return JSON.readValue(body,type);
        }catch(tools.jackson.core.JacksonException|IllegalArgumentException e){throw failure(400,"SCHEMA_VALIDATION");}
    }
}
