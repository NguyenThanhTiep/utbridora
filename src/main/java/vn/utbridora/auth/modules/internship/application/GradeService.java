package vn.utbridora.auth.modules.internship.application;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.modules.internship.api.EvaluationDtos.*;
import vn.utbridora.auth.modules.internship.api.GradeDtos.*;
import vn.utbridora.auth.modules.internship.infrastructure.*;
import vn.utbridora.auth.operations.OperationLedger;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** T3-11: authorized publication and server-side grade visibility. No rubric writer. */
@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class GradeService {
    private final GradeAccess access;private final JdbcEvaluationStore evaluations;
    private final JdbcTemplate jdbc;private final OperationLedger ledger;private final AuditService audit;
    public GradeService(GradeAccess access,JdbcEvaluationStore evaluations,JdbcTemplate jdbc,OperationLedger ledger,AuditService audit){this.access=access;this.evaluations=evaluations;this.jdbc=jdbc;this.ledger=ledger;this.audit=audit;}
    private record Policy(boolean enabled,long version) {}
    private JdbcEvaluationStore.Root root(UUID id,boolean lock){var r=evaluations.find(id,lock);if(r==null)throw failure(404,"RESOURCE_NOT_FOUND");return r;}
    public EvaluationDto publish(Authentication auth,UUID id,String key,PublishEvaluation input){return change(auth,id,key,input,input.expectedVersion(),input.decisionRef(),null);}
    public EvaluationDto revision(Authentication auth,UUID id,String key,CreateEvaluationRevision input){return change(auth,id,key,input,input.expectedVersion(),input.decisionRef(),input.reasonCode());}
    private EvaluationDto change(Authentication auth,UUID id,String key,Object input,long expected,String decision,String reason){
        var initial=root(id,false);var c=access.schoolCase(auth,initial.caseId());
        return ledger.execute(command(auth,c.schoolOrgId(),reason==null?"T3_EVALUATION_PUBLISH":"T3_EVALUATION_REVISION",key,Map.of("evaluationId",id,"request",input),reason==null?200:201),EvaluationDto.class,()->{
            access.holdSchoolMutation(auth,c);
            var current=evaluations.current(root(id,true));
            if(current.version()!=expected)throw failure(409,"VERSION_CONFLICT");
            if(current.state()!=EvaluationState.SUBMITTED && (reason==null || current.state()!=EvaluationState.PUBLISHED))throw failure(409,"STATE_CONFLICT");
            if(reason!=null && current.revisionNo()==Integer.MAX_VALUE)throw failure(409,"VERSION_CONFLICT");
            var now=Instant.now();var next=new EvaluationDto(id,c.caseId(),current.template(),current.scores(),current.total100(),
                reason==null?EvaluationState.PUBLISHED:EvaluationState.DRAFT,current.revisionNo()+(reason==null?0:1),expected+1,reason==null?now:null,reason==null?decision:null);
            if(reason==null){
                evaluations.publish(next,expected,UUID.fromString(actor(auth)),now);
                audit.workflow(actor(auth),"EVALUATION_PUBLISHED",id.toString(),c.schoolOrgId().toString(),Map.of("caseId",c.caseId().toString(),"evaluationVersion",next.version(),"decisionRef",decision));
            }else{
                evaluations.append(next,expected,UUID.fromString(actor(auth)),now);
                jdbc.update("INSERT INTO internship_evaluation_revision_decisions(evaluation_id,evaluation_version,decision_ref,reason_code) VALUES (?,?,?,?)",id.toString(),next.version(),decision,reason);
                audit.workflow(actor(auth),"EVALUATION_REVISION_CREATED",id.toString(),c.schoolOrgId().toString(),Map.of("caseId",c.caseId().toString(),"previousEvaluationId",id.toString(),"revisionNo",next.revisionNo(),"decisionRef",decision));
            }
            return next;
        },v->v.evaluationId().toString());
    }
    public SchoolResultVisibilityDto schoolPolicy(Authentication auth,UUID school,UUID term){
        access.school(auth,school,term,GradeAccess.SCHOOL);access.holdTerm(term,false);return schoolDto(school,term,schoolPolicy(school,term));
    }
    public SchoolResultVisibilityDto setSchool(Authentication auth,UUID school,UUID term,String key,SetVisibility input){
        access.school(auth,school,term,GradeAccess.SCHOOL);
        return ledger.execute(command(auth,school,"T3_SCHOOL_RESULT_VISIBILITY",key,Map.of("schoolOrgId",school,"termId",term,"request",input),200),SchoolResultVisibilityDto.class,()->{
            access.holdTerm(term,true);var old=schoolPolicy(school,term);check(old,input);
            if(old.version()==0)jdbc.update("INSERT INTO internship_school_result_policies VALUES (?,?,?,1)",school.toString(),term.toString(),input.enabled());
            else if(jdbc.update("UPDATE internship_school_result_policies SET enabled=?,version=version+1 WHERE school_org_id=? AND term_id=? AND version=?",input.enabled(),school.toString(),term.toString(),old.version())!=1)throw failure(409,"VERSION_CONFLICT");
            var next=schoolDto(school,term,new Policy(input.enabled(),old.version()+1));
            audit.workflow(actor(auth),"SCHOOL_RESULT_VISIBILITY_CHANGED",term.toString(),school.toString(),Map.of("schoolOrgId",school.toString(),"termId",term.toString(),"enabled",next.enabled(),"version",next.version()));
            return next;
        },v->v.termId().toString());
    }
    public GradeVisibilityDto hrPolicy(Authentication auth,UUID enterprise,UUID term,UUID caseId){
        access.hr(auth,enterprise,term,caseId);access.holdTerm(term,false);return hrDto(enterprise,term,caseId,hrPolicy(enterprise,term,caseId));
    }
    public GradeVisibilityDto setHr(Authentication auth,UUID enterprise,UUID term,UUID caseId,String key,SetVisibility input){
        access.hr(auth,enterprise,term,caseId);var payload=new LinkedHashMap<String,Object>();payload.put("enterpriseOrgId",enterprise);payload.put("termId",term);payload.put("caseId",caseId);payload.put("request",input);
        return ledger.execute(command(auth,enterprise,"T3_GRADE_VISIBILITY",key,payload,200),GradeVisibilityDto.class,()->{
            access.holdTerm(term,true);var old=hrPolicy(enterprise,term,caseId);check(old,input);
            if(old.version()==0)jdbc.update("INSERT INTO internship_grade_visibility VALUES (?,?,?,?,?,1)",enterprise.toString(),term.toString(),scopeKey(caseId),caseId==null?null:caseId.toString(),input.enabled());
            else if(jdbc.update("UPDATE internship_grade_visibility SET enabled=?,version=version+1 WHERE enterprise_org_id=? AND term_id=? AND scope_key=? AND version=?",input.enabled(),enterprise.toString(),term.toString(),scopeKey(caseId),old.version())!=1)throw failure(409,"VERSION_CONFLICT");
            var next=hrDto(enterprise,term,caseId,new Policy(input.enabled(),old.version()+1));
            var fields=new LinkedHashMap<String,Object>();fields.put("enterpriseOrgId",enterprise.toString());if(caseId!=null)fields.put("caseId",caseId.toString());fields.put("enabled",next.enabled());fields.put("version",next.version());
            audit.workflow(actor(auth),"GRADE_VISIBILITY_CHANGED",caseId==null?term.toString():caseId.toString(),enterprise.toString(),fields);return next;
        },v->v.caseId()==null?v.termId().toString():v.caseId().toString());
    }
    public StudentResultDto result(Authentication auth,UUID caseId){
        var c=access.student(auth,caseId);access.holdTerm(c.termId(),false);
        var school=schoolPolicy(c.schoolOrgId(),c.termId());
        var hr=hrPolicy(c.enterpriseOrgId(),c.termId(),caseId);if(hr.version()==0)hr=hrPolicy(c.enterpriseOrgId(),c.termId(),null);
        if(!school.enabled() || !hr.enabled())return StudentResultDto.hidden(caseId);
        var id=evaluations.idForCase(caseId);if(id==null)return StudentResultDto.hidden(caseId);
        var r=root(id,true);if(r.publishedVersion()==null)return StudentResultDto.hidden(caseId);
        var published=evaluations.version(r,r.publishedVersion());
        if(published.state()!=EvaluationState.PUBLISHED)throw failure(503,"DEPENDENCY_UNAVAILABLE");
        return new StudentResultDto(caseId,Visibility.VISIBLE,published,ResultReason.VISIBLE);
    }
    /** School reload is scoped and separate from Mentor/student endpoints. */
    public EvaluationDto schoolRead(Authentication auth,UUID id){var r=root(id,false);access.schoolCase(auth,r.caseId());return evaluations.current(root(id,true));}
    public WorkspaceRuntime.Page<EvaluationDto> schoolList(Authentication auth,UUID term,UUID cohort,EvaluationState state,int page,int size){
        page(page,size);
        var schools=jdbc.queryForList("SELECT school_org_id FROM terms WHERE id=?",String.class,term.toString());
        if(schools.isEmpty())throw failure(404,"RESOURCE_NOT_FOUND");var school=UUID.fromString(schools.getFirst());
        access.school(auth,school,term,"t3:workspace:read");access.school(auth,school,term,GradeAccess.PUBLISH);
        String filter=" FROM internship_evaluations e JOIN internship_cases c ON c.id=e.case_id JOIN internship_evaluation_versions v ON v.evaluation_id=e.id AND v.version=e.current_version WHERE c.school_org_id=? AND c.term_id=?";
        var args=new ArrayList<Object>(List.of(school.toString(),term.toString()));
        if(cohort!=null){
            if(jdbc.queryForObject("SELECT COUNT(*) FROM internship_cohorts WHERE id=? AND school_org_id=? AND term_id=?",Long.class,cohort.toString(),school.toString(),term.toString())!=1)throw failure(404,"RESOURCE_NOT_FOUND");
            filter+=" AND EXISTS(SELECT 1 FROM internship_cohort_cases cc WHERE cc.case_id=c.id AND cc.cohort_id=?)";args.add(cohort.toString());
        }
        if(state!=null){filter+=" AND v.state=?";args.add(state.name());}
        long total=jdbc.queryForObject("SELECT COUNT(*)"+filter,Long.class,args.toArray());
        args.add(size);args.add((long)page*size);
        var values=jdbc.query("SELECT v.snapshot_json"+filter+" ORDER BY c.id ASC,v.revision_no DESC LIMIT ? OFFSET ?",(r,n)->JSON.readValue(r.getString(1),EvaluationDto.class),args.toArray());
        return new WorkspaceRuntime.Page<>(values,page,size,total,(int)Math.ceilDiv(total,(long)size));
    }
    public boolean studentEvidence(Authentication auth,UUID caseId,String fileId){
        var result=result(auth,caseId);
        return result.evaluation()!=null && result.evaluation().scores().stream().flatMap(s->s.evidenceFileIds().stream()).anyMatch(id->id.toString().equals(fileId));
    }
    private Policy schoolPolicy(UUID school,UUID term){return jdbc.query("SELECT enabled,version FROM internship_school_result_policies WHERE school_org_id=? AND term_id=? FOR SHARE",(r,n)->new Policy(r.getBoolean(1),r.getLong(2)),school.toString(),term.toString()).stream().findFirst().orElse(new Policy(false,0));}
    private Policy hrPolicy(UUID enterprise,UUID term,UUID id){return jdbc.query("SELECT enabled,version FROM internship_grade_visibility WHERE enterprise_org_id=? AND term_id=? AND scope_key=? FOR SHARE",(r,n)->new Policy(r.getBoolean(1),r.getLong(2)),enterprise.toString(),term.toString(),scopeKey(id)).stream().findFirst().orElse(new Policy(false,0));}
    private static String scopeKey(UUID id){return id==null?"ORGANIZATION_TERM":id.toString();}
    private static void check(Policy old,SetVisibility input){if(old.version()!=input.expectedVersion())throw failure(409,"VERSION_CONFLICT");}
    private static SchoolResultVisibilityDto schoolDto(UUID school,UUID term,Policy p){return new SchoolResultVisibilityDto(school,term,p.enabled(),p.version());}
    private static GradeVisibilityDto hrDto(UUID org,UUID term,UUID id,Policy p){return new GradeVisibilityDto(org,term,id,id==null?VisibilityScope.ORGANIZATION_TERM:VisibilityScope.CASE,p.enabled(),p.version());}
    private static OperationLedger.Command command(Authentication auth,UUID org,String operation,String key,Object input,int status){return new OperationLedger.Command(actor(auth),org.toString(),operation,uuid(key),input,status);}
}
