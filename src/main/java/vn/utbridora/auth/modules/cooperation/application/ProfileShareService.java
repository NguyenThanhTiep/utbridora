package vn.utbridora.auth.modules.cooperation.application;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.modules.cooperation.port.SchoolSharingPolicyPort;
import vn.utbridora.auth.modules.student.port.StudentConsentPort;
import vn.utbridora.auth.modules.student.port.StudentConsentPort.GrantCheck;
import vn.utbridora.auth.modules.student.port.StudentProfileProjectionPort;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.contracts.profile.ProfileAccessPort;
import vn.utbridora.auth.operations.OperationLedger;
import static vn.utbridora.auth.operations.WorkflowValues.*;
import static vn.utbridora.auth.modules.cooperation.application.SchoolSharingPolicyService.fields;

/** T5 owns share lifecycle; current access and profile projection stay behind the BC24 owner ports. */
@Service
public class ProfileShareService {
    public record GrantProfileShare(String proposalItemId,String consentId,Long expectedConsentVersion,Long expectedProposalVersion,List<String> fieldWhitelist,Instant expiresAt) {}
    public record Share(String shareId,long shareVersion,String consentId,long consentVersion,String studentUserId,String schoolOrgId,String termId,String recipientOrgId,String proposalItemId,String purpose,List<String> fieldWhitelist,Instant validFrom,Instant expiresAt,Instant revokedAt,String grantedByUserId) {}
    public record Revoke(Long expectedVersion,String reasonCode) {}
    public record ProfileProjection(String studentUserId,long profileVersion,List<String> allowedFields,StudentProfileProjectionPort.Data data) {}
    private final JdbcTemplate jdbc;private final ProposalService proposals;private final StudentConsentPort consents;
    private final SchoolSharingPolicyPort policies;private final SchoolDelegationAccess scope;
    private final StudentProfileProjectionPort profiles;private final OperationLedger ledger;private final AuditService audit;
    public ProfileShareService(JdbcTemplate jdbc,ProposalService proposals,StudentConsentPort consents,SchoolSharingPolicyPort policies,SchoolDelegationAccess scope,StudentProfileProjectionPort profiles,OperationLedger ledger,AuditService audit) {
        this.jdbc=jdbc;this.proposals=proposals;this.consents=consents;this.policies=policies;this.scope=scope;this.profiles=profiles;this.ledger=ledger;this.audit=audit;
    }
    @Transactional public Share grant(Authentication auth,String id,String key,GrantProfileShare request) {
        if(!uuid(id).equals(uuid(request.proposalItemId()))) throw failure(400,"SCHEMA_VALIDATION");fields(request.fieldWhitelist(),false);version(request.expectedConsentVersion());version(request.expectedProposalVersion());
        var c=proposals.cooperationForItem(id);scope.require(auth,c.schoolOrgId(),c.termId(),"student_profile:share:school:manage");
        return ledger.execute(new OperationLedger.Command(actor(auth),c.schoolOrgId(),"PROFILE_SHARE_GRANT",key,request,201),Share.class,()->{
            var item=proposals.item(id);if(item.version()!=request.expectedProposalVersion()) throw failure(409,"VERSION_CONFLICT");
            boolean firstShare="ACCEPTED".equals(item.state()) && item.shareId()==null;
            boolean replacement=Set.of("SHARED","ENTERPRISE_ACCEPTED").contains(item.state()) && item.shareId()!=null;
            boolean canShare=firstShare || replacement;
            if(!canShare) throw failure(409,"PROPOSAL_STATE_CONFLICT");
            if(item.invitationId()==null || !"ACCEPTED".equals(jdbc.queryForObject("SELECT state FROM internship_invitations WHERE id=? FOR UPDATE",String.class,item.invitationId()))) throw failure(409,"INVITATION_STATE_CONFLICT");
            // Legacy profile reads lock share before consent lineage; replacements use the same order.
            var previous=item.shareId()==null?null:stored(item.shareId(),true);
            var consent=consents.lockAndCheckForShare(vn.utbridora.auth.contracts.ActorContext.fromAuthenticatedWorkflow(auth,c.schoolOrgId()),new GrantCheck(request.consentId(),request.expectedConsentVersion(),null,item.studentUserId(),c.schoolOrgId(),c.termId(),c.enterpriseOrgId(),"INTERNSHIP_SELECTION",request.fieldWhitelist()));
            if(!consent.valid()) throw failure(409,"CONSENT_INVALID");
            if(request.expiresAt()==null || !Instant.now().isBefore(request.expiresAt()) || request.expiresAt().isAfter(consent.validUntil())) throw failure(422,"CONSENT_EXPIRY_INVALID");
            if(!policies.current(c.schoolOrgId(),c.termId()).allowedFields().containsAll(request.fieldWhitelist())) throw failure(422,"CONSENT_FIELDS_NOT_ALLOWED");
            if(!Instant.now().isBefore(item.reservationUntil())) throw failure(409,"RESERVATION_EXPIRED");
            // Replacement is explicit and atomic. Old URLs must not retain broader fields or expiry.
            long nextShareVersion=1;Instant now=Instant.now();
            if(previous!=null) {
                nextShareVersion=version(previous.shareVersion()+1);
                if(previous.revokedAt()==null) {
                    var revoked=new Share(previous.shareId(),nextShareVersion,previous.consentId(),previous.consentVersion(),previous.studentUserId(),previous.schoolOrgId(),previous.termId(),previous.recipientOrgId(),previous.proposalItemId(),previous.purpose(),previous.fieldWhitelist(),previous.validFrom(),previous.expiresAt(),now,previous.grantedByUserId());
                    jdbc.update("UPDATE profile_shares SET data_json=? WHERE id=?",JSON.writeValueAsString(revoked),previous.shareId());
                    audit.workflow(actor(auth),"PROFILE_SHARE_REVOKED",previous.shareId(),c.schoolOrgId(),Map.of("shareVersion",nextShareVersion,"reasonCode","SCHOOL_RESHARED"));
                }
            }
            String shareId=UUID.randomUUID().toString();var result=new Share(shareId,nextShareVersion,request.consentId(),request.expectedConsentVersion(),item.studentUserId(),c.schoolOrgId(),c.termId(),c.enterpriseOrgId(),id,"INTERNSHIP_SELECTION",List.copyOf(request.fieldWhitelist()),now,request.expiresAt(),null,actor(auth));
            // Renewing privacy access must not reset the enterprise's existing acceptance.
            String nextState=firstShare ? "SHARED" : item.state();
            jdbc.update("INSERT INTO profile_shares VALUES(?,?,?,?)",shareId,id,result.consentId(),JSON.writeValueAsString(result));jdbc.update("UPDATE proposal_items SET state=?,version=version+1,share_id=?,reservation_until=LEAST(reservation_until,?) WHERE id=?",nextState,shareId,utc(consent.validUntil()),id);
            audit.workflow(actor(auth),"PROFILE_SHARED",shareId,c.schoolOrgId(),Map.of("consentId",result.consentId(),"consentVersion",result.consentVersion(),"shareVersion",result.shareVersion(),"fieldCodes",result.fieldWhitelist(),"expiresAt",result.expiresAt().toString()));return result;
        },Share::shareId);
    }
    @Transactional public Share revoke(Authentication auth,String id,String key,Revoke request) {
        var before=stored(uuid(id),true);scope.require(auth,before.schoolOrgId(),before.termId(),"student_profile:share:school:manage");version(request.expectedVersion());reason(request.reasonCode());
        return ledger.execute(new OperationLedger.Command(actor(auth),before.schoolOrgId(),"PROFILE_SHARE_REVOKE",key,Map.of("id",id,"request",request),200),Share.class,()->{
            var c=stored(id);if(c.shareVersion()!=request.expectedVersion() || c.revokedAt()!=null) throw failure(409,"VERSION_CONFLICT");
            var result=new Share(c.shareId(),c.shareVersion()+1,c.consentId(),c.consentVersion(),c.studentUserId(),c.schoolOrgId(),c.termId(),c.recipientOrgId(),c.proposalItemId(),c.purpose(),c.fieldWhitelist(),c.validFrom(),c.expiresAt(),Instant.now(),c.grantedByUserId());
            jdbc.update("UPDATE profile_shares SET data_json=? WHERE id=?",JSON.writeValueAsString(result),id);audit.workflow(actor(auth),"PROFILE_SHARE_REVOKED",id,c.schoolOrgId(),Map.of("shareVersion",result.shareVersion(),"reasonCode",request.reasonCode()));return result;
        },Share::shareId);
    }
    @Transactional public ProfileProjection read(Authentication auth,String student,String term,String shareId,List<String> requested,String purpose) {
        fields(requested,false);
        var p=profiles.readAllowed(ActorContext.fromAuthenticatedWorkflow(auth,null),
            new ProfileAccessPort.Query(uuid(student),uuid(term),shareId,requested,purpose));
        return new ProfileProjection(p.studentUserId(),p.profileVersion(),p.allowedFields(),p.data());
    }
    private Share stored(String id) {return stored(id,false);}
    private Share stored(String id,boolean update) {return jdbc.query("SELECT data_json FROM profile_shares WHERE id=?"+(update?" FOR UPDATE":" FOR SHARE"),(r,n)->JSON.readValue(r.getString(1),Share.class),id).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));}
}
