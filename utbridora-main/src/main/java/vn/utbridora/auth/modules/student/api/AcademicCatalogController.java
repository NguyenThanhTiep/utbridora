package vn.utbridora.auth.modules.student.api;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.student.application.AcademicCatalogService;
import vn.utbridora.auth.modules.student.application.AcademicCatalogService.*;

@RestController
@RequestMapping("/api/v1/students/me/catalogs")
public class AcademicCatalogController {
    private final AcademicCatalogService catalogs;
    public AcademicCatalogController(AcademicCatalogService catalogs) { this.catalogs=catalogs; }
    @GetMapping public CatalogPage list(Authentication actor,@RequestParam String termId,@RequestParam Kind kind,
            @RequestParam(required=false) String expectedCatalogVersion,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="25") int size) {
        return catalogs.list(actor,termId,kind,expectedCatalogVersion,page,size);
    }
}
