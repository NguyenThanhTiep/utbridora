package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.files.FileContracts.*;
import vn.utbridora.auth.files.FileResourcePolicy;
import vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** Reuses T3's current Placement/Case gate. Read history does not reuse the upload state gate. */
@Component
public class TaskAssignmentFilePolicy implements FileResourcePolicy {
    private record Assignment(String task,String caseId,String state,long version) {}
    private final JdbcTemplate jdbc;private final AuthorizationPolicy core;
    private final WorkspaceRuntime runtime;private final CaseRuntime cases;private final JdbcWorkspaceSession session;
    public TaskAssignmentFilePolicy(JdbcTemplate jdbc,AuthorizationPolicy core,WorkspaceRuntime runtime,CaseRuntime cases,JdbcWorkspaceSession session) {
        this.jdbc=jdbc;this.core=core;this.runtime=runtime;this.cases=cases;this.session=session;
    }
    @Override public ResourceType resourceType() {return ResourceType.TASK_ASSIGNMENT;}
    @Override public Grant check(ActorContext actor,ResourceRef resource,FilePurpose purpose,Action action,Instant now) {
        if(purpose!=FilePurpose.SUBMISSION) return Grant.deny();
        var row=jdbc.query("SELECT task_id,case_id,state,version FROM internship_task_assignments WHERE id=? AND student_user_id=?",
            (r,n)->new Assignment(r.getString(1),r.getString(2),r.getString(3),r.getLong(4)),resource.resourceId(),actor.actorId()).stream().findFirst().orElse(null);
        if(row==null) return Grant.deny();
        try {
            var task=runtime.readTask(actor.authentication(),UUID.fromString(row.task()));var caseId=UUID.fromString(row.caseId());var c=cases.read(caseId);
            if(!core.holdOrganization(actor.authentication(),c.schoolOrgId().toString(),Role.STUDENT,"internship:student:submission_write")) return Grant.deny();
            if(action==Action.UPLOAD) {
                var group=session.group(task.groupId());var cohort=session.cohort(group.cohortId());
                cases.requireTaskParticipant(actor.authentication(),cohort,group,caseId,now);
            }
            // Match T3's mutation lock order: Placement/Case first, then the current assignment.
            var current=jdbc.query("SELECT task_id,case_id,state,version FROM internship_task_assignments WHERE id=? AND student_user_id=? FOR SHARE",
                (r,n)->new Assignment(r.getString(1),r.getString(2),r.getString(3),r.getLong(4)),resource.resourceId(),actor.actorId()).stream().findFirst().orElse(null);
            if(current==null || !current.task().equals(row.task()) || !current.caseId().equals(row.caseId())) return Grant.deny();
            if(action==Action.UPLOAD && !current.state().equals("IN_PROGRESS")) throw failure(409,"STATE_CONFLICT");
            return Grant.owner(current.version(),c.schoolOrgId().toString(),false);
        } catch(WorkspaceRuleException denied) {
            if(denied.kind()==WorkspaceRuleException.Kind.STATE_CONFLICT) throw failure(409,"STATE_CONFLICT");
            if(denied.kind()==WorkspaceRuleException.Kind.UNAVAILABLE) throw failure(503,"DEPENDENCY_UNAVAILABLE");
            return Grant.deny();
        }
    }
}
