package vn.utbridora.auth.modules.matching.api;

import java.net.URI;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.matching.advisory.AdvisoryService;
import vn.utbridora.auth.modules.matching.advisory.AdvisoryModels.*;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@RestController @RequestMapping("/api/v1/matching")
public class AdvisoryController {
    private final AdvisoryService service;
    public AdvisoryController(AdvisoryService service) {this.service=service;}
    @PostMapping("/runs/{id}/advisories") public ResponseEntity<Accepted> create(Authentication auth,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String json,HttpServletRequest request) {
        query(request,Set.of());var result=service.create(auth,id,key,body(json,Create.class));
        return ResponseEntity.accepted().location(URI.create("/api/v1/matching/advisories/"+result.advisoryId())).cacheControl(CacheControl.noStore()).body(result);
    }
    @GetMapping("/advisories/{id}") public ResponseEntity<Metadata> metadata(Authentication auth,@PathVariable String id,HttpServletRequest request) {query(request,Set.of());return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.metadata(auth,id));}
    @GetMapping("/advisories/{id}/items") public ResponseEntity<Page> items(Authentication auth,@PathVariable String id,HttpServletRequest request) {
        query(request,Set.of("page","size"));int page=number(request,"page",0),size=number(request,"size",25);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.items(auth,id,page,size));
    }
    private static void query(HttpServletRequest r,Set<String> allowed) {if(r.getParameterMap().entrySet().stream().anyMatch(x->!allowed.contains(x.getKey()) || x.getValue().length!=1)) throw failure(400,"SCHEMA_VALIDATION");}
    private static int number(HttpServletRequest r,String key,int fallback) {String value=r.getParameter(key);if(value==null)return fallback;try {if(!value.matches("0|[1-9][0-9]{0,4}"))throw new NumberFormatException();return Integer.parseInt(value);}catch(NumberFormatException invalid){throw failure(400,"INVALID_PAGINATION");}}
}
