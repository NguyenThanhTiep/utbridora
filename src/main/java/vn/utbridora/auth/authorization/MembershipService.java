package vn.utbridora.auth.authorization;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.organization.*;
import vn.utbridora.auth.user.*;

@Service
public class MembershipService {
    private final MembershipRepository memberships;private final OrganizationRepository organizations;
    private final UserRepository users;private final AuditService audit;
    private final AdministrationGuard administration;
    public MembershipService(MembershipRepository memberships,OrganizationRepository organizations,UserRepository users,AuditService audit,AdministrationGuard administration) {
        this.memberships=memberships;this.organizations=organizations;this.users=users;this.audit=audit;
        this.administration=administration;
    }
    @Transactional
    @PreAuthorize("@authorizationPolicy.hasGlobal(authentication, 'membership:admin:write')")
    public void grant(String actorId,String userId,Role role,String organizationId) {
        administration.lockAndCheckActor(actorId,"membership:admin:write");
        assertValidScope(role,organizationId);
        UserAccount subject=users.findByIdForUpdate(userId).orElseThrow(()->new IllegalArgumentException("User not found"));
        if(subject.status==AccountStatus.DEACTIVATED) throw new IllegalArgumentException("Deactivated account cannot receive roles");
        Membership member=(organizationId==null
           ? memberships.findByUserIdAndOrganizationIdIsNullAndRole(userId,role)
           : memberships.findByUserIdAndOrganizationIdAndRole(userId,organizationId,role)).orElseGet(()->{
              Membership m=new Membership();m.userId=userId;m.role=role;m.organizationId=organizationId;return m;
           });
        if(member.id!=null && member.active) return;
        member.active=true;member.updatedAt=Instant.now();memberships.save(member);
        subject.securityVersion++;
        audit.writeScoped(actorId,"MEMBERSHIP_GRANTED","membership",member.id,"SUCCESS",organizationId,"MEMBERSHIP_GRANTED");
    }
    @Transactional
    @PreAuthorize("@authorizationPolicy.hasGlobal(authentication, 'membership:admin:write')")
    public void revoke(String actorId,String userId,Role role,String organizationId) {
        administration.lockAndCheckActor(actorId,"membership:admin:write");
        assertValidScope(role,organizationId);
        if(actorId.equals(userId) && role==Role.ADMIN) throw new IllegalArgumentException("Cannot revoke your own administrator membership");
        UserAccount subject=users.findByIdForUpdate(userId).orElseThrow(()->new IllegalArgumentException("User not found"));
        Membership member=(organizationId==null
           ? memberships.findByUserIdAndOrganizationIdIsNullAndRole(userId,role)
           : memberships.findByUserIdAndOrganizationIdAndRole(userId,organizationId,role))
           .orElseThrow(()->new IllegalArgumentException("Membership not found"));
        if(!member.active) return;
        if(role==Role.ADMIN && subject.status==AccountStatus.ACTIVE
            && memberships.countActiveGlobalRole(Role.ADMIN,AccountStatus.ACTIVE)<=1)
            throw new IllegalArgumentException("Cannot revoke last active administrator");
        member.active=false;member.updatedAt=Instant.now();subject.securityVersion++;
        audit.writeScoped(actorId,"MEMBERSHIP_REVOKED","membership",member.id,"SUCCESS",organizationId,"MEMBERSHIP_REVOKED");
    }
    private void assertValidScope(Role role,String organizationId) {
        if(role==null) throw new IllegalArgumentException("Role is required");
        if(role==Role.ADMIN) {
            if(organizationId!=null) throw new IllegalArgumentException("Global administrator cannot have an organization scope");
            return;
        }
        if(organizationId==null || organizationId.isBlank()) throw new IllegalArgumentException("Organization ID required");
        Organization o=organizations.findById(organizationId)
            .orElseThrow(()->new IllegalArgumentException("Organization not found"));
        if(role==Role.ENTERPRISE && o.type!=Organization.Type.ENTERPRISE
           || (role==Role.STUDENT || role==Role.SCHOOL_STAFF) && o.type!=Organization.Type.UNIVERSITY)
            throw new IllegalArgumentException("Organization type incompatible with role");
    }
}
