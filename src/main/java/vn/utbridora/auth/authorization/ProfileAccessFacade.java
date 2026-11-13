package vn.utbridora.auth.authorization;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.contracts.profile.ProfileAccessPort;
import vn.utbridora.auth.modules.cooperation.port.*;
import vn.utbridora.auth.modules.student.port.*;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.operations.WorkflowValues.*;
import static vn.utbridora.auth.modules.cooperation.application.SchoolSharingPolicyService.fields;

/** T1 facade joins current T1 authority, T5 share/policy and T2 consent. No role or ADMIN bypass. */
@Service
public class ProfileAccessFacade implements ProfileAccessPort {
    private final AuthorizationPolicy core;
    private final SchoolDelegationAccess delegation;
    private final ProfileShareReadPort shares;
    private final SchoolSharingPolicyPort policies;
    private final StudentConsentPort consents;
    private final org.springframework.beans.factory.ObjectProvider<StudentProfileProjectionPort> profiles;
    public ProfileAccessFacade(AuthorizationPolicy core,SchoolDelegationAccess delegation,ProfileShareReadPort shares,
        SchoolSharingPolicyPort policies,StudentConsentPort consents,
        org.springframework.beans.factory.ObjectProvider<StudentProfileProjectionPort> profiles) {
        this.core=core;this.delegation=delegation;this.shares=shares;this.policies=policies;this.consents=consents;this.profiles=profiles;
    }
    @Override @Transactional(propagation=Propagation.MANDATORY,noRollbackFor=vn.utbridora.auth.operations.OperationFailure.class)
    public Decision decide(ActorContext context,Query q) {
        var auth=context.authentication();actor(auth);uuid(q.studentUserId());uuid(q.termId());fields(q.requestedFields(),false);
        String school=profiles.getObject().schoolOf(context,q.studentUserId(),q.termId());
        if("SCHOOL_REVIEW".equals(q.purpose())) {
            if(q.shareId()!=null) throw failure(400,"SCHEMA_VALIDATION");
            var mask=delegation.profileFields(auth,school,q.termId());
            var allowed=q.requestedFields().stream().filter(mask::contains).toList();
            if(allowed.isEmpty()) throw failure(403,"ACCESS_DENIED");
            return new Decision(true,allowed,null,null,null,policies.current(school,q.termId()).version(),"ALLOWED");
        }
        if(!"INTERNSHIP_SELECTION".equals(q.purpose())) throw failure(400,"SCHEMA_VALIDATION");
        var share=shares.readCurrent(context,q.shareId());
        if(!q.shareId().equals(share.shareId()) || !q.studentUserId().equals(share.studentUserId()) || !school.equals(share.schoolOrgId())
            || !q.termId().equals(share.termId()) || !q.purpose().equals(share.purpose())
            || context.organizationId()!=null && !context.organizationId().equals(share.recipientOrgId())
            || !core.holdOrganization(auth,share.recipientOrgId(),Role.ENTERPRISE,"student_profile:shared:read"))
            throw failure(404,"RESOURCE_NOT_FOUND");
        Instant now=Instant.now();
        if(share.revokedAt()!=null || now.isBefore(share.validFrom()) || !now.isBefore(share.expiresAt())) throw failure(403,"ACCESS_DENIED");
        var policy=policies.current(school,q.termId());
        var allowed=q.requestedFields().stream().filter(share.fieldWhitelist()::contains).filter(policy.allowedFields()::contains).toList();
        if(allowed.isEmpty()) throw failure(403,"ACCESS_DENIED");
        var consent=consents.lockAndCheckForShare(context,new StudentConsentPort.GrantCheck(share.consentId(),share.consentVersion(),
            share.shareId(),q.studentUserId(),school,q.termId(),share.recipientOrgId(),q.purpose(),allowed));
        if(!consent.valid()) throw failure(403,"ACCESS_DENIED");
        Instant until=share.expiresAt().isBefore(consent.validUntil())?share.expiresAt():consent.validUntil();
        return new Decision(true,allowed,until,share.consentVersion(),share.shareVersion(),policy.version(),"ALLOWED");
    }
}
