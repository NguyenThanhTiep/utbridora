package vn.utbridora.auth.api;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.organization.*;
import vn.utbridora.auth.user.*;

/** Bounded administrative reads. Never serialize UserAccount, which contains credentials. */
@RestController
@RequestMapping("/api/v1/admin")
@Transactional(readOnly=true)
public class AdminReadController {
    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final AccountStatusHistoryRepository history;
    public AdminReadController(UserRepository users,OrganizationRepository organizations,
            MembershipRepository memberships,AccountStatusHistoryRepository history) {
        this.users=users;this.organizations=organizations;this.memberships=memberships;this.history=history;
    }
    public record UserView(String userId,String email,String displayName,AccountStatus status,
                           Instant lockedUntil,List<ApiDtos.MembershipView> memberships) {}
    @GetMapping("/users")
    @PreAuthorize("@authorizationPolicy.hasGlobal(authentication, 'account:admin:status')")
    public Page<UserView> users(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size,
            @RequestParam(required=false) String search,@RequestParam(required=false) AccountStatus status) {
        PageRequest paging=page(page,size,"createdAt");
        if(search!=null && search.length()>150) throw new IllegalArgumentException("Search is too long");
        Specification<UserAccount> filter=(root,query,cb)->cb.conjunction();
        if(search!=null && !search.isBlank()) {
            String term="%"+search.trim().toLowerCase(java.util.Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
            filter=filter.and((root,query,cb)->cb.or(cb.like(cb.lower(root.get("email")),term,'!'),cb.like(cb.lower(root.get("displayName")),term,'!')));
        }
        if(status!=null) filter=filter.and((root,query,cb)->cb.equal(root.get("status"),status));
        return users.findAll(filter,paging).map(u->new UserView(u.id,u.email,u.displayName,u.status,u.lockedUntil,
            memberships.findByUserIdAndActiveTrue(u.id).stream().map(m->new ApiDtos.MembershipView(m.role,m.organizationId)).toList()));
    }
    @GetMapping("/organizations")
    @PreAuthorize("@authorizationPolicy.hasGlobal(authentication, 'membership:admin:write')")
    public Page<Organization> organizations(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {
        return organizations.findAll(page(page,size,"createdAt"));
    }
    @GetMapping("/users/{id}/status-history")
    @PreAuthorize("@authorizationPolicy.hasGlobal(authentication, 'account:admin:status')")
    public Page<AccountStatusHistory> history(@PathVariable String id,@RequestParam(defaultValue="0") int page,
                                              @RequestParam(defaultValue="25") int size) {
        return history.findByUserId(id,page(page,size,"changedAt"));
    }
    static PageRequest page(int page,int size,String order) {
        if(page<0 || page>10000 || size<1 || size>100) throw new IllegalArgumentException("Invalid pagination");
        return PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,order).and(Sort.by("id")));
    }
}
