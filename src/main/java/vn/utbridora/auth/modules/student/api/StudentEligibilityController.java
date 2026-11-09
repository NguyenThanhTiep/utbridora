package vn.utbridora.auth.modules.student.api;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.student.application.StudentEligibilityService;
import static vn.utbridora.auth.operations.WorkflowValues.body;
@RestController
public class StudentEligibilityController {
    private final StudentEligibilityService service;
    public StudentEligibilityController(StudentEligibilityService service) {this.service=service;}
    @PostMapping("/api/v1/school/student-eligibilities/decisions")
    public StudentEligibilityService.StudentTermEligibility decide(Authentication auth,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return service.decide(auth,key,body(data,StudentEligibilityService.EligibilityDecision.class));}
}
