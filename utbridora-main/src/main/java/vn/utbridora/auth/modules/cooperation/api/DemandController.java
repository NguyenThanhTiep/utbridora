package vn.utbridora.auth.modules.cooperation.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import vn.utbridora.auth.modules.cooperation.application.DemandService;
import vn.utbridora.auth.modules.cooperation.domain.DemandJson;
import static vn.utbridora.auth.modules.cooperation.domain.DemandModels.*;

@RestController
@RequestMapping("/api/v1/enterprise/demands")
public class DemandController {
    private final DemandService service;
    public DemandController(DemandService service) { this.service=service; }
    @PostMapping
    public ResponseEntity<DemandDto> create(Authentication authentication,@RequestHeader("X-Organization-Id") String org,
            @RequestHeader("Idempotency-Key") String key,@RequestBody JsonNode body) {
        return ResponseEntity.status(201).body(service.create(authentication,org,key,DemandJson.request(body,DemandInput.class)));
    }
    @PatchMapping("/{id}")
    public DemandDto update(Authentication authentication,@RequestHeader("X-Organization-Id") String org,
            @PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody JsonNode body) {
        return service.update(authentication,org,id,key,DemandJson.request(body,UpdateDemand.class));
    }
    @GetMapping
    public Page<DemandDto> list(Authentication authentication,@RequestHeader("X-Organization-Id") String org,
            @RequestParam(required=false) String termId,@RequestParam(required=false) String state,
            @RequestParam(defaultValue="false") boolean includeDeleted,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="25") int size) {
        return service.list(authentication,org,termId,state,includeDeleted,page,size);
    }
}
