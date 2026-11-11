package vn.utbridora.auth.modules.cooperation.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.cooperation.application.CooperationService;
import vn.utbridora.auth.modules.cooperation.application.CooperationService.*;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page;
import static vn.utbridora.auth.operations.WorkflowValues.body;

@RestController @RequestMapping("/api/v1")
public class CooperationController {
    private final CooperationService service;
    public CooperationController(CooperationService service) {this.service=service;}
    @PostMapping("/enterprise/cooperations") @ResponseStatus(HttpStatus.CREATED)
    public CooperationDto create(Authentication a,@RequestHeader("X-Organization-Id") String org,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return service.create(a,org,key,body(data,Submit.class));}
    @PostMapping("/enterprise/demands/submit-with-cooperation")
    public Submitted atomic(Authentication a,@RequestHeader("X-Organization-Id") String org,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return service.submitWithCooperation(a,org,key,body(data,Submit.class));}
    @PatchMapping("/enterprise/cooperations/{id}/submit")
    public CooperationDto submit(Authentication a,@RequestHeader("X-Organization-Id") String org,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return service.submit(a,org,id,key,body(data,Resubmit.class));}
    @PatchMapping("/enterprise/cooperations/{id}/reopen")
    public CooperationDto reopen(Authentication a,@RequestHeader("X-Organization-Id") String org,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return service.reopen(a,org,id,key,body(data,Reopen.class));}
    @PatchMapping("/school/cooperations/{id}/decision")
    public CooperationDto decision(Authentication a,@RequestHeader("X-Organization-Id") String org,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return service.decide(a,org,id,key,body(data,Decision.class));}
    @GetMapping("/{side:school|enterprise}/cooperations")
    public Page<CooperationDto> list(Authentication a,@RequestHeader("X-Organization-Id") String org,@PathVariable String side,@RequestParam(required=false) String termId,@RequestParam(required=false) String state,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return service.list(a,org,side.equals("school"),termId,state,page,size);}
    @GetMapping("/school/cooperations/{id}/submission-history")
    public Page<SubmissionHistory> history(Authentication a,@RequestHeader("X-Organization-Id") String org,@PathVariable String id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return service.history(a,org,id,page,size);}
    @GetMapping("/{side:school|enterprise}/cooperations/{id}")
    public ReviewDetail detail(Authentication a,@RequestHeader("X-Organization-Id") String org,@PathVariable String side,@PathVariable String id) {return service.reviewDetail(a,org,id,side.equals("school"));}
}
