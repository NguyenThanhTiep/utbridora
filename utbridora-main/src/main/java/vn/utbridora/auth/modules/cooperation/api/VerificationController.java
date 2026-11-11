package vn.utbridora.auth.modules.cooperation.api;

import jakarta.validation.Validator;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.modules.cooperation.application.VerificationService;
import vn.utbridora.auth.modules.cooperation.api.CooperationDtos.*;
import vn.utbridora.auth.modules.cooperation.domain.VerificationState;
import vn.utbridora.auth.operations.OperationFailure;

@RestController
@RequestMapping("/api/v1")
public class VerificationController {
    private static final JsonMapper JSON=JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    private final VerificationService service;
    private final Validator validator;
    public VerificationController(VerificationService service,Validator validator) {this.service=service;this.validator=validator;}
    private <T> T decode(JsonNode body,Class<T> type) {
        try {T data=JSON.treeToValue(body,type);if(data==null || !validator.validate(data).isEmpty()) throw new IllegalArgumentException();return data;}
        catch(RuntimeException invalid) {throw new OperationFailure(400,"SCHEMA_VALIDATION","Kiểm tra các trường và kiểu dữ liệu bắt buộc.",null);}
    }
    @PostMapping("/enterprise/verifications") @ResponseStatus(HttpStatus.CREATED)
    public VerificationView create(Authentication actor,@RequestHeader("X-Organization-Id") String org,@RequestHeader("Idempotency-Key") String key,@RequestBody JsonNode body) {
        return service.create(actor,org,key,decode(body,VerificationInput.class));
    }
    @PatchMapping("/enterprise/verifications/{id}")
    public VerificationView update(Authentication actor,@RequestHeader("X-Organization-Id") String org,@RequestHeader("Idempotency-Key") String key,@PathVariable String id,@RequestBody JsonNode body) {
        return service.update(actor,org,id,key,decode(body,UpdateVerificationData.class));
    }
    @PatchMapping("/enterprise/verifications/{id}/submit")
    public VerificationView submit(Authentication actor,@RequestHeader("X-Organization-Id") String org,@RequestHeader("Idempotency-Key") String key,@PathVariable String id,@RequestBody JsonNode body) {
        return service.submit(actor,org,id,key,decode(body,SubmitVerification.class));
    }
    @PatchMapping("/school/verifications/{id}/decision")
    public VerificationView decide(Authentication actor,@RequestHeader("X-Organization-Id") String org,@RequestHeader("Idempotency-Key") String key,@PathVariable String id,@RequestBody JsonNode body) {
        return service.decide(actor,org,id,key,decode(body,VerificationDecision.class));
    }
    @GetMapping("/enterprise/verifications")
    public Page<VerificationView> enterpriseList(Authentication actor,@RequestHeader("X-Organization-Id") String org,@RequestParam(required=false) String schoolOrgId,
        @RequestParam(required=false) VerificationState state,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return service.list(actor,org,false,schoolOrgId,state,page,size);}
    @GetMapping("/school/verifications")
    public Page<VerificationView> schoolList(Authentication actor,@RequestHeader("X-Organization-Id") String org,@RequestParam(required=false) String schoolOrgId,
        @RequestParam(required=false) VerificationState state,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return service.list(actor,org,true,schoolOrgId,state,page,size);}
    @GetMapping("/enterprise/verifications/{id}")
    public VerificationView enterpriseGet(Authentication actor,@RequestHeader("X-Organization-Id") String org,@PathVariable String id) {return service.detail(actor,org,id,false);}
    @GetMapping("/school/verifications/{id}")
    public VerificationView schoolGet(Authentication actor,@RequestHeader("X-Organization-Id") String org,@PathVariable String id) {return service.detail(actor,org,id,true);}
}
