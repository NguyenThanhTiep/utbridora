package vn.utbridora.auth.modules.cooperation.application;

import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.modules.cooperation.port.SchoolSharingPolicyPort;
import vn.utbridora.auth.modules.student.port.StudentConsentPort;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** Current share/consent checks held until the proposal transaction commits. */
@Component
public class ProposalShareAccess {
    private final JdbcTemplate jdbc;
    private final AuthorizationPolicy core;
    private final org.springframework.beans.factory.ObjectProvider<StudentConsentPort> consents;
    private final SchoolSharingPolicyPort policies;
    public ProposalShareAccess(JdbcTemplate jdbc,AuthorizationPolicy core,org.springframework.beans.factory.ObjectProvider<StudentConsentPort> consents,SchoolSharingPolicyPort policies) {
        this.jdbc=jdbc;this.core=core;this.consents=consents;this.policies=policies;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void require(Authentication auth,ProposalService.Item item,CooperationService.CooperationDto c) {
        if(!visible(auth,item,c)) throw failure(403,"ACCESS_DENIED");
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public boolean visible(Authentication auth,ProposalService.Item item,CooperationService.CooperationDto c) {
        if(item.shareId()==null || !core.holdOrganization(auth,c.enterpriseOrgId(),Role.ENTERPRISE,"student_profile:shared:read")) return false;
        var share=jdbc.query("SELECT data_json FROM profile_shares WHERE id=? AND proposal_item_id=? FOR SHARE",
            (r,n)->JSON.readValue(r.getString(1),ProfileShareService.Share.class),item.shareId(),item.proposalItemId())
            .stream().findFirst().orElse(null);
        if(share==null) return false;
        Instant now=Instant.now();
        if(!share.shareId().equals(item.shareId()) || !share.proposalItemId().equals(item.proposalItemId())
            || !share.studentUserId().equals(item.studentUserId()) || !share.schoolOrgId().equals(c.schoolOrgId())
            || !share.termId().equals(c.termId()) || !share.recipientOrgId().equals(c.enterpriseOrgId())
            || !"INTERNSHIP_SELECTION".equals(share.purpose()) || share.revokedAt()!=null
            || now.isBefore(share.validFrom()) || !now.isBefore(share.expiresAt())) return false;
        var allowed=share.fieldWhitelist().stream().filter(policies.current(c.schoolOrgId(),c.termId()).allowedFields()::contains).toList();
        if(allowed.isEmpty()) return false;
        var consent=consents.getObject().lockAndCheckForShare(ActorContext.fromAuthenticatedWorkflow(auth,c.enterpriseOrgId()),
            new StudentConsentPort.GrantCheck(share.consentId(),share.consentVersion(),share.shareId(),item.studentUserId(),
                c.schoolOrgId(),c.termId(),c.enterpriseOrgId(),"INTERNSHIP_SELECTION",allowed));
        return consent.valid();
    }
}
