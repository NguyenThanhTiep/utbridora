package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.contracts.placement.PlacementReadPort;
import vn.utbridora.auth.operations.OperationLedger;
import static vn.utbridora.auth.operations.WorkflowValues.*;
import static vn.utbridora.auth.modules.internship.api.InternshipOperationsDtos.*;

@Service
@Transactional
public class InternshipOperationsService {
    private final JdbcTemplate jdbc;private final OperationsAccess access;private final CaseRuntime cases;
    private final OperationLedger ledger;private final AuditService audit;private final PlacementReadPort source;private final WorkspaceAuthority authority;
    private final vn.utbridora.auth.authorization.AuthorizationPolicy core;
    public InternshipOperationsService(JdbcTemplate jdbc,OperationsAccess access,CaseRuntime cases,OperationLedger ledger,AuditService audit,PlacementReadPort source,WorkspaceAuthority authority,vn.utbridora.auth.authorization.AuthorizationPolicy core) {
        this.jdbc=jdbc;this.access=access;this.cases=cases;this.ledger=ledger;this.audit=audit;this.source=source;this.authority=authority;this.core=core;
    }
    public WorkspaceSummary summary(Authentication auth,UUID term,UUID cohort,String kind) {
        var scope=access.scope(auth,term,kind);var ids=new ArrayList<>(access.caseIds(scope));
        if(cohort!=null) {
            boolean exists=jdbc.queryForObject("SELECT COUNT(*) FROM internship_cohorts WHERE id=? AND school_org_id=? AND term_id=? AND (? IS NULL OR enterprise_org_id=?)",Integer.class,cohort.toString(),scope.school(),scope.term(),scope.enterprise(),scope.enterprise())==1;
            if(!exists) throw failure(404,"RESOURCE_NOT_FOUND");
            var members=new HashSet<>(jdbc.queryForList("SELECT case_id FROM internship_cohort_cases WHERE cohort_id=?",String.class,cohort.toString()));ids.removeIf(id->!members.contains(id));
        }
        var cohorts=jdbc.queryForList("SELECT id FROM internship_cohorts WHERE school_org_id=? AND term_id=? AND (? IS NULL OR enterprise_org_id=?) AND (? IS NULL OR id=?) AND (? IS NULL OR EXISTS(SELECT 1 FROM internship_groups g WHERE g.cohort_id=internship_cohorts.id AND g.id=?))",String.class,scope.school(),scope.term(),scope.enterprise(),scope.enterprise(),str(cohort),str(cohort),scope.group(),scope.group());
        var groups=cohorts.isEmpty()?List.<String>of():jdbc.queryForList("SELECT id FROM internship_groups WHERE cohort_id IN ("+marks(cohorts)+") AND (? IS NULL OR id=?) ORDER BY id",String.class,append(cohorts,scope.group(),scope.group()));
        Set<String> mentors=new HashSet<>();
        if(!groups.isEmpty()) for(String user:jdbc.queryForList("SELECT DISTINCT user_id FROM internship_context_assignments WHERE kind='MENTOR' AND group_id IN ("+marks(groups)+")",String.class,groups.toArray())) {
            // Assignment lookup validates current ancestry; a revoked chain contributes no mentor.
            var candidates=jdbc.queryForList("SELECT id FROM users WHERE id=? AND status='ACTIVE'",String.class,user);
            if(!candidates.isEmpty()) {
                var current=jdbc.queryForObject("SELECT COUNT(*) FROM memberships WHERE user_id=? AND role='ENTERPRISE' AND active=true",Integer.class,user);
                if(current>0 && currentMentor(user,groups)) mentors.add(user);
            }
        }
        Map<String,Long> counts=new HashMap<>();long late=0;
        if(!ids.isEmpty()) {
            var rows=jdbc.queryForList("SELECT a.state,COUNT(*) n FROM internship_task_assignments a WHERE a.case_id IN ("+marks(ids)+") GROUP BY a.state",ids.toArray());
            for(var row:rows) counts.put(row.get("state").toString(),((Number)row.get("n")).longValue());
            late=jdbc.queryForObject("SELECT COUNT(*) FROM internship_task_assignments a JOIN internship_tasks t ON t.id=a.task_id WHERE a.case_id IN ("+marks(ids)+") AND a.state<>'COMPLETED' AND t.due_at<?",Long.class,append(ids,utc(Instant.now())));
        }
        return new WorkspaceSummary(cohorts.size(),ids.size(),groups.size(),mentors.size(),java.util.Arrays.stream(vn.utbridora.auth.modules.internship.api.TaskDtos.AssignmentState.values()).map(s->new AssignmentCount(s.name(),counts.getOrDefault(s.name(),0L))).toList(),late,counts.getOrDefault("SUBMITTED",0L));
    }
    private boolean currentMentor(String user,List<String> groups) {
        // T1 reads the identity; T3 validates its current assignment chain without replacing request context.
        return coreSubject(user).map(auth->authority.assignments(auth).stream().anyMatch(a->a.kind().equals("MENTOR") && groups.contains(a.scope().groupId()))).orElse(false);
    }
    private Optional<Authentication> coreSubject(String user) {return core.currentSubject(user).map(a->(Authentication)a);}
    public WorkspaceRuntime.Page<CalendarEvent> calendar(Authentication auth,Instant from,Instant until,UUID caseId,int page,int size) {
        actor(auth);page(page,size);
        if(from==null || until==null || !from.isBefore(until) || Duration.between(from,until).compareTo(Duration.ofDays(62))>0) throw failure(422,"CALENDAR_RANGE_INVALID");
        List<String> ids;boolean student;
        if(caseId!=null) {
            var c=cases.read(caseId);var s=access.scope(auth,c.termId(),"calendar");ids=new ArrayList<>(access.caseIds(s));ids.removeIf(v->!v.equals(caseId.toString()));student=s.student()!=null;
            if(ids.isEmpty()) throw failure(404,"RESOURCE_NOT_FOUND");
        } else {
            // CalendarQuery has no termId: students use their own cases; staff use all currently authorized terms.
            ids=new ArrayList<>();student=false;
            for(String term:jdbc.queryForList("SELECT DISTINCT term_id FROM internship_cases ORDER BY term_id",String.class)) try {
                var s=access.scope(auth,UUID.fromString(term),"calendar");ids.addAll(access.caseIds(s));student|=s.student()!=null;
            } catch(vn.utbridora.auth.operations.OperationFailure denied) {if(denied.status>=500 || denied.status==401) throw denied;}
            catch(vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException denied) {if(denied.kind()==vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException.Kind.UNAVAILABLE) throw denied;}
        }
        if(ids.isEmpty()) return new WorkspaceRuntime.Page<>(List.of(),page,size,0,0);
        String columns=student?"t.id task_id,a.id assignment_id,t.title,t.due_at,a.state":"DISTINCT t.id task_id,NULL assignment_id,t.title,t.due_at,NULL state";
        String sql=" FROM internship_tasks t JOIN internship_task_assignments a ON a.task_id=t.id WHERE a.case_id IN ("+marks(ids)+") AND t.due_at>=? AND t.due_at<?";
        var args=append(ids,utc(from),utc(until));
        long total=jdbc.queryForObject("SELECT COUNT("+(student?"*":"DISTINCT t.id")+")"+sql,Long.class,args);
        var rows=jdbc.query("SELECT "+columns+sql+" ORDER BY due_at,task_id,assignment_id LIMIT ? OFFSET ?",(r,n)->new CalendarEvent(UUID.fromString(r.getString("task_id")),r.getString("assignment_id")==null?null:UUID.fromString(r.getString("assignment_id")),r.getString("title"),instant(r,"due_at"),r.getString("state")),append(Arrays.asList(args),size,page*size));
        return new WorkspaceRuntime.Page<>(rows,page,size,total,(int)Math.ceilDiv(total,size));
    }
    public WorkspaceRuntime.Page<EnterpriseQualityTemplate> templates(Authentication auth,UUID term,int page,int size) {
        page(page,size);String school=access.school(auth,term,"t3:workspace:read");
        var all=jdbc.queryForList("SELECT template_json FROM internship_enterprise_quality_templates WHERE school_org_id=? AND term_id=? ORDER BY id,version",String.class,school,term.toString()).stream().map(s->JSON.readValue(s,EnterpriseQualityTemplate.class)).toList();
        return pageOf(all,page,size);
    }
    public EnterpriseQualityDto feedback(Authentication auth,String key,EnterpriseQualityInput input) {
        String school=access.school(auth,input.termId(),"t3:enterprise_quality:school:write");
        return ledger.execute(command(auth,school,"T3_ENTERPRISE_FEEDBACK",key,input,201),EnterpriseQualityDto.class,()->{
            if(!source.hasApprovedRelationship(school,input.enterpriseOrgId().toString(),input.termId().toString())) throw failure(404,"RESOURCE_NOT_FOUND");
            var templates=jdbc.queryForList("SELECT template_json FROM internship_enterprise_quality_templates WHERE id=? AND version=? AND school_org_id=? AND term_id=? FOR SHARE",String.class,input.templateId().toString(),input.templateVersion(),school,input.termId().toString());
            if(templates.size()!=1) throw failure(422,"ENTERPRISE_RUBRIC_INVALID");
            var template=JSON.readValue(templates.getFirst(),EnterpriseQualityTemplate.class);
            if(!template.templateId().equals(input.templateId()) || template.version()!=input.templateVersion()) throw failure(422,"ENTERPRISE_RUBRIC_INVALID");
            var codes=input.ratings().stream().map(EnterpriseRating::criterionCode).toList();
            if(new HashSet<>(codes).size()!=codes.size() || !new HashSet<>(codes).equals(template.criteria().stream().map(EnterpriseQualityCriterion::code).collect(java.util.stream.Collectors.toSet()))) throw failure(422,"ENTERPRISE_RUBRIC_INVALID");
            var id=UUID.randomUUID();var now=Instant.now();
            jdbc.update("INSERT INTO internship_enterprise_feedback(id,school_org_id,enterprise_org_id,term_id,template_id,template_version,input_json,created_by,created_at) VALUES(?,?,?,?,?,?,?,?,?)",id.toString(),school,input.enterpriseOrgId().toString(),input.termId().toString(),input.templateId().toString(),input.templateVersion(),JSON.writeValueAsString(input),actor(auth),utc(now));
            audit.workflow(actor(auth),"ENTERPRISE_QUALITY_EVALUATED",id.toString(),school,Map.of("enterpriseOrgId",input.enterpriseOrgId(),"schoolOrgId",school,"termId",input.termId(),"version",1));
            return new EnterpriseQualityDto(id,input,UUID.fromString(school),1,now);
        },r->r.id().toString());
    }
    public CoordinationIssue createIssue(Authentication auth,String key,CreateCoordinationIssue input) {
        var c=cases.read(input.caseId());access.schoolCase(auth,c,"t3:coordination:manage");
        return ledger.execute(command(auth,c.schoolOrgId().toString(),"T3_ISSUE_CREATE",key,input,201),CoordinationIssue.class,()->{
            holdActive(c);access.schoolCase(auth,c,"t3:coordination:manage");
            if(!access.owner(input.ownerUserId(),c)) throw failure(422,"ISSUE_OWNER_INVALID");
            UUID id=UUID.randomUUID();Instant now=Instant.now();
            jdbc.update("INSERT INTO internship_coordination_issues(id,case_id,category,description,owner_user_id,state,version,created_at,updated_at) VALUES(?,?,?,?,?,'OPEN',1,?,?)",id.toString(),c.caseId().toString(),input.category().name(),input.description(),input.ownerUserId().toString(),utc(now),utc(now));
            issueHistory(auth,id,1,"OPEN",c,"CREATE");return issue(id);
        },r->r.issueId().toString());
    }
    public CoordinationIssue updateIssue(Authentication auth,UUID id,String key,UpdateCoordinationIssue input) {
        var before=issue(id);var c=access.readCase(auth,before.caseId());access.coordinationWriter(auth,c);
        return ledger.execute(command(auth,c.schoolOrgId().toString(),"T3_ISSUE_UPDATE",key,Map.of("id",id,"input",input),200),CoordinationIssue.class,()->{
            holdActive(c);access.coordinationWriter(auth,c);
            jdbc.queryForObject("SELECT version FROM internship_coordination_issues WHERE id=? FOR UPDATE",Long.class,id.toString());var current=issue(id);
            if(current.version()!=input.expectedVersion()) throw failure(409,"VERSION_CONFLICT");
            if(current.state()==IssueState.RESOLVED || current.state()==IssueState.OPEN && input.state()!=IssueState.IN_PROGRESS || current.state()==IssueState.IN_PROGRESS && input.state()!=IssueState.RESOLVED) throw failure(409,"STATE_CONFLICT");
            jdbc.update("UPDATE internship_coordination_issues SET state=?,version=version+1,updated_at=? WHERE id=?",input.state().name(),utc(Instant.now()),id.toString());
            issueHistory(auth,id,current.version()+1,input.state().name(),c,"UPDATE");return issue(id);
        },r->r.issueId().toString());
    }
    public WorkspaceRuntime.Page<CoordinationIssue> issues(Authentication auth,UUID caseId,IssueState state,int page,int size) {
        page(page,size);access.readCase(auth,caseId);
        var rows=jdbc.queryForList("SELECT id FROM internship_coordination_issues WHERE case_id=? AND (? IS NULL OR state=?) ORDER BY created_at DESC,id",String.class,caseId.toString(),state==null?null:state.name(),state==null?null:state.name()).stream().map(UUID::fromString).map(this::issue).toList();return pageOf(rows,page,size);
    }
    private CoordinationIssue issue(UUID id) {
        return jdbc.query("SELECT * FROM internship_coordination_issues WHERE id=?",(r,n)->new CoordinationIssue(id,UUID.fromString(r.getString("case_id")),IssueCategory.valueOf(r.getString("category")),r.getString("description"),IssueState.valueOf(r.getString("state")),UUID.fromString(r.getString("owner_user_id")),r.getLong("version"),instant(r,"created_at"),instant(r,"updated_at")),id.toString()).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
    }
    private void issueHistory(Authentication auth,UUID id,long version,String state,CaseRuntime.CaseRef c,String operation) {
        jdbc.update("INSERT INTO internship_coordination_issue_history(issue_id,version,state,actor_id,created_at) VALUES(?,?,?,?,?)",id.toString(),version,state,actor(auth),utc(Instant.now()));
        audit.workflow(actor(auth),"COORDINATION_ISSUE_CHANGED",id.toString(),c.schoolOrgId().toString(),Map.of("enterpriseOrgId",c.enterpriseOrgId(),"schoolOrgId",c.schoolOrgId(),"operation",operation,"version",version));
    }
    public HiringInterestDto interest(Authentication auth,UUID id) {
        var c=cases.read(id);
        try {access.schoolCase(auth,c,"t3:workspace:read");} catch(vn.utbridora.auth.operations.OperationFailure denied) {if(denied.status>=500 || denied.status==401)throw denied;access.hrCase(auth,c,"t3:workspace:read");}
        return readInterest(id);
    }
    public HiringInterestDto interest(Authentication auth,UUID id,String key,HiringInterestInput input) {
        var c=cases.read(id);access.hrCase(auth,c,"t3:hiring_interest:manage");
        return ledger.execute(command(auth,c.enterpriseOrgId().toString(),"T3_HIRING_INTEREST",key,Map.of("caseId",id,"input",input),200),HiringInterestDto.class,()->{
            if(c.version()!=input.expectedCaseVersion()) throw failure(409,"VERSION_CONFLICT");holdActive(c);access.hrCase(auth,c,"t3:hiring_interest:manage");
            var old=readInterest(id);if(old.version()!=input.expectedVersion()) throw failure(409,"VERSION_CONFLICT");if(old.version()>0)version(old.version());
            Instant now=Instant.now();long version=old.version()+1;
            jdbc.update("INSERT INTO internship_hiring_interests(case_id,interested,note,version,updated_at,updated_by) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE interested=VALUES(interested),note=VALUES(note),version=VALUES(version),updated_at=VALUES(updated_at),updated_by=VALUES(updated_by)",id.toString(),input.interested(),input.note(),version,utc(now),actor(auth));
            jdbc.update("INSERT INTO internship_hiring_interest_history(case_id,version,interested,note,actor_id,created_at) VALUES(?,?,?,?,?,?)",id.toString(),version,input.interested(),input.note(),actor(auth),utc(now));
            audit.workflow(actor(auth),"HIRING_INTEREST_CHANGED",id.toString(),c.enterpriseOrgId().toString(),Map.of("caseId",id,"enterpriseOrgId",c.enterpriseOrgId(),"schoolOrgId",c.schoolOrgId(),"termId",c.termId(),"version",version,"interested",input.interested()));
            return new HiringInterestDto(id,input.interested(),input.note(),version,now);
        },r->r.caseId().toString());
    }
    private HiringInterestDto readInterest(UUID id) {return jdbc.query("SELECT * FROM internship_hiring_interests WHERE case_id=?",(r,n)->new HiringInterestDto(id,r.getBoolean("interested"),r.getString("note"),r.getLong("version"),instant(r,"updated_at")),id.toString()).stream().findFirst().orElse(new HiringInterestDto(id,false,null,0,null));}
    private void holdActive(CaseRuntime.CaseRef c) {
        var p=source.readCurrentForGuard(c.placementId().toString()).orElseThrow(()->failure(503,"DEPENDENCY_UNAVAILABLE"));
        var held=cases.holdForReport(c.caseId(),c.version());
        if(!held.equals(c) || !Set.of("WAITING_START","IN_PROGRESS").contains(c.state()) || !Set.of("APPROVED","ACTIVE").contains(p.state()) || p.approvedAt()==null) throw failure(409,"STATE_CONFLICT");
    }
    private static OperationLedger.Command command(Authentication auth,String scope,String op,String key,Object input,int status) {return new OperationLedger.Command(actor(auth),scope,op,key,input,status);}
    private static String str(UUID id) {return id==null?null:id.toString();}
    private static String marks(List<?> list) {return String.join(",",Collections.nCopies(list.size(),"?"));}
    private static Object[] append(List<?> first,Object... rest) {var out=new ArrayList<Object>(first);out.addAll(Arrays.asList(rest));return out.toArray();}
    private static <T> WorkspaceRuntime.Page<T> pageOf(List<T> all,int page,int size) {int from=Math.min(all.size(),page*size);return new WorkspaceRuntime.Page<>(all.subList(from,Math.min(from+size,all.size())),page,size,all.size(),(all.size()+size-1)/size);}
}
