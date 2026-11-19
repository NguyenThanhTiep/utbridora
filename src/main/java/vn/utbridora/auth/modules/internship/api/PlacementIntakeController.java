package vn.utbridora.auth.modules.internship.api;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.utbridora.auth.files.RosterImportQuarantine;
import vn.utbridora.auth.modules.internship.infrastructure.PlacementIntakeService;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@RestController
@RequestMapping("/api/v1/t3/cohorts/{cohortId}/placements/preview")
public class PlacementIntakeController {
    private final PlacementIntakeService service;private final RosterImportQuarantine quarantine;
    public PlacementIntakeController(PlacementIntakeService service,RosterImportQuarantine quarantine) {this.service=service;this.quarantine=quarantine;}
    @ModelAttribute void privateResponse(jakarta.servlet.http.HttpServletResponse response) {response.setHeader("Cache-Control","private, no-store");response.setHeader("X-Content-Type-Options","nosniff");}
    @PostMapping(consumes="multipart/form-data") public PlacementIntakeService.PlacementPreview preview(Authentication actor,@PathVariable UUID cohortId,
        @RequestHeader("Idempotency-Key") String key,@RequestPart MultipartFile file,@RequestParam long expectedCohortVersion,jakarta.servlet.http.HttpServletRequest request) {
        uuid(key);version(expectedCohortVersion);service.authorize(actor,cohortId,expectedCohortVersion);
        if(request.getParameterMap().keySet().stream().anyMatch(k->!k.equals("expectedCohortVersion"))) throw failure(400,"SCHEMA_VALIDATION");
        try {var counts=new java.util.HashMap<String,Integer>();for(var part:request.getParts()) {
            if(!java.util.Set.of("file","expectedCohortVersion").contains(part.getName()) || counts.merge(part.getName(),1,Integer::sum)!=1) throw failure(400,"SCHEMA_VALIDATION");
        }if(counts.getOrDefault("file",0)!=1 || counts.getOrDefault("expectedCohortVersion",0)!=1) throw failure(400,"SCHEMA_VALIDATION");}
        catch(java.io.IOException|jakarta.servlet.ServletException invalid) {throw failure(400,"SCHEMA_VALIDATION");}
        try(var scanned=quarantine.preparePlacements(file)) {return service.preview(actor,cohortId,key,expectedCohortVersion,scanned);}
    }
}
