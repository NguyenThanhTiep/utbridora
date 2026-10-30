package vn.utbridora.auth.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.api.ApiDtos.*;
import vn.utbridora.auth.audit.*;
import vn.utbridora.auth.authorization.MembershipService;
import vn.utbridora.auth.core.AccountLifecycleService;
import vn.utbridora.auth.organization.*;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.tokens.AccountTokenService;
import vn.utbridora.auth.user.*;

@RestController @RequestMapping("/api/v1/admin")
public class AdminController {
    private final OrganizationRepository organizations;
    private final AccountTokenService tokens;
    private final AccountLifecycleService lifecycle;
    private final MembershipService membershipService;
    private final AuditService audit;
    private final AuditRepository auditRepository;
    private final UserRepository users;
    public AdminController(OrganizationRepository organizations,AccountTokenService tokens,
              AccountLifecycleService lifecycle,MembershipService membershipService,
              AuditService audit,AuditRepository auditRepository,UserRepository users) {
        this.organizations=organizations;this.tokens=tokens;this.lifecycle=lifecycle;
        this.membershipService=membershipService;this.audit=audit;this.auditRepository=auditRepository;this.users=users;
    }
    private static String actor(Authentication a){return ((AppPrincipal)a.getPrincipal()).userId();}
    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/organizations")
    @PreAuthorize("@authorizationPolicy.hasGlobal(authentication, 'organization:admin:create')")
    public Organization createOrganization(Authentication auth,@Valid @RequestBody CreateOrganization body) {
        Organization o=new Organization();o.name=body.name().trim();o.type=body.type();o.status=Organization.Status.PENDING;
        Organization stored=organizations.save(o);
        audit.write(actor(auth),"ORG_CREATED","organization",stored.id,"SUCCESS");
        return stored;
    }
    @PostMapping("/invitations")
    @PreAuthorize("@authorizationPolicy.hasGlobal(authentication, 'membership:admin:write')")
    public Message invite(Authentication auth,@Valid @RequestBody CreateInvitation body) {
        tokens.invite(actor(auth),body.email(),body.role(),body.organizationId());
        return new Message("Invitation queued for delivery");
    }
    @PostMapping("/memberships")
    @PreAuthorize("@authorizationPolicy.hasGlobal(authentication, 'membership:admin:write')")
    public Message grant(Authentication auth,@Valid @RequestBody ChangeMembership body) {
        membershipService.grant(actor(auth),body.userId(),body.role(),body.organizationId());
        return new Message("Membership granted; existing sessions revoked");
    }
    @DeleteMapping("/memberships")
    @PreAuthorize("@authorizationPolicy.hasGlobal(authentication, 'membership:admin:write')")
    public Message revokeMembership(Authentication auth,@Valid @RequestBody ChangeMembership body) {
        membershipService.revoke(actor(auth),body.userId(),body.role(),body.organizationId());
        return new Message("Membership revoked; existing sessions revoked");
    }
    @GetMapping("/audit-logs")
    @PreAuthorize("@authorizationPolicy.hasGlobal(authentication, 'audit:global:read')")
    public Page<AuditLogView> auditLogs(@RequestParam(defaultValue="0") int page,
                                       @RequestParam(defaultValue="25") int size,
                                       @RequestParam(required=false) String action,
                                       @RequestParam(required=false) String actorId,
                                       @RequestParam(required=false) String organizationId,
                                       @RequestParam(required=false) String result,
                                       @RequestParam(required=false) java.time.Instant from,
                                       @RequestParam(required=false) java.time.Instant until) {
        if(page<0 || page>10000 || size<1 || size>100) throw new IllegalArgumentException("Invalid pagination");
        PageRequest paging=AdminReadController.page(page,size,"createdAt");
        // Restrict filters to indexed/known fields; no free-form SQL or raw request payloads.
        if(action!=null && !action.matches("[A-Z0-9_]{1,90}")) throw new IllegalArgumentException("Invalid action filter");
        if(actorId!=null && !actorId.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("Invalid actor filter");
        if(organizationId!=null && !organizationId.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("Invalid organization filter");
        if(result!=null && !java.util.Set.of("SUCCESS","FAILURE","DENIED").contains(result)) throw new IllegalArgumentException("Invalid result filter");
        if(from!=null && until!=null && !from.isBefore(until)) throw new IllegalArgumentException("Invalid time range");
        org.springframework.data.jpa.domain.Specification<AuditLog> filter=(root,query,cb)->cb.conjunction();
        if(action!=null) filter=filter.and((root,query,cb)->cb.equal(root.get("action"),action));
        if(actorId!=null) filter=filter.and((root,query,cb)->cb.equal(root.get("actorId"),actorId));
        if(organizationId!=null) filter=filter.and((root,query,cb)->cb.equal(root.get("organizationId"),organizationId));
        if(result!=null) filter=filter.and((root,query,cb)->cb.equal(root.get("result"),result));
        if(from!=null) filter=filter.and((root,query,cb)->cb.greaterThanOrEqualTo(root.get("createdAt"),from));
        if(until!=null) filter=filter.and((root,query,cb)->cb.lessThan(root.get("createdAt"),until));
        Page<AuditLog> found=auditRepository.findAll(filter,paging);
        return found.map(AuditLogView::from);
    }
    @PatchMapping("/users/{id}/revoke-sessions")
    @PreAuthorize("@authorizationPolicy.hasGlobal(authentication, 'account:admin:revoke')")
    public Message revokeSessions(Authentication auth,@PathVariable String id) {
        lifecycle.revokeSessions(actor(auth),id);
        return new Message("Existing sessions will be rejected on their next request");
    }
    @PatchMapping("/users/{id}/status")
    @PreAuthorize("@authorizationPolicy.hasGlobal(authentication, 'account:admin:status')")
    public Message status(Authentication auth,@PathVariable String id,@Valid @RequestBody UpdateStatus body) {
        lifecycle.transition(actor(auth),id,body.status(),body.reasonCode());
        return new Message("Account status updated and audited");
    }
}
