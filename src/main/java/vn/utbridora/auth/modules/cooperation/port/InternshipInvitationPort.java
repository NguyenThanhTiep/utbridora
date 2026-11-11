package vn.utbridora.auth.modules.cooperation.port;

import java.time.Instant;
import org.springframework.security.core.Authentication;
public interface InternshipInvitationPort {
    record InternshipInvitation(String invitationId,String proposalItemId,String placementCandidateId,String studentUserId,String schoolOrgId,String enterpriseOrgId,String termId,String opportunityId,String title,String enterpriseName,String state,Instant expiresAt,Instant decidedAt,String decisionReasonCode,long version,String placementId) {}
    InternshipInvitation readMine(Authentication actor,String invitationId);
}
