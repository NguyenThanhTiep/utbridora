package vn.utbridora.auth.modules.cooperation.application;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.*;
import vn.utbridora.auth.modules.cooperation.persistence.DemandRepository;
import vn.utbridora.auth.modules.cooperation.persistence.DemandRepository.StoredDemand;
import vn.utbridora.auth.modules.cooperation.integration.DemandReferencePort;
import vn.utbridora.auth.operations.OperationLedger;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@Service
public class CooperationService {
    public record CooperationDto(String cooperationId,String demandId,String schoolOrgId,String enterpriseOrgId,String termId,String state,int submissionNo,long version,Instant submittedAt,String decisionRef) {}
    public record Submit(String demandId,Long expectedDemandVersion) {}
    public record Resubmit(Long expectedVersion,Long expectedDemandVersion) {}
    public record Decision(Long expectedVersion,String decision,String reasonCode,String decisionRef,List<String> missingFields) {}
    public record Reopen(Long expectedVersion,String reasonCode) {}
    public record Submitted(DemandDto demand,CooperationDto cooperation) {}
    public record ReviewDetail(DemandDto demand,CooperationDto cooperation,Decision feedback) {}
    public record SubmissionHistory(int submissionNo,Instant submittedAt,long demandVersion,long criteriaVersion,int quantitySnapshot,String decision,Instant decidedAt,String reasonCode,String decisionRef) {}
    public record Publication(Instant publishedAt,Instant closedAt,Long expectedVersion) {}
    public record PublicationView(String cooperationId,Instant publishedAt,Instant closedAt,long version) {}
    public record OpportunityView(String opportunityId,String cooperationId,String demandId,String schoolOrgId,String enterpriseOrgId,String termId,String enterpriseName,String title,List<String> majorCodes,DateRange dateRange,String benefits,String location,Instant publishedAt,Instant closedAt,long version) {}
    private final vn.utbridora.auth.student.StudentTermLinkService studentTerms;
    private final JdbcTemplate jdbc;private final DemandRepository demands;private final DemandReferencePort refs;
    private final AuthorizationPolicy core;private final SchoolDelegationAccess scope;private final OperationLedger ledger;private final AuditService audit;
    public CooperationService(JdbcTemplate jdbc,DemandRepository demands,DemandReferencePort refs,AuthorizationPolicy core,SchoolDelegationAccess scope,OperationLedger ledger,AuditService audit,vn.utbridora.auth.student.StudentTermLinkService studentTerms) {
        this.jdbc=jdbc;this.demands=demands;this.refs=refs;this.core=core;this.scope=scope;this.ledger=ledger;this.audit=audit;
        this.studentTerms=studentTerms;
    }
    @Transactional public PublicationView publication(Authentication auth,String school,String id) {
        var c=owned(id,uuid(school),true);scope.require(auth,school,c.termId(),"opportunity:school:publish");
        return publicationOf(c);
    }
    @Transactional public OpportunityView publish(Authentication auth,String school,String id,String key,Publication request) {
        var before=owned(id,uuid(school),true);scope.require(auth,school,before.termId(),"opportunity:school:publish");
        version(request.expectedVersion());period(request.publishedAt(),request.closedAt());
        return ledger.execute(new OperationLedger.Command(actor(auth),school,"OPPORTUNITY_PUBLISH",key,Map.of("id",id,"request",request),200),OpportunityView.class,()->{
            var c=read(id);
            if(c.version()!=request.expectedVersion()) throw failure(409,"VERSION_CONFLICT");
            if(!"APPROVED".equals(c.state())) throw failure(409,"STATE_CONFLICT");
            jdbc.update("UPDATE cooperations SET published_at=?,closed_at=?,version=version+1 WHERE id=?",utc(request.publishedAt()),utc(request.closedAt()),id);
            var result=opportunityProjection("c.id=?",id).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
            audit.workflow(actor(auth),"OPPORTUNITY_PUBLICATION_CHANGED",id,school,Map.of("publishedAt",request.publishedAt().toString(),"closedAt",request.closedAt().toString(),"version",result.version(),"operation",request.closedAt().isAfter(Instant.now())?"PUBLISH":"CLOSE"));
            return result;
        },OpportunityView::cooperationId);
    }
    @Transactional public Page<OpportunityView> opportunities(Authentication auth,String term,String query,String major,String enterprise,int page,int size) {
        page(page,size);uuid(term);if(query!=null) text(query,150);
        if(major!=null) text(major,64);if(enterprise!=null) uuid(enterprise);
        String school=jdbc.queryForList("SELECT school_org_id FROM terms WHERE id=?",String.class,term).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
        studentScope(auth,school,term);
        var visible=opportunityRows("c.school_org_id=? AND c.term_id=? AND (? IS NULL OR c.enterprise_org_id=?)",school,term,enterprise,enterprise).stream()
            .filter(v->major==null || v.majorCodes().contains(major))
            .filter(v->query==null || (v.title()+" "+v.enterpriseName()).toLowerCase(Locale.ROOT).contains(query.strip().toLowerCase(Locale.ROOT))).toList();
        int from=Math.min(page*size,visible.size());
        return new Page<>(visible.subList(from,Math.min(from+size,visible.size())),page,size,visible.size(),(visible.size()+size-1)/size);
    }
    @Transactional public OpportunityView opportunity(Authentication auth,String id) {
        var result=opportunityRows("c.id=?",uuid(id)).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
        studentScope(auth,result.schoolOrgId(),result.termId());return result;
    }
    private void studentScope(Authentication auth,String school,String term) {
        if(!core.holdOrganization(auth,school,Role.STUDENT,"student_profile:self:read") || !studentTerms.linked(actor(auth),school,term)) throw failure(404,"RESOURCE_NOT_FOUND");
    }
    private PublicationView publicationOf(CooperationDto c) {
        return jdbc.queryForObject("SELECT published_at,closed_at FROM cooperations WHERE id=?",(r,n)->new PublicationView(c.cooperationId(),instant(r,"published_at"),instant(r,"closed_at"),c.version()),c.cooperationId());
    }
    private List<OpportunityView> opportunityRows(String filter,Object... args) {
        return opportunityProjection("c.published_at<=UTC_TIMESTAMP(6) AND UTC_TIMESTAMP(6)<c.closed_at AND "+filter,args);
    }
    private List<OpportunityView> opportunityProjection(String filter,Object... args) {
        return jdbc.query("SELECT c.*,d.data_json,o.name AS enterprise_name FROM cooperations c JOIN demands d ON d.id=c.demand_id JOIN organizations o ON o.id=c.enterprise_org_id WHERE c.state='APPROVED' AND d.soft_deleted_at IS NULL AND "+filter+" ORDER BY c.published_at DESC,c.id",(r,n)->{
            var d=JSON.readValue(r.getString("data_json"),DemandInput.class);
            return new OpportunityView(r.getString("id"),r.getString("id"),r.getString("demand_id"),r.getString("school_org_id"),r.getString("enterprise_org_id"),r.getString("term_id"),r.getString("enterprise_name"),d.title(),d.required().majorCodes(),d.required().dateRange(),d.benefits(),d.location(),instant(r,"published_at"),instant(r,"closed_at"),r.getLong("version"));
        },args);
    }
    @Transactional public CooperationDto create(Authentication auth,String org,String key,Submit request) {
        enterprise(auth,org);uuid(request.demandId());version(request.expectedDemandVersion());
        return ledger.execute(new OperationLedger.Command(actor(auth),org,"COOPERATION_CREATE",key,request,201),CooperationDto.class,()->createDraft(auth,org,request),CooperationDto::cooperationId);
    }
    private CooperationDto createDraft(Authentication auth,String org,Submit request) {
        StoredDemand d=demands.findLocked(request.demandId(),org).orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
        if(d.version()!=request.expectedDemandVersion()) throw failure(409,"VERSION_CONFLICT");
        if(d.deletedAt()!=null || d.cooperationId()!=null) throw failure(409,"COOPERATION_ALREADY_EXISTS");
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO cooperations(id,demand_id,school_org_id,enterprise_org_id,term_id,state,created_at) VALUES(?,?,?,?,?,'DRAFT',UTC_TIMESTAMP(6))",id,d.id(),d.data().schoolOrgId(),org,d.data().termId());
        jdbc.update("UPDATE demands SET cooperation_id=? WHERE id=?",id,d.id());
        audit.workflow(actor(auth),"COOPERATION_DRAFT_SAVED",id,d.data().schoolOrgId(),Map.of("operation","CREATE","version",1));
        return read(id);
    }
    @Transactional public Submitted submitWithCooperation(Authentication auth,String org,String key,Submit request) {
        enterprise(auth,org);uuid(request.demandId());version(request.expectedDemandVersion());
        return ledger.execute(new OperationLedger.Command(actor(auth),org,"COOPERATION_ATOMIC_SUBMIT",key,request,200),Submitted.class,()->{
            var coop=createDraft(auth,org,request);return submitAuthorized(auth,coop,request.expectedDemandVersion());
        },r->r.cooperation().cooperationId());
    }
    @Transactional public CooperationDto submit(Authentication auth,String org,String id,String key,Resubmit request) {
        enterprise(auth,org);var before=owned(id,org,false);version(request.expectedVersion());version(request.expectedDemandVersion());
        return ledger.execute(new OperationLedger.Command(actor(auth),org,"COOPERATION_SUBMIT",key,Map.of("id",id,"request",request),200),CooperationDto.class,()->{
            var current=read(id);if(current.version()!=request.expectedVersion()) throw failure(409,"VERSION_CONFLICT");
            return submitAuthorized(auth,current,request.expectedDemandVersion()).cooperation();
        },CooperationDto::cooperationId);
    }
    private Submitted submitAuthorized(Authentication auth,CooperationDto c,long expectedDemand) {
        if(!Set.of("DRAFT","NEEDS_CHANGES").contains(c.state())) throw failure(409,"STATE_CONFLICT");
        var d=demands.findLocked(c.demandId(),c.enterpriseOrgId()).orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
        if(d.version()!=expectedDemand) throw failure(409,"VERSION_CONFLICT");
        if(d.deletedAt()!=null || d.data().deadline().isBefore(Instant.now())) throw failure(422,"INVALID_CRITERIA");
        refs.validate(c.enterpriseOrgId(),d.data());
        var verified=jdbc.queryForList("SELECT id FROM enterprise_verifications WHERE enterprise_org_id=? AND school_org_id=? AND state='APPROVED' FOR SHARE",String.class,c.enterpriseOrgId(),c.schoolOrgId());
        if(verified.isEmpty()) throw failure(422,"SCHOOL_VERIFICATION_REQUIRED");
        var ready=ready(auth,d,"READY");Instant now=Instant.now();
        jdbc.update("UPDATE cooperations SET state='SUBMITTED',submission_no=submission_no+1,version=version+1,submitted_at=?,decision_ref=NULL WHERE id=?",utc(now),c.cooperationId());
        var result=read(c.cooperationId());
        history(auth,result,"SUBMIT",Map.of("cooperation",result,"demand",ready.dto()));
        audit.workflow(actor(auth),"COOPERATION_SUBMITTED",c.cooperationId(),c.schoolOrgId(),Map.of("submissionNo",result.submissionNo(),"demandVersion",ready.version(),"version",result.version()));
        return new Submitted(ready.dto(),result);
    }
    @Transactional public CooperationDto decide(Authentication auth,String school,String id,String key,Decision request) {
        var before=owned(id,school,true);scope.require(auth,school,before.termId(),"cooperation:school:review");version(request.expectedVersion());text(request.decisionRef(),120);
        if(request.decision()==null || !Set.of("APPROVE","REJECT","REQUEST_INFO").contains(request.decision()) || request.missingFields()==null) throw failure(400,"SCHEMA_VALIDATION");
        if(!request.decision().equals("APPROVE")) reason(request.reasonCode());
        if(request.decision().equals("REQUEST_INFO") && request.missingFields().isEmpty()) throw failure(422,"MISSING_FIELDS_REQUIRED");
        return ledger.execute(new OperationLedger.Command(actor(auth),school,"COOPERATION_DECIDE",key,Map.of("id",id,"request",request),200),CooperationDto.class,()->{
            var c=read(id);if(c.version()!=request.expectedVersion()) throw failure(409,"VERSION_CONFLICT");
            if(!c.state().equals("SUBMITTED")) throw failure(409,"STATE_CONFLICT");
            if(request.decision().equals("APPROVE") && jdbc.queryForList("SELECT v.id FROM enterprise_verifications v JOIN organizations o ON o.id=v.enterprise_org_id WHERE v.enterprise_org_id=? AND v.school_org_id=? AND v.state='APPROVED' AND o.status='VERIFIED' FOR SHARE",String.class,c.enterpriseOrgId(),school).isEmpty()) throw failure(422,"SCHOOL_VERIFICATION_REQUIRED");
            String state=switch(request.decision()) {case "APPROVE"->"APPROVED";case "REJECT"->"REJECTED";default->"NEEDS_CHANGES";};
            jdbc.update("UPDATE cooperations SET state=?,version=version+1,decision_ref=? WHERE id=?",state,request.decisionRef(),id);
            if(state.equals("NEEDS_CHANGES")) ready(auth,demands.findLocked(c.demandId(),c.enterpriseOrgId()).orElseThrow(),"DRAFT");
            var result=read(id);history(auth,result,request.decision(),request);
            var meta=new HashMap<String,Object>();meta.put("decision",request.decision());meta.put("submissionNo",result.submissionNo());meta.put("version",result.version());if(request.reasonCode()!=null) meta.put("reasonCode",request.reasonCode());
            audit.workflow(actor(auth),"COOPERATION_REVIEWED",id,school,meta);return result;
        },CooperationDto::cooperationId);
    }
    @Transactional public CooperationDto reopen(Authentication auth,String org,String id,String key,Reopen request) {
        enterprise(auth,org);owned(id,org,false);version(request.expectedVersion());reason(request.reasonCode());
        return ledger.execute(new OperationLedger.Command(actor(auth),org,"COOPERATION_REOPEN",key,Map.of("id",id,"request",request),200),CooperationDto.class,()->{
            var c=read(id);if(c.version()!=request.expectedVersion()) throw failure(409,"VERSION_CONFLICT");
            if(!c.state().equals("REJECTED")) throw failure(409,"STATE_CONFLICT");
            ready(auth,demands.findLocked(c.demandId(),org).orElseThrow(),"DRAFT");
            jdbc.update("UPDATE cooperations SET state='DRAFT',version=version+1 WHERE id=?",id);
            var result=read(id);history(auth,result,"REOPEN",request);audit.workflow(actor(auth),"COOPERATION_DRAFT_SAVED",id,c.schoolOrgId(),Map.of("operation","REOPEN","version",result.version()));return result;
        },CooperationDto::cooperationId);
    }
    @Transactional(readOnly=true) public Page<CooperationDto> list(Authentication auth,String org,boolean school,String term,String state,int page,int size) {
        uuid(org);page(page,size);if(term!=null) uuid(term);
        if(school) scope.require(auth,org,term,"cooperation:school:review");else enterprise(auth,org);
        if(state!=null && !Set.of("DRAFT","SUBMITTED","NEEDS_CHANGES","APPROVED","REJECTED").contains(state)) throw failure(400,"SCHEMA_VALIDATION");
        String where=" WHERE "+(school?"school_org_id":"enterprise_org_id")+"=? AND (? IS NULL OR term_id=?) AND (? IS NULL OR state=?)";
        Object[] args={org,term,term,state,state};
        var rows=jdbc.queryForList("SELECT id FROM cooperations"+where+" ORDER BY created_at DESC,id ASC",String.class,args);
        var visible=rows.stream().map(this::read).filter(c->{if(!school)return true;try {scope.require(auth,org,c.termId(),"cooperation:school:review");return true;}catch(vn.utbridora.auth.operations.OperationFailure denied){return false;}}).toList();
        int from=Math.min(page*size,visible.size());return new Page<>(visible.subList(from,Math.min(from+size,visible.size())),page,size,visible.size(),(visible.size()+size-1)/size);
    }
    @Transactional public Page<SubmissionHistory> history(Authentication auth,String school,String id,int page,int size) {
        page(page,size);var c=owned(id,school,true);scope.require(auth,school,c.termId(),"cooperation:school:review");
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM cooperation_history WHERE cooperation_id=? AND operation='SUBMIT'",Long.class,id);
        var rows=jdbc.query("SELECT data_json,submission_no,created_at FROM cooperation_history WHERE cooperation_id=? AND operation='SUBMIT' ORDER BY submission_no DESC LIMIT ? OFFSET ?",(r,n)->{
            var data=JSON.readTree(r.getString(1));int submission=r.getInt(2);
            var decisions=jdbc.query("SELECT data_json,created_at FROM cooperation_history WHERE cooperation_id=? AND submission_no=? AND operation IN ('APPROVE','REJECT','REQUEST_INFO') ORDER BY created_at DESC LIMIT 1",(d,i)->new Object[]{JSON.readValue(d.getString(1),Decision.class),instant(d,"created_at")},id,submission);
            Decision decision=decisions.isEmpty()?null:(Decision)decisions.getFirst()[0];
            return new SubmissionHistory(submission,instant(r,"created_at"),data.get("demand").get("version").asLong(),data.get("demand").get("criteriaVersion").asLong(),data.get("demand").get("data").get("quantity").asInt(),decision==null?null:decision.decision(),decision==null?null:(Instant)decisions.getFirst()[1],decision==null?null:decision.reasonCode(),decision==null?null:decision.decisionRef());
        },id,size,page*size);
        return new Page<>(rows,page,size,count,(int)((count+size-1)/size));
    }
    @Transactional(readOnly=true) public ReviewDetail reviewDetail(Authentication auth,String org,String id,boolean schoolSide) {
        var c=owned(id,org,schoolSide);if(schoolSide) scope.require(auth,org,c.termId(),"cooperation:school:review");else enterprise(auth,org);
        var feedback=jdbc.query("SELECT data_json FROM cooperation_history WHERE cooperation_id=? AND operation IN ('APPROVE','REJECT','REQUEST_INFO') ORDER BY created_at DESC,id ASC LIMIT 1",(r,n)->JSON.readValue(r.getString(1),Decision.class),id).stream().findFirst().orElse(null);
        return new ReviewDetail(demands.findForRead(c.demandId(),c.enterpriseOrgId()).orElseThrow().dto(),c,feedback);
    }
    @Transactional(propagation=Propagation.MANDATORY) public CooperationDto read(String id) {
        // Parallel list/detail readers must not take Cooperation -> delegation locks while
        // another reader holds delegation and walks the remaining Cooperation rows.
        String lock=org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()?"":" FOR UPDATE";
        return jdbc.query("SELECT * FROM cooperations WHERE id=?"+lock,(r,n)->new CooperationDto(r.getString("id"),r.getString("demand_id"),r.getString("school_org_id"),r.getString("enterprise_org_id"),r.getString("term_id"),r.getString("state"),r.getInt("submission_no"),r.getLong("version"),r.getTimestamp("submitted_at")==null?null:instant(r,"submitted_at"),r.getString("decision_ref")),uuid(id)).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
    }
    private CooperationDto owned(String id,String org,boolean school) {var c=read(id);if(!org.equals(school?c.schoolOrgId():c.enterpriseOrgId())) throw failure(404,"RESOURCE_NOT_FOUND");return c;}
    private void enterprise(Authentication auth,String org) {if(!core.holdOrganization(auth,uuid(org),Role.ENTERPRISE,"enterprise:cooperation:submit")) throw failure(403,"ACCESS_DENIED");}
    private StoredDemand ready(Authentication auth,StoredDemand d,String state) {
        long next=d.version()+1;version(next);
        jdbc.update("UPDATE demands SET state=?,version=?,updated_at=UTC_TIMESTAMP(6) WHERE id=?",state,next,d.id());
        var updated=demands.findLocked(d.id(),d.enterpriseOrgId()).orElseThrow();
        var snapshot=new DemandCriteriaSnapshot(d.id(),d.data().schoolOrgId(),d.enterpriseOrgId(),d.data().termId(),d.data().quantity(),next,d.criteriaVersion(),"VALID",d.data().required(),d.data().preferred(),d.criteriaHash(),d.data().academicCatalogVersion(),d.data().taxonomyVersion(),d.cooperationId(),null,Instant.now());
        demands.addRevision(updated,snapshot,actor(auth),"UPDATE");audit.demandChanged(actor(auth),d.id(),d.enterpriseOrgId(),"UPDATE",next,d.criteriaVersion());return updated;
    }
    private void history(Authentication auth,CooperationDto c,String operation,Object value) {
        jdbc.update("INSERT INTO cooperation_history VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(6))",UUID.randomUUID().toString(),c.cooperationId(),c.submissionNo(),actor(auth),operation,JSON.writeValueAsString(value));
    }
}
