package vn.utbridora.auth.modules.student.api;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.student.api.StudentProfileDtos.SelfProfileView;
import vn.utbridora.auth.modules.student.application.StudentProfileService;

/** BC11 routes (Contract Pack v1.0 line 778). Business rules live in StudentProfileService. */
@RestController
@RequestMapping("/api/v1/students/me/profile")
public class StudentProfileController {
    private final StudentProfileService profiles;
    public StudentProfileController(StudentProfileService profiles) { this.profiles=profiles; }

    @GetMapping
    public SelfProfileView read(Authentication auth,@RequestParam String termId) {
        return profiles.read(auth,termId);
    }
    /** The body is read raw so that missing and unknown fields can be reported per field (closed schema). */
    @PutMapping
    public SelfProfileView save(Authentication auth,@RequestHeader(name="Idempotency-Key",required=false) String idempotencyKey,
                                @RequestBody(required=false) String body) {
        return profiles.save(auth,idempotencyKey,body);
    }
}
