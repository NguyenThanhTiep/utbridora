package vn.utbridora.auth.core;

import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.AdministrationGuard;
import vn.utbridora.auth.organization.MembershipRepository;
import vn.utbridora.auth.user.*;

/** All administrator-initiated status changes go through this service. */
@Service
public class AccountLifecycleService {
    private final UserRepository users;private final AccountStatusHistoryRepository history;
    private final MembershipRepository memberships;private final AuditService audit;
    private final AdministrationGuard administration;
    public AccountLifecycleService(UserRepository users,AccountStatusHistoryRepository history,
       MembershipRepository memberships,AuditService audit,AdministrationGuard administration){
       this.users=users;this.history=history;this.memberships=memberships;this.audit=audit;
       this.administration=administration;
    }
    @Transactional
    @PreAuthorize("@authorizationPolicy.hasGlobal(authentication, 'account:admin:revoke')")
    public void revokeSessions(String actorId,String userId) {
        administration.lockAndCheckActor(actorId,"account:admin:revoke");
        UserAccount user=users.findByIdForUpdate(userId)
            .orElseThrow(()->new IllegalArgumentException("Account not found"));
        user.securityVersion++;
        audit.write(actorId,"SESSIONS_REVOKED","user",userId,"SUCCESS");
    }
    @Transactional
    @PreAuthorize("@authorizationPolicy.hasGlobal(authentication, 'account:admin:status')")
    public void transition(String actorId,String userId,AccountStatus next,String reasonCode){
        administration.lockAndCheckActor(actorId,"account:admin:status");
        if(next==null) throw new IllegalArgumentException("Account status is required");
        if(actorId.equals(userId) && next!=AccountStatus.ACTIVE)
            throw new IllegalArgumentException("Administrators cannot disable themselves");
        if(reasonCode==null || !reasonCode.matches("[A-Z0-9_]{3,64}"))
            throw new IllegalArgumentException("Reason code is required (UPPER_SNAKE_CASE)");
        UserAccount user=users.findByIdForUpdate(userId)
            .orElseThrow(()->new IllegalArgumentException("Account not found"));
        AccountStatus previous=user.status;
        if(!AccountStatusPolicy.adminCanTransition(previous,next))
            throw new IllegalArgumentException("Account status transition is not allowed");
        if(previous==next) return;
        if(previous==AccountStatus.ACTIVE && next!=AccountStatus.ACTIVE
            && memberships.existsByUserIdAndOrganizationIdIsNullAndRoleAndActiveTrue(userId,Role.ADMIN)
            && memberships.countActiveGlobalRole(Role.ADMIN,AccountStatus.ACTIVE)<=1)
            throw new IllegalArgumentException("Cannot disable the last active global administrator");
        user.status=next; user.securityVersion++; user.failedAttempts=0;user.lockedUntil=null;
        AccountStatusHistory event=new AccountStatusHistory();
        event.actorId=actorId;event.userId=userId;event.fromStatus=previous;
        event.toStatus=next;event.reasonCode=reasonCode;
        history.save(event);
        audit.writeScoped(actorId,"ACCOUNT_STATUS_CHANGED","user",userId,"SUCCESS",null,reasonCode);
    }
}
