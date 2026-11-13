package vn.utbridora.auth.modules.cooperation.api;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.cooperation.application.PortalOrganizationService;

@RestController
@RequestMapping("/api/v1/cooperation/organizations")
public class PortalOrganizationController {
    private final PortalOrganizationService service;
    public PortalOrganizationController(PortalOrganizationService service) {this.service=service;}
    @GetMapping
    public List<PortalOrganizationService.OrganizationOption> list(Authentication actor) {return service.list(actor);}
}
