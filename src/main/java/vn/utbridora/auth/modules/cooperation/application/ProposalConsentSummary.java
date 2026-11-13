package vn.utbridora.auth.modules.cooperation.application;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.modules.cooperation.port.SchoolSharingPolicyPort;
import vn.utbridora.auth.modules.student.port.StudentConsentPort.ConsentGrant;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** School-only metadata. Caller must authorize the cooperation's school/term first. */
@Component
public class ProposalConsentSummary {
    public record Summary(String activeConsentId,Long activeConsentVersion,Instant activeConsentExpiresAt,
                          List<String> shareableFields,boolean shareIsActive,boolean shareNeedsUpdate,Long shareVersion) {}
    private final JdbcTemplate jdbc;
    private final SchoolSharingPolicyPort policies;
    public ProposalConsentSummary(JdbcTemplate jdbc,SchoolSharingPolicyPort policies) {this.jdbc=jdbc;this.policies=policies;}
    @Transactional(propagation=Propagation.MANDATORY)
    public Summary read(ProposalService.Item item,CooperationService.CooperationDto cooperation) {
        var consent=jdbc.query("SELECT s.data_json FROM student_consent_lineages l JOIN student_consents s ON s.id=l.current_consent_id AND s.lineage_id=l.id WHERE l.student_user_id=? AND l.school_org_id=? AND l.term_id=? AND l.recipient_org_id=? AND l.purpose='INTERNSHIP_SELECTION' FOR SHARE",
            (r,n)->JSON.readValue(r.getString(1),ConsentGrant.class),item.studentUserId(),cooperation.schoolOrgId(),cooperation.termId(),cooperation.enterpriseOrgId())
            .stream().findFirst().orElse(null);
        Instant now=Instant.now();
        boolean active=consent!=null && consent.studentUserId().equals(item.studentUserId())
            && consent.schoolOrgId().equals(cooperation.schoolOrgId()) && consent.termId().equals(cooperation.termId())
            && consent.recipientOrgId().equals(cooperation.enterpriseOrgId()) && "INTERNSHIP_SELECTION".equals(consent.purpose())
            && consent.revokedAt()==null && consent.replacedByConsentId()==null && !now.isBefore(consent.grantedAt()) && now.isBefore(consent.expiresAt());
        var policy=policies.current(cooperation.schoolOrgId(),cooperation.termId());
        List<String> fields=active?consent.fieldWhitelist().stream().filter(policy.allowedFields()::contains).toList():List.of();
        var share=item.shareId()==null?null:jdbc.query("SELECT data_json FROM profile_shares WHERE id=? AND proposal_item_id=? FOR SHARE",
            (r,n)->JSON.readValue(r.getString(1),ProfileShareService.Share.class),item.shareId(),item.proposalItemId()).stream().findFirst().orElse(null);
        boolean shared=active && share!=null && share.studentUserId().equals(item.studentUserId())
            && share.schoolOrgId().equals(cooperation.schoolOrgId()) && share.termId().equals(cooperation.termId())
            && share.recipientOrgId().equals(cooperation.enterpriseOrgId()) && "INTERNSHIP_SELECTION".equals(share.purpose())
            && share.consentId().equals(consent.consentId()) && share.consentVersion()==consent.consentVersion()
            && share.revokedAt()==null && !now.isBefore(share.validFrom()) && now.isBefore(share.expiresAt())
            && share.fieldWhitelist().stream().anyMatch(fields::contains);
        boolean invitationAccepted=item.invitationId()!=null && !jdbc.queryForList("SELECT id FROM internship_invitations WHERE id=? AND student_user_id=? AND state='ACCEPTED'",String.class,item.invitationId(),item.studentUserId()).isEmpty();
        boolean canUpdate=("SHARED".equals(item.state()) || "ENTERPRISE_ACCEPTED".equals(item.state())) && item.shareId()!=null && !shared && invitationAccepted && !fields.isEmpty() && now.isBefore(item.reservationUntil());
        return new Summary(active?consent.consentId():null,active?consent.consentVersion():null,active?consent.expiresAt():null,fields,shared,canUpdate,share==null?null:share.shareVersion());
    }
}
