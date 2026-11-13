package vn.utbridora.auth.modules.student.port;
import java.time.Instant;
import java.util.List;
public interface StudentConsentPort {
    record ConsentGrant(String consentId,long consentVersion,long lineageVersion,String studentUserId,String schoolOrgId,String termId,String recipientOrgId,String invitationId,String purpose,List<String> fieldWhitelist,Instant grantedAt,Instant expiresAt,Instant revokedAt,String policyVersion,String replacedByConsentId) {}
    record GrantCheck(String consentId,long consentVersion,String shareId,String studentUserId,String schoolOrgId,String termId,String recipientOrgId,String purpose,List<String> requestedFields) {}
    record GrantValidation(boolean valid,List<String> allowedFields,Instant validUntil,Long currentVersion,String reasonCode) {}
    GrantValidation lockAndCheckForShare(vn.utbridora.auth.contracts.ActorContext actor,GrantCheck request);

}
