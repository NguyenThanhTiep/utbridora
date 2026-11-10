package vn.utbridora.auth.modules.internship.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.contracts.placement.PlacementReadPort;
import vn.utbridora.auth.modules.internship.application.WorkspaceService;
import vn.utbridora.auth.modules.internship.api.CohortGroupDtos.*;
import vn.utbridora.auth.modules.internship.api.TaskDtos.*;
import vn.utbridora.auth.modules.internship.domain.*;
import vn.utbridora.auth.operations.*;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException.Kind.*;

/** Week 1 runtime for create/read cohorts, direct-ID intake, grouping, membership and task creation. */
@Service
public class WorkspaceRuntime {
    public record Page<T>(List<T> content,int page,int size,long totalElements,int totalPages) {}
    public record BatchCases(Long expectedVersion,List<UUID> placementIds,UUID previewId,String fileHash) {
        public BatchCases {
            if(expectedVersion==null || expectedVersion<1 || expectedVersion>9_007_199_254_740_991L || placementIds==null || placementIds.isEmpty() || placementIds.size()>1000
                || placementIds.stream().anyMatch(Objects::isNull) || new HashSet<>(placementIds).size()!=placementIds.size()) throw new IllegalArgumentException("Invalid batch");
            placementIds=List.copyOf(placementIds);
        }
    }
    public record BatchItem(UUID placementId,UUID caseId,String outcome,String reasonCode) {}
    public record BatchResult(UUID cohortId,long version,List<BatchItem> items) {}
    public record PlannedGroup(int ordinal,List<UUID> caseIds) {}
    public record GroupingPlan(UUID planId,long cohortVersion,Instant expiresAt,List<PlannedGroup> groups) {}
    public record PlanData(List<PlannedGroup> groups,Map<String,Long> caseVersions) {}
    public record MembershipResult(@JsonProperty("case") CaseRuntime.CaseRef caseRef,UUID toGroupId,Long fromGroupVersion,long toGroupVersion) {}
    private final JdbcTemplate jdbc;private final JdbcWorkspaceSession session;private final WorkspaceService workspace;
    private final WorkspaceAuthority authority;private final CaseRuntime cases;private final PlacementReadPort placements;
    private final AuthorizationPolicy core;private final SchoolDelegationAccess delegations;private final OperationLedger ledger;
    private final vn.utbridora.auth.audit.AuditService audit;
    private final JsonMapper json=JsonMapper.builder().build();
    public WorkspaceRuntime(JdbcTemplate jdbc,JdbcWorkspaceSession session,WorkspaceService workspace,WorkspaceAuthority authority,
            CaseRuntime cases,PlacementReadPort placements,AuthorizationPolicy core,SchoolDelegationAccess delegations,OperationLedger ledger,vn.utbridora.auth.audit.AuditService audit) {
        this.jdbc=jdbc;this.session=session;this.workspace=workspace;this.authority=authority;this.cases=cases;
        this.placements=placements;this.core=core;this.delegations=delegations;this.ledger=ledger;this.audit=audit;
    }
    @Transactional public CohortDto createCohort(Authentication actor,String key,CreateCohort input) {
        var selected=authority.selected(actor);
        if(!selected.kind().equals("HR")) throw new WorkspaceRuleException(DENIED);
        if(!input.termId().toString().equals(selected.scope().termId())) throw new WorkspaceRuleException(SCOPE_MISMATCH);
        var scope=new WorkspaceScope(UUID.fromString(selected.scope().enterpriseOrgId()),UUID.fromString(selected.scope().schoolOrgId()),input.termId());
        authority.requireHr(actor,scope,null);
        return ledger.execute(command(actor,scope,"T3_COHORT_CREATE",key,input,201),CohortDto.class,
            ()->workspace.createCohort(actor,input),c->c.cohortId().toString());
    }
    @Transactional public TaskDto createTask(Authentication actor,String key,CreateTask input) {
        var selected=requireKind(actor,"MENTOR");
        if(!core.holdOrganization(actor,selected.scope().enterpriseOrgId(),Role.ENTERPRISE,"internship:enterprise:task_write")) throw new WorkspaceRuleException(DENIED);
        var group=group(input.groupId());var cohort=cohort(group.cohortId());
        for(UUID id:input.caseIds()) authority.requireMentor(actor,cohort.scope(),group.id(),id);
        if(!core.hasOrganization(actor,cohort.scope().enterpriseOrgId().toString(),Role.ENTERPRISE,"internship:enterprise:task_write")) throw new WorkspaceRuleException(DENIED);
        return ledger.execute(command(actor,cohort.scope(),"T3_TASK_CREATE",key,input,201),TaskDto.class,
            ()->workspace.createTask(actor,input),t->t.taskId().toString());
    }
    @Transactional public TaskDto readTask(Authentication actor,UUID task) {
        try {return workspace.readTask(actor,task);}
        catch(WorkspaceRuleException failure) {if(failure.kind()==DENIED) throw new WorkspaceRuleException(NOT_FOUND);throw failure;}
    }
    @Transactional public Page<TaskDto> tasks(Authentication actor,UUID group,UUID caseId,String state,Instant from,Instant until,int page,int size) {
        pagination(page,size);
        if(state!=null) AssignmentState.valueOf(state);
        if(from!=null && until!=null && from.isAfter(until)) throw new IllegalArgumentException("Invalid dates");
        // Scope SQL bounds candidates before per-assignment filtering, including historical task-group snapshots.
        String actorId=WorkspaceAuthority.actor(actor);
        List<String> ids=jdbc.query("SELECT DISTINCT t.id,t.due_at FROM internship_tasks t JOIN internship_groups g ON g.id=t.group_id JOIN internship_cohorts c ON c.id=g.cohort_id JOIN internship_task_assignments a ON a.task_id=t.id WHERE (? IS NULL OR t.group_id=?) AND (? IS NULL OR a.case_id=?) AND (? IS NULL OR a.state=?) AND (? IS NULL OR t.due_at>=?) AND (? IS NULL OR t.due_at<=?) AND (a.student_user_id=? OR EXISTS(SELECT 1 FROM internship_context_assignments x WHERE x.user_id=? AND x.enterprise_org_id=c.enterprise_org_id AND x.school_org_id=c.school_org_id AND x.term_id=c.term_id AND x.active=true)) ORDER BY t.due_at,t.id",
            (r,n)->r.getString("id"),str(group),str(group),str(caseId),str(caseId),state,state,from==null?null:java.time.LocalDateTime.ofInstant(from,java.time.ZoneOffset.UTC),from==null?null:java.time.LocalDateTime.ofInstant(from,java.time.ZoneOffset.UTC),until==null?null:java.time.LocalDateTime.ofInstant(until,java.time.ZoneOffset.UTC),until==null?null:java.time.LocalDateTime.ofInstant(until,java.time.ZoneOffset.UTC),actorId,actorId);
        List<TaskDto> visible=new ArrayList<>();
        for(String id:ids) try {visible.add(workspace.readTask(actor,UUID.fromString(id)));} catch(WorkspaceRuleException denied) {if(denied.kind()!=DENIED) throw denied;}
        return page(visible,page,size);
    }
    @Transactional public BatchResult intake(Authentication actor,UUID cohortId,String key,BatchCases input) {
        requireKind(actor,"HR");
        Cohort cohort=cohort(cohortId);authority.requireHr(actor,cohort.scope(),null);
        if(input.previewId()!=null || input.fileHash()!=null) throw new OperationFailure(422,"BUSINESS_VALIDATION","Chỉ nhận danh sách ID trực tiếp ở bước này.",null);
        return ledger.execute(command(actor,cohort.scope(),"T3_COHORT_INTAKE",key,Map.of("cohortId",cohortId,"request",input),200),BatchResult.class,()->{
            requireVersion(cohort,input.expectedVersion());List<BatchItem> items=new ArrayList<>();
            for(UUID placementId:input.placementIds().stream().sorted().toList()) {
                var p=placements.readCurrentForGuard(placementId.toString()).orElse(null);
                var ids=jdbc.queryForList("SELECT id FROM internship_cases WHERE placement_id=?",String.class,placementId.toString());
                if(p==null || !matches(cohort,p) || !Set.of("APPROVED","ACTIVE").contains(p.state()) || p.approvedAt()==null || ids.isEmpty()) {
                    items.add(new BatchItem(placementId,null,"INVALID","PLACEMENT_UNAVAILABLE"));continue;
                }
                UUID caseId=UUID.fromString(ids.getFirst());
                var currentCase=cases.read(caseId);
                if(!Set.of("WAITING_START","IN_PROGRESS").contains(currentCase.state())) {items.add(new BatchItem(placementId,null,"INVALID","PLACEMENT_UNAVAILABLE"));continue;}
                var bound=jdbc.queryForList("SELECT cohort_id FROM internship_cohort_cases WHERE case_id=? FOR UPDATE",String.class,caseId.toString());
                if(!bound.isEmpty()) {
                    boolean same=bound.getFirst().equals(cohortId.toString());
                    items.add(new BatchItem(placementId,same?caseId:null,same?"ALREADY_PRESENT":"INVALID",same?"ALREADY_PRESENT":"PLACEMENT_UNAVAILABLE"));continue;
                }
                jdbc.update("INSERT INTO internship_cohort_cases(cohort_id,case_id) VALUES (?,?)",cohortId.toString(),caseId.toString());
                long version=cases.advanceMembershipVersion(caseId,currentCase.version());
                audit.groupMembershipChanged(WorkspaceAuthority.actor(actor),caseId.toString(),cohort.scope().schoolOrgId().toString(),cohortId.toString(),null,null,version,"COHORT_ADDED");
                items.add(new BatchItem(placementId,caseId,"ADDED","NONE"));
            }
            jdbc.update("UPDATE internship_cohorts SET version=version+1 WHERE id=?",cohortId.toString());
            return new BatchResult(cohortId,cohort.version()+1,List.copyOf(items));
        },r->r.cohortId().toString());
    }
    @Transactional public GroupingPlan preview(Authentication actor,UUID id,String key,int groupSize,long expectedVersion) {
        requireKind(actor,"HR");
        Cohort c=cohort(id);authority.requireHr(actor,c.scope(),null);
        if(groupSize<1 || groupSize>1000) throw new IllegalArgumentException("Invalid group size");
        return ledger.execute(command(actor,c.scope(),"T3_GROUP_PREVIEW",key,Map.of("cohortId",id,"groupSize",groupSize,"expectedVersion",expectedVersion),200),GroupingPlan.class,()->{
            requireVersion(c,expectedVersion);
            var candidates=jdbc.queryForList("SELECT case_id FROM internship_cohort_cases WHERE cohort_id=? AND group_id IS NULL ORDER BY case_id",String.class,id.toString());
            if(candidates.isEmpty()) throw new OperationFailure(422,"NO_GROUPABLE_CASES","Không có hồ sơ để chia nhóm.",null);
            List<PlannedGroup> groups=new ArrayList<>();Map<String,Long> versions=new LinkedHashMap<>();List<UUID> pending=new ArrayList<>();
            for(String value:candidates) {
                UUID caseId=UUID.fromString(value);long version=cases.read(caseId).version();
                cases.requireMembership(actor,c,caseId,version);versions.put(value,version);pending.add(caseId);
                if(pending.size()==groupSize) {groups.add(new PlannedGroup(groups.size()+1,List.copyOf(pending)));pending.clear();}
            }
            if(!pending.isEmpty()) groups.add(new PlannedGroup(groups.size()+1,List.copyOf(pending)));
            UUID plan=UUID.randomUUID();Instant expiry=Instant.now().plusSeconds(900);
            jdbc.update("INSERT INTO internship_grouping_plans(id,cohort_id,cohort_version,groups_json,expires_at) VALUES (?,?,?,?,?)",plan.toString(),id.toString(),c.version(),json.writeValueAsString(new PlanData(groups,versions)),java.time.LocalDateTime.ofInstant(expiry,java.time.ZoneOffset.UTC));
            return new GroupingPlan(plan,c.version(),expiry,List.copyOf(groups));
        },p->p.planId().toString());
    }
    @Transactional public GroupDto[] confirm(Authentication actor,UUID id,String key,UUID plan,long expectedVersion) {
        requireKind(actor,"HR");
        Cohort c=cohort(id);authority.requireHr(actor,c.scope(),null);
        return ledger.execute(command(actor,c.scope(),"T3_GROUP_CONFIRM",key,Map.of("cohortId",id,"planId",plan,"expectedVersion",expectedVersion),200),GroupDto[].class,()->{
            requireVersion(c,expectedVersion);
            var plans=jdbc.queryForList("SELECT groups_json,cohort_version,expires_at FROM internship_grouping_plans WHERE id=? AND cohort_id=? FOR UPDATE",plan.toString(),id.toString());
            if(plans.isEmpty()) throw new WorkspaceRuleException(NOT_FOUND);
            var stored=plans.getFirst();
            if(!((java.time.LocalDateTime)stored.get("expires_at")).toInstant(java.time.ZoneOffset.UTC).isAfter(Instant.now())) throw new OperationFailure(409,"GROUPING_PLAN_EXPIRED","Kế hoạch chia nhóm đã hết hạn.",null);
            if(((Number)stored.get("cohort_version")).longValue()!=c.version()) throw new WorkspaceRuleException(VERSION_CONFLICT);
            var data=json.readValue((String)stored.get("groups_json"),PlanData.class);List<GroupDto> result=new ArrayList<>();
            for(var planned:data.groups()) {
                UUID groupId=UUID.randomUUID();
                jdbc.update("INSERT INTO internship_groups(id,cohort_id,code,version) VALUES (?,?,?,1)",groupId.toString(),id.toString(),"G-"+groupId);
                InternshipGroup group=group(groupId);
                for(UUID caseId:planned.caseIds()) {
                    long version=data.caseVersions().get(caseId.toString());cases.requireMembership(actor,c,caseId,version);
                    var updated=group.add(caseId,session.currentGroup(id,caseId),group.version());
                    session.addMember(updated,caseId,version,group.version());group=updated;
                }
                result.add(group.toDto());
            }
            jdbc.update("UPDATE internship_cohorts SET version=version+1 WHERE id=?",id.toString());
            return result.toArray(GroupDto[]::new);
        },r->plan.toString());
    }
    @Transactional public MembershipResult add(Authentication actor,UUID groupId,String key,AssignGroupMember input) {
        requireKind(actor,"HR");
        var g=group(groupId);var c=cohort(g.cohortId());authority.requireHr(actor,c.scope(),groupId);
        return ledger.execute(command(actor,c.scope(),"T3_GROUP_ADD",key,Map.of("groupId",groupId,"request",input),201),MembershipResult.class,()->{
            var result=workspace.addMember(actor,groupId,input);return new MembershipResult(cases.read(input.caseId()),groupId,null,result.version());
        },r->r.caseRef.caseId().toString());
    }
    @Transactional public MembershipResult move(Authentication actor,UUID groupId,String key,MoveCase input) {
        requireKind(actor,"HR");
        var g=group(groupId);var c=cohort(g.cohortId());var target=group(input.toGroupId());var targetC=cohort(target.cohortId());
        authority.requireHr(actor,c.scope(),groupId);authority.requireHr(actor,targetC.scope(),target.id());
        return ledger.execute(command(actor,c.scope(),"T3_GROUP_MOVE",key,Map.of("groupId",groupId,"request",input),200),MembershipResult.class,()->{
            var result=workspace.moveMember(actor,groupId,input);return new MembershipResult(cases.read(input.caseId()),input.toGroupId(),result.source().version(),result.target().version());
        },r->r.caseRef.caseId().toString());
    }
    @Transactional public List<WorkspaceAuthority.Assignment> assignments(Authentication actor) {return authority.assignments(actor);}
    @Transactional public Page<CohortDto> cohorts(Authentication actor,UUID term,int page,int size) {
        pagination(page,size);var a=authority.selected(actor);
        if(term!=null && !term.toString().equals(a.scope().termId())) return page(List.of(),page,size);
        var ids=jdbc.queryForList("SELECT id FROM internship_cohorts WHERE enterprise_org_id=? AND school_org_id=? AND term_id=? ORDER BY code,id",String.class,a.scope().enterpriseOrgId(),a.scope().schoolOrgId(),a.scope().termId());
        List<CohortDto> visible=new ArrayList<>();
        for(String id:ids) {var c=cohort(UUID.fromString(id));if(authority.canReadScope(actor,c.scope(),null)
            && (a.kind().equals("HR") || a.kind().equals("MENTOR") && jdbc.queryForObject("SELECT COUNT(*) FROM internship_groups WHERE id=? AND cohort_id=?",Integer.class,a.scope().groupId(),id)==1)) visible.add(c.toDto());}
        return page(visible,page,size);
    }
    @Transactional public Page<GroupDto> groups(Authentication actor,UUID cohortId,boolean mentorOnly,int page,int size) {
        pagination(page,size);var a=authority.selected(actor);
        if(cohortId!=null && (!authority.canReadScope(actor,cohort(cohortId).scope(),null)
            || a.kind().equals("MENTOR") && jdbc.queryForObject("SELECT COUNT(*) FROM internship_groups WHERE id=? AND cohort_id=?",Integer.class,a.scope().groupId(),cohortId.toString())!=1))
            throw new WorkspaceRuleException(NOT_FOUND);
        if(mentorOnly && !a.kind().equals("MENTOR")) return page(List.of(),page,size);
        String selectedGroup=a.kind().equals("MENTOR")?a.scope().groupId():null;
        var ids=jdbc.queryForList("SELECT g.id FROM internship_groups g JOIN internship_cohorts c ON c.id=g.cohort_id WHERE c.enterprise_org_id=? AND c.school_org_id=? AND c.term_id=? AND (? IS NULL OR c.id=?) AND (? IS NULL OR g.id=?) ORDER BY g.code,g.id",String.class,
            a.scope().enterpriseOrgId(),a.scope().schoolOrgId(),a.scope().termId(),str(cohortId),str(cohortId),selectedGroup,selectedGroup);
        List<GroupDto> visible=new ArrayList<>();
        for(String id:ids) {
            var g=group(UUID.fromString(id));var c=cohort(g.cohortId());
            if(authority.canReadScope(actor,c.scope(),g.id())) {
                var mentors=jdbc.queryForList("SELECT DISTINCT user_id FROM internship_context_assignments WHERE group_id=? AND kind='MENTOR' ORDER BY user_id",String.class,id);
                // Only current chain-valid mentors are exposed, not historical or revoked assignments.
                var current=mentors.stream().filter(user->core.currentSubject(user).map(auth->authority.assignments(auth).stream().anyMatch(x->x.kind().equals("MENTOR") && id.equals(x.scope().groupId()))).orElse(false)).map(UUID::fromString).toList();
                var visibleCases=a.scope().caseId()==null?g.caseIds():g.caseIds().stream().filter(caseId->caseId.toString().equals(a.scope().caseId())).toList();
                visible.add(new GroupDto(g.id(),g.cohortId(),g.code(),visibleCases,current,g.version()));
            }
        }
        return page(visible,page,size);
    }
    @Transactional public CaseRuntime.CaseRef caseRef(Authentication actor,UUID id) {
        var c=cases.read(id);
        if(core.isSelf(actor,c.studentUserId().toString()) && core.hasOrganization(actor,c.schoolOrgId().toString(),Role.STUDENT,"t3:workspace:read")) return c;
        try {delegations.require(actor,c.schoolOrgId().toString(),c.termId().toString(),"t3:workspace:read");return c;} catch(OperationFailure denied) { /* try enterprise scope */ }
        var bound=jdbc.queryForList("SELECT cohort_id,group_id FROM internship_cohort_cases WHERE case_id=?",id.toString());
        if(!bound.isEmpty()) {
            var row=bound.getFirst();var cohort=cohort(UUID.fromString((String)row.get("cohort_id")));
            UUID group=row.get("group_id")==null?null:UUID.fromString((String)row.get("group_id"));
            try {
                var selected=authority.selected(actor);
                if(authority.canReadScope(actor,cohort.scope(),group) && (selected.kind().equals("HR") || group!=null)
                    && (selected.scope().caseId()==null || selected.scope().caseId().equals(id.toString()))) return c;
            } catch(WorkspaceRuleException denied) {if(denied.kind()==UNAVAILABLE) throw denied;}
        }
        throw new WorkspaceRuleException(NOT_FOUND);
    }
    private static OperationLedger.Command command(Authentication actor,WorkspaceScope scope,String op,String key,Object input,int status) {
        return new OperationLedger.Command(WorkspaceAuthority.actor(actor),scope.enterpriseOrgId().toString(),op,key,input,status);
    }
    private WorkspaceAuthority.Assignment requireKind(Authentication actor,String kind) {
        var selected=authority.selected(actor);if(!selected.kind().equals(kind)) throw new WorkspaceRuleException(DENIED);return selected;
    }
    private Cohort cohort(UUID id) {var c=session.cohort(id);if(c==null) throw new WorkspaceRuleException(NOT_FOUND);return c;}
    private InternshipGroup group(UUID id) {var g=session.group(id);if(g==null) throw new WorkspaceRuleException(NOT_FOUND);return g;}
    private static void requireVersion(Cohort c,long expected) {if(c.version()!=expected || expected>=9_007_199_254_740_991L) throw new WorkspaceRuleException(VERSION_CONFLICT);}
    private static boolean matches(Cohort c,PlacementReadPort.PlacementView p) {return c.scope().enterpriseOrgId().toString().equals(p.enterpriseOrgId()) && c.scope().schoolOrgId().toString().equals(p.schoolOrgId()) && c.scope().termId().toString().equals(p.termId());}
    private static String str(UUID id) {return id==null?null:id.toString();}
    private static void pagination(int page,int size) {if(page<0 || page>10000 || size<1 || size>100) throw new IllegalArgumentException("Invalid pagination");}
    private static <T> Page<T> page(List<T> all,int page,int size) {int from=Math.min(page*size,all.size());return new Page<>(all.subList(from,Math.min(from+size,all.size())),page,size,all.size(),(all.size()+size-1)/size);}
}
