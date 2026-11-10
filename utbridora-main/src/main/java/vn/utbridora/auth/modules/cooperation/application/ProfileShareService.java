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
import vn.utbridora.auth.modules.student.persistence.StudentProfileRepository;
import vn.utbridora.auth.operations.OperationLedger;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.operations.WorkflowValues.*;
import static vn.utbridora.auth.modules.cooperation.application.SchoolSharingPolicyService.fields;

/** Minimal BC24 integration needed to prove revocation on a real subsequent HTTP profile read. */
@Service
public class ProfileShareService {
    public record GrantProfileShare(String proposalItemId,String consentId,Long expectedConsentVersion,Long expectedProposalVersion,List<String> fieldWhitelist,Instant expiresAt) {}
    public record Share(String shareId,long shareVersion,String consentId,long consentVersion,String studentUserId,String schoolOrgId,String termId,String recipientOrgId,String proposalItemId,String purpose,List<String> fieldWhitelist,Instant validFrom,Instant expiresAt,Instant revokedAt,String grantedByUserId) {}
    public record Revoke(Long expectedVersion,String reasonCode) {}
    public record ProfileProjection(String studentUserId,long profileVersion,List<String> allowedFields,Map<String,Object> data) {}
    private final JdbcTemplate jdbc;private final ProposalService proposals;private final CooperationService cooperations;private final StudentConsentPort consents;
    private final SchoolSharingPolicyPort policies;private final SchoolDelegationAccess scope;private final AuthorizationPolicy core;
    private final StudentProfileRepository profiles;private final OperationLedger ledger;private final AuditService audit;
    public ProfileShareService(JdbcTemplate jdbc,ProposalService proposals,CooperationService cooperations,StudentConsentPort consents,SchoolSharingPolicyPort policies,SchoolDelegationAccess scope,AuthorizationPolicy core,StudentProfileRepository profiles,OperationLedger ledger,AuditService audit) {
        this.jdbc=jdbc;this.proposals=proposals;this.cooperations=cooperations;this.consents=consents;this.policies=policies;this.scope=scope;this.core=core;this.profiles=profiles;this.ledger=ledger;this.audit=audit;
    }
    @Transactional public Share grant(Authentication auth,String id,String key,GrantProfileShare request) {
        if(!uuid(id).equals(uuid(request.proposalItemId()))) throw failure(400,"SCHEMA_VALIDATION");fields(request.fieldWhitelist(),false);version(request.expectedConsentVersion());version(request.expectedProposalVersion());
        var before=proposals.item(id);var c=cooperations.read(before.cooperationId());scope.require(auth,c.schoolOrgId(),c.termId(),"student_profile:share:school:manage");
        return ledger.execute(new OperationLedger.Command(actor(auth),c.schoolOrgId(),"PROFILE_SHARE_GRANT",key,request,201),Share.class,()->{
            var item=proposals.item(id);if(item.version()!=request.expectedProposalVersion()) throw failure(409,"VERSION_CONFLICT");
            if(!item.state().equals("ACCEPTED") || item.invitationId()==null || !"ACCEPTED".equals(jdbc.queryForObject("SELECT state FROM internship_invitations WHERE id=? FOR UPDATE",String.class,item.invitationId()))) throw failure(409,"STATE_CONFLICT");
            var consent=consents.lockAndCheckForShare(vn.utbridora.auth.contracts.ActorContext.fromAuthenticatedWorkflow(auth,c.schoolOrgId()),new GrantCheck(request.consentId(),request.expectedConsentVersion(),null,item.studentUserId(),c.schoolOrgId(),c.termId(),c.enterpriseOrgId(),"INTERNSHIP_SELECTION",request.fieldWhitelist()));
            if(!consent.valid()) throw failure(409,"CONSENT_INVALID");
            if(request.expiresAt()==null || !Instant.now().isBefore(request.expiresAt()) || request.expiresAt().isAfter(consent.validUntil())) throw failure(422,"CONSENT_EXPIRY_INVALID");
            if(!policies.current(c.schoolOrgId(),c.termId()).allowedFields().containsAll(request.fieldWhitelist())) throw failure(422,"CONSENT_FIELDS_NOT_ALLOWED");
            if(!Instant.now().isBefore(item.reservationUntil())) throw failure(409,"RESERVATION_EXPIRED");
            String shareId=UUID.randomUUID().toString();var result=new Share(shareId,1,request.consentId(),request.expectedConsentVersion(),item.studentUserId(),c.schoolOrgId(),c.termId(),c.enterpriseOrgId(),id,"INTERNSHIP_SELECTION",request.fieldWhitelist(),Instant.now(),request.expiresAt(),null,actor(auth));
            jdbc.update("INSERT INTO profile_shares VALUES(?,?,?,?)",shareId,id,result.consentId(),JSON.writeValueAsString(result));jdbc.update("UPDATE proposal_items SET state='SHARED',version=version+1,share_id=? WHERE id=?",shareId,id);
            audit.workflow(actor(auth),"PROFILE_SHARED",shareId,c.schoolOrgId(),Map.of("consentId",result.consentId(),"consentVersion",result.consentVersion(),"shareVersion",1,"fieldCodes",result.fieldWhitelist(),"expiresAt",result.expiresAt().toString()));return result;
        },Share::shareId);
    }
    @Transactional public Share revoke(Authentication auth,String id,String key,Revoke request) {
        var before=stored(uuid(id));scope.require(auth,before.schoolOrgId(),before.termId(),"student_profile:share:school:manage");version(request.expectedVersion());reason(request.reasonCode());
        return ledger.execute(new OperationLedger.Command(actor(auth),before.schoolOrgId(),"PROFILE_SHARE_REVOKE",key,Map.of("id",id,"request",request),200),Share.class,()->{
            var c=stored(id);if(c.shareVersion()!=request.expectedVersion() || c.revokedAt()!=null) throw failure(409,"VERSION_CONFLICT");
            var result=new Share(c.shareId(),c.shareVersion()+1,c.consentId(),c.consentVersion(),c.studentUserId(),c.schoolOrgId(),c.termId(),c.recipientOrgId(),c.proposalItemId(),c.purpose(),c.fieldWhitelist(),c.validFrom(),c.expiresAt(),Instant.now(),c.grantedByUserId());
            jdbc.update("UPDATE profile_shares SET data_json=? WHERE id=?",JSON.writeValueAsString(result),id);audit.workflow(actor(auth),"PROFILE_SHARE_REVOKED",id,c.schoolOrgId(),Map.of("shareVersion",result.shareVersion(),"reasonCode",request.reasonCode()));return result;
        },Share::shareId);
    }
    @Transactional public ProfileProjection read(Authentication auth,String student,String term,String shareId,List<String> requested,String purpose) {
        uuid(student);uuid(term);fields(requested,false);List<String> allowed;Long consentVersion=null,shareVersion=null;
        String school=jdbc.queryForList("SELECT school_org_id FROM student_term_eligibilities WHERE user_id=? AND term_id=? FOR SHARE",String.class,student,term).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
        if(purpose.equals("SCHOOL_REVIEW")) {
            if(shareId!=null) throw failure(400,"SCHEMA_VALIDATION");scope.require(auth,school,term,"student_profile:school:read");
            allowed=requested.stream().filter(scope.profileFields(auth,school,term)::contains).toList();
        } else if(purpose.equals("INTERNSHIP_SELECTION")) {
            var s=stored(uuid(shareId));
            if(!s.studentUserId().equals(student) || !s.termId().equals(term) || !core.holdOrganization(auth,s.recipientOrgId(),Role.ENTERPRISE,"student_profile:shared:read")) throw failure(404,"RESOURCE_NOT_FOUND");
            if(s.revokedAt()!=null || Instant.now().isBefore(s.validFrom()) || !Instant.now().isBefore(s.expiresAt())) throw failure(403,"ACCESS_DENIED");
            allowed=requested.stream().filter(s.fieldWhitelist()::contains).filter(policies.current(school,term).allowedFields()::contains).toList();
            if(allowed.isEmpty()) throw failure(403,"ACCESS_DENIED");
            var check=consents.lockAndCheckForShare(vn.utbridora.auth.contracts.ActorContext.fromAuthenticatedWorkflow(auth,s.recipientOrgId()),new GrantCheck(s.consentId(),s.consentVersion(),s.shareId(),student,school,term,s.recipientOrgId(),purpose,allowed));
            if(!check.valid()) throw failure(403,"ACCESS_DENIED");consentVersion=s.consentVersion();shareVersion=s.shareVersion();
        } else throw failure(400,"SCHEMA_VALIDATION");
        if(allowed.isEmpty()) throw failure(403,"ACCESS_DENIED");
        var profile=profiles.lockByUser(student).orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));var per=profiles.findTerm(profile.id(),term).orElse(null);
        var data=new LinkedHashMap<String,Object>();
        for(String field:allowed) switch(field) {
            case "majorCode"->data.put(field,per==null?null:per.majorCode());
            case "skills"->{var values=new ArrayList<Object>();profiles.skills(profile.id()).forEach(s->values.add(Map.of("kind","CANONICAL","skill",s)));profiles.customSkills(profile.id()).forEach(s->values.add(Map.of("kind","CUSTOM","skill",s)));data.put(field,values);}
            case "projects"->data.put(field,profiles.projects(profile.id()));
            case "certificates"->data.put(field,profiles.certificates(profile.id()));
            case "phone"->data.put(field,profile.phone());
            case "email","displayName"->data.put(field,jdbc.queryForObject("SELECT "+(field.equals("email")?"email":"display_name")+" FROM users WHERE id=?",String.class,student));
            case "studentCode"->data.put(field,jdbc.queryForObject("SELECT student_code FROM student_rosters WHERE user_id=? AND school_org_id=?",String.class,student,school));
            case "cv"->{if(profile.cvFileId()!=null) throw failure(503,"DEPENDENCY_UNAVAILABLE");data.put(field,null);}
            case "evidence"->{if(profiles.projects(profile.id()).stream().anyMatch(p->!p.evidenceFileIds().isEmpty())) throw failure(503,"DEPENDENCY_UNAVAILABLE");data.put(field,List.of());}
            default->throw failure(422,"PROFILE_FIELD_UNSUPPORTED");
        }
        var metadata=new HashMap<String,Object>();metadata.put("accessPurpose",purpose);metadata.put("fieldCodes",allowed);if(consentVersion!=null) metadata.put("consentVersion",consentVersion);if(shareVersion!=null) metadata.put("shareVersion",shareVersion);
        audit.workflow(actor(auth),"STUDENT_PROFILE_ACCESSED",profile.id(),school,metadata);return new ProfileProjection(student,profile.version(),allowed,data);
    }
    private Share stored(String id) {return jdbc.query("SELECT data_json FROM profile_shares WHERE id=? FOR SHARE",(r,n)->JSON.readValue(r.getString(1),Share.class),id).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));}
}
