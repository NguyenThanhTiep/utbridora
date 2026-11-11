package vn.utbridora.auth.tokens;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.Authentication;
/** T3 owns scope and assignments; T1 owns identity and onboarding acceptance. No public callback. */
public interface MentorScopePort {
    record InviteScope(String hrAssignmentId,String hrUserId,String enterpriseOrgId,String schoolOrgId,String termId,List<String> groupIds,Instant validUntil) {}
    InviteScope validateInvite(Authentication actor,String enterprise,String term,List<String> groups);
    List<String> activateAccepted(String invitationId,String subject,InviteScope scope);
    void revokeAccepted(String invitationId,InviteScope scope);
}
