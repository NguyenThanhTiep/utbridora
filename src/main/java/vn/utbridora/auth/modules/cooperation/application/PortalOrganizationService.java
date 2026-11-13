package vn.utbridora.auth.modules.cooperation.application;

import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.authorization.EffectivePermissionService;
import vn.utbridora.auth.operations.OperationFailure;
import vn.utbridora.auth.organization.Organization;
import vn.utbridora.auth.organization.OrganizationRepository;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.user.Role;

@Service
public class PortalOrganizationService {
    public record OrganizationOption(String id,String name,Organization.Type type) {}
    private final AuthorizationPolicy access;
    private final EffectivePermissionService permissions;
    private final OrganizationRepository organizations;
    public PortalOrganizationService(AuthorizationPolicy access,EffectivePermissionService permissions,OrganizationRepository organizations) {
        this.access=access;this.permissions=permissions;this.organizations=organizations;
    }
    @Transactional(readOnly=true)
    public List<OrganizationOption> list(Authentication actor) {
        if (actor==null || !(actor.getPrincipal() instanceof AppPrincipal principal) || !access.isSelf(actor,principal.userId()))
            throw new OperationFailure(401,"SESSION_EXPIRED","Phiên đăng nhập không còn hiệu lực.",null);
        var grants=permissions.forUser(principal.userId());
        Set<String> enterprise=new HashSet<>(),school=new HashSet<>();
        for (var grant:grants) {
            if (grant.organizationId()==null) continue;
            if (grant.role()==Role.ENTERPRISE && grant.permissions().contains("cooperation:enterprise:create")) enterprise.add(grant.organizationId());
            if (grant.role()==Role.SCHOOL_STAFF) school.add(grant.organizationId());
        }
        Set<String> ids=new HashSet<>(enterprise);ids.addAll(school);
        if (ids.isEmpty()) return List.of();
        return organizations.findAllById(ids).stream()
            .filter(row->row.status==Organization.Status.VERIFIED &&
                (row.type==Organization.Type.ENTERPRISE && enterprise.contains(row.id) || row.type==Organization.Type.UNIVERSITY && school.contains(row.id)))
            .map(row->new OrganizationOption(row.id,row.name,row.type))
            .sorted(Comparator.comparing(OrganizationOption::name,String.CASE_INSENSITIVE_ORDER).thenComparing(OrganizationOption::id)).toList();
    }
}
