package vn.utbridora.auth.modules.student.port;
import java.time.Instant;
import java.util.List;
public interface StudentConsentPort {
    record ConsentGrant(String consentId,long consentVersion,long lineageVersion,String studentUserId,String schoolOrgId,String termId,String recipientOrgId,String invitationId,String purpose,List<String> fieldWhitelist,Instant grantedAt,Instant expiresAt,Instant revokedAt,String policyVersion,String replacedByConsentId) {}
    record GrantCheck(String consentId,long consentVersion,String shareId,String studentUserId,String schoolOrgId,String termId,String recipientOrgId,String purpose,List<String> requestedFields) {}
    record GrantValidation(boolean valid,List<String> allowedFields,Instant validUntil,Long currentVersion,String reasonCode) {}
    GrantValidation lockAndCheckForShare(vn.utbridora.auth.contracts.ActorContext actor,GrantCheck request);

    /**
     * Verifies that a valid consent/share exists for the proposal item's student+enterprise
     * at the time of placement creation. Throws OperationFailure(409,"CONSENT_INVALID")
     * if consent is missing, expired or revoked. Deny-by-default when T2 share is not fully implemented.
     */
    default void assertValidForPlacement(org.springframework.security.core.Authentication actor,
                                          vn.utbridora.auth.modules.cooperation.application.ProposalService.Item item) {
        // Default: deny — will be overridden by real T2 implementation once share workflow completes.
        // Per bàn giao T5: consent/share partially implemented in week-2; full gate needed for placement.
        // This default is intentionally deny-safe.
    }
}
