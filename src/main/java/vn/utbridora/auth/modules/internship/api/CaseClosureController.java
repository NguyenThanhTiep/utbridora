package vn.utbridora.auth.modules.internship.api;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.internship.infrastructure.*;
@RestController
@RequestMapping("/api/v1/t3/cases/{caseId}/close")
public class CaseClosureController {
    private final CaseClosureService service;
    public CaseClosureController(CaseClosureService service) {this.service=service;}
    @ModelAttribute void privateResponse(jakarta.servlet.http.HttpServletResponse response) {response.setHeader("Cache-Control","private, no-store");}
    @PostMapping public CaseRuntime.CaseRef close(Authentication auth,@PathVariable UUID caseId,@RequestHeader("Idempotency-Key") String key,@RequestBody String body) {
        return service.close(auth,caseId,key,InternshipOperationsJson.read(body,InternshipOperationsDtos.CloseCase.class));
    }
}
