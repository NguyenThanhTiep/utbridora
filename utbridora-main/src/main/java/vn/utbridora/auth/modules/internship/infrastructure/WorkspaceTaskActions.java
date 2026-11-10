package vn.utbridora.auth.modules.internship.infrastructure;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.modules.internship.api.TaskDtos.*;
import vn.utbridora.auth.operations.OperationLedger;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.operations.WorkflowValues.*;
@Service
public class WorkspaceTaskActions {
    public record StartAssignment(Long expectedVersion,String state) {}
    public record CalendarEvent(String taskId,String assignmentId,String title,Instant dueAt,String state) {}
    private final JdbcTemplate jdbc;private final WorkspaceRuntime runtime;private final JdbcWorkspaceSession session;private final CaseRuntime cases;
    private final AuthorizationPolicy core;private final OperationLedger ledger;private final AuditService audit;
    public WorkspaceTaskActions(JdbcTemplate jdbc,WorkspaceRuntime runtime,JdbcWorkspaceSession session,CaseRuntime cases,AuthorizationPolicy core,OperationLedger ledger,AuditService audit) {this.jdbc=jdbc;this.runtime=runtime;this.session=session;this.cases=cases;this.core=core;this.ledger=ledger;this.audit=audit;}
    @Transactional public AssignmentDto start(Authentication auth,String id,String key,StartAssignment request) {
        uuid(id);version(request.expectedVersion());if(!"IN_PROGRESS".equals(request.state())) throw failure(409,"STATE_CONFLICT");
        var ids=jdbc.queryForList("SELECT task_id FROM internship_task_assignments WHERE id=? AND student_user_id=?",String.class,id,actor(auth));
        if(ids.isEmpty()) throw failure(404,"RESOURCE_NOT_FOUND");var task=runtime.readTask(auth,UUID.fromString(ids.getFirst()));
        var assignment=task.assignments().stream().filter(a->a.assignmentId().toString().equals(id)).findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
        var c=cases.read(assignment.caseId());if(!core.holdOrganization(auth,c.schoolOrgId().toString(),Role.STUDENT,"internship:student:submission_write")) throw failure(404,"RESOURCE_NOT_FOUND");
        return ledger.execute(new OperationLedger.Command(actor(auth),c.schoolOrgId().toString(),"T3_ASSIGNMENT_START",key,Map.of("id",id,"request",request),200),AssignmentDto.class,()->{
            var group=session.group(task.groupId());var cohort=session.cohort(group.cohortId());cases.requireTaskParticipant(auth,cohort,group,assignment.caseId(),Instant.now());
            var current=jdbc.queryForMap("SELECT state,version FROM internship_task_assignments WHERE id=? FOR UPDATE",id);
            if(((Number)current.get("version")).longValue()!=request.expectedVersion()) throw failure(409,"VERSION_CONFLICT");
            String from=(String)current.get("state");if(!Set.of("NEW","REVISION_REQUIRED").contains(from)) throw failure(409,"STATE_CONFLICT");
            jdbc.update("UPDATE internship_task_assignments SET state='IN_PROGRESS',version=version+1 WHERE id=?",id);
            audit.workflow(actor(auth),"TASK_UPDATED",id,c.schoolOrgId().toString(),Map.of("taskId",task.taskId().toString(),"caseId",assignment.caseId().toString(),"fromStatus",from,"toStatus","IN_PROGRESS","assignmentVersion",request.expectedVersion()+1));
            return new AssignmentDto(assignment.assignmentId(),assignment.caseId(),assignment.studentUserId(),AssignmentState.IN_PROGRESS,request.expectedVersion()+1,assignment.currentSubmissionId());
        },r->id);
    }
    @Transactional public WorkspaceRuntime.Page<CalendarEvent> calendar(Authentication auth,Instant from,Instant until,String caseId,int page,int size) {
        page(page,size);if(from==null || until==null || !from.isBefore(until) || Duration.between(from,until).compareTo(Duration.ofDays(62))>0) throw failure(422,"CALENDAR_RANGE_INVALID");
        var events=new ArrayList<CalendarEvent>();int part=0;WorkspaceRuntime.Page<TaskDto> tasks;
        do {
            tasks=runtime.tasks(auth,null,caseId==null?null:UUID.fromString(uuid(caseId)),null,from,until,part++,100);
            for(var task:tasks.content()) {
                if(!task.dueAt().isBefore(until)) continue;
                var own=task.assignments().stream().filter(a->core.isSelf(auth,a.studentUserId().toString())).toList();
                if(!own.isEmpty()) for(var a:own) events.add(new CalendarEvent(task.taskId().toString(),a.assignmentId().toString(),task.title(),task.dueAt(),a.state().name()));
                else events.add(new CalendarEvent(task.taskId().toString(),null,task.title(),task.dueAt(),null));
            }
        } while(part<tasks.totalPages());
        events.sort(Comparator.comparing(CalendarEvent::dueAt).thenComparing(CalendarEvent::taskId).thenComparing(CalendarEvent::assignmentId,Comparator.nullsFirst(String::compareTo)));
        int offset=Math.min(page*size,events.size());return new WorkspaceRuntime.Page<>(events.subList(offset,Math.min(offset+size,events.size())),page,size,events.size(),(events.size()+size-1)/size);
    }
}
