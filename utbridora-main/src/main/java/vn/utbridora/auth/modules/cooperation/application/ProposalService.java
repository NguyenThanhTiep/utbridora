package vn.utbridora.auth.modules.cooperation.application;

import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.*;
import vn.utbridora.auth.modules.cooperation.persistence.DemandRepository;
import vn.utbridora.auth.modules.cooperation.port.InternshipInvitationPort;
import vn.utbridora.auth.modules.matching.application.MatchingService;
import vn.utbridora.auth.modules.matching.domain.StrictClassifier.MatchResult;
import vn.utbridora.auth.modules.student.port.*;
import vn.utbridora.auth.modules.student.port.StudentProfileSnapshotPort.DecisionProfileRef;
import vn.utbridora.auth.operations.OperationLedger;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@Service
public class ProposalService implements InternshipInvitationPort {
    public record Nominate(List<String> studentUserIds,Long expectedDemandVersion,Long expectedCriteriaVersion,Long expectedCooperationVersion,Long expectedBatchVersion) {}
    public record Item(String proposalItemId,String batchId,String studentUserId,String cooperationId,String state,long version,Instant reservationUntil,String invitationId,String shareId,String placementId) {}
    public record Reserved(String proposalItemId,String studentUserId,String state,Instant reservationUntil) {}
    public record Capacity(String cooperationId,String demandId,int quantity,int placedCount,int activeReservations,int openForNomination,int declinedCount) {}
    public record NominationResult(String batchId,long batchVersion,List<Reserved> items,int quantity,int placedCount,int activeReservations,int openForNomination) {}
    public record Invite(Long expectedVersion,Instant expiresAt) {}
    public record InvitationDecision(String decision,Long expectedVersion,String reasonCode,String reasonText) {}
    public record Withdrawal(Long expectedVersion,String reasonCode,String reasonText) {}
    private final JdbcTemplate jdbc;private final MatchingService matching;private final DemandRepository demands;private final CooperationService cooperations;
    private final StudentProfileSnapshotPort profiles;private final StudentEligibilityPort eligibility;private final SchoolDelegationAccess scope;private final AuthorizationPolicy core;private final OperationLedger ledger;private final AuditService audit;
    private final vn.utbridora.auth.modules.cooperation.integration.DemandReferencePort references;
    public ProposalService(JdbcTemplate jdbc,MatchingService matching,DemandRepository demands,CooperationService cooperations,StudentProfileSnapshotPort profiles,StudentEligibilityPort eligibility,SchoolDelegationAccess scope,AuthorizationPolicy core,OperationLedger ledger,AuditService audit,vn.utbridora.auth.modules.cooperation.integration.DemandReferencePort references) {
        this.jdbc=jdbc;this.matching=matching;this.demands=demands;this.cooperations=cooperations;this.profiles=profiles;this.eligibility=eligibility;this.scope=scope;this.core=core;this.ledger=ledger;this.audit=audit;
        this.references=references;
    }
    @Transactional public NominationResult nominate(Authentication auth,String runId,String key,Nominate request) {
        var stored=matching.read(auth,runId,"matching:school:nominate");scope.require(auth,stored.schoolOrgId(),stored.termId(),"t5:proposal:manage");
        if(request.studentUserIds()==null || request.studentUserIds().isEmpty() || request.studentUserIds().size()>5000 || new HashSet<>(request.studentUserIds()).size()!=request.studentUserIds().size()) throw failure(400,"SCHEMA_VALIDATION");
        request.studentUserIds().forEach(vn.utbridora.auth.operations.WorkflowValues::uuid);version(request.expectedDemandVersion());version(request.expectedCriteriaVersion());version(request.expectedCooperationVersion());
        return ledger.execute(new OperationLedger.Command(actor(auth),stored.schoolOrgId(),"PROPOSAL_NOMINATE",key,Map.of("runId",runId,"request",request),201),NominationResult.class,()->{
            var run=stored.run();var d=demands.findLocked(run.demandId(),stored.enterpriseOrgId()).orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
            if(d.version()!=request.expectedDemandVersion() || d.criteriaVersion()!=request.expectedCriteriaVersion() || run.criteriaVersion()!=d.criteriaVersion() || run.demandVersion()!=d.version() || !run.criteriaHash().equals(d.criteriaHash())) throw failure(409,"MATCH_RUN_STALE");
            references.validate(stored.enterpriseOrgId(),d.data());
            if(d.cooperationId()==null) throw failure(409,"STATE_CONFLICT");var c=cooperations.read(d.cooperationId());
            if(!c.state().equals("APPROVED") || c.version()!=request.expectedCooperationVersion()) throw failure(409,"STATE_CONFLICT");
            var refs=new ArrayList<DecisionProfileRef>();
            for(String user:request.studentUserIds().stream().sorted().toList()) {
                var result=jdbc.query("SELECT data_json FROM match_results WHERE run_id=? AND student_user_id=? AND overall_status='MATCH'",(r,n)->JSON.readValue(r.getString(1),MatchResult.class),runId,user).stream().findFirst().orElseThrow(()->failure(422,"CANDIDATE_NOT_MATCH"));
                refs.add(new DecisionProfileRef(user,result.profileSnapshotId(),result.profileVersion(),result.relevantHash()));eligibility.assertEligibleForNomination(auth,user,c.schoolOrgId(),c.termId());
            }
            profiles.assertCurrentForDecision(auth,refs);
            var batch=jdbc.queryForList("SELECT id FROM proposal_batches WHERE cooperation_id=? FOR UPDATE",String.class,c.cooperationId());String batchId;long batchVersion;
            if(batch.isEmpty()) {
                if(request.expectedBatchVersion()!=null) throw failure(409,"VERSION_CONFLICT");batchId=UUID.randomUUID().toString();batchVersion=1;
                jdbc.update("INSERT INTO proposal_batches VALUES(?,?,1)",batchId,c.cooperationId());
            } else {
                batchId=batch.getFirst();if(request.expectedBatchVersion()==null) throw failure(409,"PROPOSAL_BATCH_EXISTS");
                batchVersion=jdbc.queryForObject("SELECT version FROM proposal_batches WHERE id=?",Long.class,batchId);
                if(batchVersion!=request.expectedBatchVersion()) throw failure(409,"VERSION_CONFLICT");batchVersion++;jdbc.update("UPDATE proposal_batches SET version=? WHERE id=?",batchVersion,batchId);
            }
            Capacity capacity=capacityOf(c,d.data().quantity());if(capacity.openForNomination()<refs.size()) throw failure(409,"CAPACITY_EXCEEDED");
            Instant until=earlier(Instant.now().plusSeconds(86400),d.data().deadline());if(!Instant.now().isBefore(until)) throw failure(409,"RESERVATION_EXPIRED");
            var items=new ArrayList<Reserved>();
            for(var ref:refs) {
                if(jdbc.queryForObject("SELECT COUNT(*) FROM proposal_items WHERE batch_id=? AND student_user_id=?",Integer.class,batchId,ref.studentUserId())>0) throw failure(409,"PROPOSAL_ALREADY_EXISTS");
                String id=UUID.randomUUID().toString();jdbc.update("INSERT INTO proposal_items(id,batch_id,student_user_id,state,version,reservation_until,profile_snapshot_id,match_run_id) VALUES(?,?,?,'RESERVED',1,?,?,?)",id,batchId,ref.studentUserId(),utc(until),ref.profileSnapshotId(),runId);
                audit.workflow(actor(auth),"PROPOSAL_ITEM_RESERVED",id,c.schoolOrgId(),Map.of("batchId",batchId,"reservationUntil",until.toString(),"version",1));items.add(new Reserved(id,ref.studentUserId(),"RESERVED",until));
            }
            var after=capacityOf(c,d.data().quantity());return new NominationResult(batchId,batchVersion,items,after.quantity(),after.placedCount(),after.activeReservations(),after.openForNomination());
        },NominationResult::batchId);
    }
    @Transactional public Capacity capacity(Authentication auth,String coopId) {var c=cooperations.read(coopId);scope.require(auth,c.schoolOrgId(),c.termId(),"t5:proposal:manage");var d=demands.findLocked(c.demandId(),c.enterpriseOrgId()).orElseThrow();return capacityOf(c,d.data().quantity());}
    private Capacity capacityOf(CooperationService.CooperationDto c,int quantity) {
        int placed=jdbc.queryForObject("SELECT COUNT(*) FROM proposal_items i JOIN proposal_batches b ON b.id=i.batch_id WHERE b.cooperation_id=? AND i.state='PLACED'",Integer.class,c.cooperationId());
        int reserved=jdbc.queryForObject("SELECT COUNT(*) FROM proposal_items i JOIN proposal_batches b ON b.id=i.batch_id WHERE b.cooperation_id=? AND i.state IN ('RESERVED','INVITED','ACCEPTED','SHARED','ENTERPRISE_ACCEPTED') AND i.reservation_until>UTC_TIMESTAMP(6)",Integer.class,c.cooperationId());
        int declined=jdbc.queryForObject("SELECT COUNT(*) FROM proposal_items i JOIN proposal_batches b ON b.id=i.batch_id WHERE b.cooperation_id=? AND i.state='DECLINED'",Integer.class,c.cooperationId());
        return new Capacity(c.cooperationId(),c.demandId(),quantity,placed,reserved,Math.max(0,quantity-placed-reserved),declined);
    }
    @Transactional public Page<Item> list(Authentication auth,String coopId,int page,int size) {
        page(page,size);var c=cooperations.read(coopId);scope.require(auth,c.schoolOrgId(),c.termId(),"t5:proposal:manage");
        var ids=jdbc.queryForList("SELECT i.id FROM proposal_items i JOIN proposal_batches b ON b.id=i.batch_id WHERE b.cooperation_id=? ORDER BY i.id",String.class,coopId);int from=Math.min(page*size,ids.size());
        return new Page<>(ids.subList(from,Math.min(from+size,ids.size())).stream().map(this::item).toList(),page,size,ids.size(),(ids.size()+size-1)/size);
    }
    @Transactional public InternshipInvitation invite(Authentication auth,String id,String key,Invite request) {
        version(request.expectedVersion());var before=item(uuid(id));var c=cooperations.read(before.cooperationId());scope.require(auth,c.schoolOrgId(),c.termId(),"t5:proposal:manage");
        return ledger.execute(new OperationLedger.Command(actor(auth),c.schoolOrgId(),"INTERNSHIP_INVITE",key,Map.of("id",id,"request",request),202),InternshipInvitation.class,()->{
            var i=item(id);if(i.version()!=request.expectedVersion()) throw failure(409,"VERSION_CONFLICT");
            if(!i.state().equals("RESERVED") || !Instant.now().isBefore(i.reservationUntil())) throw failure(409,"RESERVATION_EXPIRED");
            var d=demands.findLocked(c.demandId(),c.enterpriseOrgId()).orElseThrow();Instant now=Instant.now();
            Instant termEnd=jdbc.queryForObject("SELECT end_date FROM terms WHERE id=?",java.sql.Date.class,c.termId()).toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1000);
            Instant limit=earlier(d.data().deadline(),termEnd),expires=request.expiresAt()==null?earlier(now.plusSeconds(7*86400),limit):request.expiresAt();
            if(!now.isBefore(expires) || expires.isAfter(limit) || expires.isAfter(now.plusSeconds(30*86400))) throw failure(422,"INVITATION_EXPIRY_INVALID");
            String invitation=UUID.randomUUID().toString();jdbc.update("INSERT INTO internship_invitations(id,proposal_item_id,student_user_id,state,expires_at) VALUES(?,?,?,'PENDING',?)",invitation,id,i.studentUserId(),utc(expires));
            jdbc.update("UPDATE proposal_items SET state='INVITED',version=version+1,invitation_id=? WHERE id=?",invitation,id);
            history(auth,invitation,null,"PENDING",1,null);audit.workflow(actor(auth),"INTERNSHIP_INVITATION_ISSUED",invitation,c.schoolOrgId(),Map.of("proposalItemId",id,"expiresAt",expires.toString(),"version",1));return invitation(invitation);
        },InternshipInvitation::invitationId);
    }
    @Override @Transactional public InternshipInvitation readMine(Authentication auth,String id) {
        var i=invitation(uuid(id));if(!core.isSelf(auth,i.studentUserId()) || !core.holdOrganization(auth,i.schoolOrgId(),Role.STUDENT,"student_profile:self:read")) throw failure(404,"RESOURCE_NOT_FOUND");return i;
    }
    @Transactional public Page<InternshipInvitation> mine(Authentication auth,int page,int size) {
        page(page,size);var ids=jdbc.queryForList("SELECT id FROM internship_invitations WHERE student_user_id=? ORDER BY expires_at DESC,id",String.class,actor(auth));
        var visible=ids.stream().map(id->readMine(auth,id)).toList();int from=Math.min(page*size,visible.size());return new Page<>(visible.subList(from,Math.min(from+size,visible.size())),page,size,visible.size(),(visible.size()+size-1)/size);
    }
    @Transactional public InternshipInvitation decide(Authentication auth,String id,String key,InvitationDecision request) {
        var before=readMine(auth,id);version(request.expectedVersion());if(request.decision()==null || !Set.of("ACCEPT","DECLINE").contains(request.decision()) || request.reasonText()!=null && request.reasonText().length()>1000) throw failure(400,"SCHEMA_VALIDATION");
        if(request.decision().equals("DECLINE")) reason(request.reasonCode());else if(request.reasonCode()!=null || request.reasonText()!=null) throw failure(400,"SCHEMA_VALIDATION");
        return ledger.execute(new OperationLedger.Command(actor(auth),before.schoolOrgId(),"INTERNSHIP_INVITATION_DECIDE",key,Map.of("id",id,"request",request),200),InternshipInvitation.class,()->{
            var i=invitation(id);if(i.version()!=request.expectedVersion()) throw failure(409,"VERSION_CONFLICT");
            if(!i.state().equals("PENDING")) throw failure(409,"INVITATION_STATE_CONFLICT");if(!Instant.now().isBefore(i.expiresAt())) throw failure(409,"INVITATION_EXPIRED");
            String next=request.decision().equals("ACCEPT")?"ACCEPTED":"DECLINED";
            jdbc.update("UPDATE internship_invitations SET state=?,version=version+1,decided_at=UTC_TIMESTAMP(6),decision_reason_code=? WHERE id=?",next,request.reasonCode(),id);
            jdbc.update("UPDATE proposal_items SET state=?,version=version+1 WHERE id=?",next,i.proposalItemId());
            history(auth,id,"PENDING",next,i.version()+1,request.reasonCode());audit.workflow(actor(auth),"INVITATION_RESPONDED",id,i.schoolOrgId(),Map.of("decision",request.decision(),"invitationVersion",i.version()+1));return invitation(id);
        },InternshipInvitation::invitationId);
    }
    @Transactional public InternshipInvitation withdraw(Authentication auth,String id,String key,Withdrawal request) {
        var before=readMine(auth,id);version(request.expectedVersion());reason(request.reasonCode());
        return ledger.execute(new OperationLedger.Command(actor(auth),before.schoolOrgId(),"INTERNSHIP_INVITATION_WITHDRAW",key,Map.of("id",id,"request",request),200),InternshipInvitation.class,()->{
            var i=invitation(id);if(i.version()!=request.expectedVersion()) throw failure(409,"VERSION_CONFLICT");
            if(!i.state().equals("ACCEPTED")) throw failure(409,"INVITATION_STATE_CONFLICT");if(i.placementId()!=null) throw failure(409,"PLACEMENT_ALREADY_EXISTS");
            jdbc.update("UPDATE internship_invitations SET state='WITHDRAWN',version=version+1,decision_reason_code=? WHERE id=?",request.reasonCode(),id);jdbc.update("UPDATE proposal_items SET state='REVOKED',version=version+1 WHERE id=?",i.proposalItemId());
            history(auth,id,"ACCEPTED","WITHDRAWN",i.version()+1,request.reasonCode());audit.workflow(actor(auth),"INTERNSHIP_INVITATION_REVOKED",id,i.schoolOrgId(),Map.of("version",i.version()+1,"reasonCode",request.reasonCode()));return invitation(id);
        },InternshipInvitation::invitationId);
    }
    public Item item(String id) {
        return jdbc.query("SELECT i.*,b.cooperation_id FROM proposal_items i JOIN proposal_batches b ON b.id=i.batch_id WHERE i.id=? FOR UPDATE",(r,n)->new Item(r.getString("id"),r.getString("batch_id"),r.getString("student_user_id"),r.getString("cooperation_id"),r.getString("state"),r.getLong("version"),instant(r,"reservation_until"),r.getString("invitation_id"),r.getString("share_id"),r.getString("placement_id")),id).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
    }
    private InternshipInvitation invitation(String id) {
        return jdbc.query("SELECT v.*,c.school_org_id,c.enterprise_org_id,c.term_id,c.id AS opportunity_id,JSON_UNQUOTE(JSON_EXTRACT(d.data_json,'$.title')) AS title,o.name AS enterprise_name,i.placement_id FROM internship_invitations v JOIN proposal_items i ON i.id=v.proposal_item_id JOIN proposal_batches b ON b.id=i.batch_id JOIN cooperations c ON c.id=b.cooperation_id JOIN demands d ON d.id=c.demand_id JOIN organizations o ON o.id=c.enterprise_org_id WHERE v.id=? FOR UPDATE",(r,n)->new InternshipInvitation(r.getString("id"),r.getString("proposal_item_id"),r.getString("proposal_item_id"),r.getString("student_user_id"),r.getString("school_org_id"),r.getString("enterprise_org_id"),r.getString("term_id"),r.getString("opportunity_id"),r.getString("title"),r.getString("enterprise_name"),r.getString("state"),instant(r,"expires_at"),r.getTimestamp("decided_at")==null?null:instant(r,"decided_at"),r.getString("decision_reason_code"),r.getLong("version"),r.getString("placement_id")),id).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
    }
    private void history(Authentication auth,String id,String from,String to,long version,String reason) {jdbc.update("INSERT INTO internship_invitation_history VALUES(?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",UUID.randomUUID().toString(),id,from,to,version,actor(auth),reason);}
    private static Instant earlier(Instant a,Instant b) {return a.isBefore(b)?a:b;}
}
