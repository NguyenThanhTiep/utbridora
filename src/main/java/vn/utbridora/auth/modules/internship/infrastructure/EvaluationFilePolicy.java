package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.files.FileContracts.*;
import vn.utbridora.auth.files.FileResourcePolicy;
import vn.utbridora.auth.modules.internship.api.EvaluationDtos.EvaluationState;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** EVALUATION evidence: Mentor scope, School delegation, or Student published-result gates. HR reads remain denied. */
@Component
public class EvaluationFilePolicy implements FileResourcePolicy {
    private final JdbcEvaluationStore store;
    private final EvaluationAccess access;
    private final GradeAccess gradeAccess;
    private final vn.utbridora.auth.modules.internship.application.GradeService grades;
    public EvaluationFilePolicy(JdbcEvaluationStore store, EvaluationAccess access,GradeAccess gradeAccess,vn.utbridora.auth.modules.internship.application.GradeService grades) { this.store=store; this.access=access;this.gradeAccess=gradeAccess;this.grades=grades; }
    @Override public ResourceType resourceType() { return ResourceType.EVALUATION; }
    @Override public Grant check(ActorContext actor, ResourceRef resource, FilePurpose purpose, Action action, Instant now) {
        if (purpose != FilePurpose.EVIDENCE) return Grant.deny();
        var root=store.find(UUID.fromString(resource.resourceId()),false);
        if (root==null) return Grant.deny();
        if(action!=Action.UPLOAD){
            var c=gradeAccess.caseRef(root.caseId());
            if(actor.actorId().equals(c.studentUserId().toString())){
                var result=grades.result(actor.authentication(),root.caseId());
                if(result.evaluation()==null)return Grant.deny();
                return Grant.owner(result.evaluation().version(),c.schoolOrgId().toString(),true);
            }
            if(gradeAccess.schoolReader(actor.authentication(),c.schoolOrgId())){
                grades.schoolRead(actor.authentication(),root.id());
                return Grant.owner(root.currentVersion(),c.schoolOrgId().toString(),true);
            }
        }
        var scope=access.mentor(actor.authentication(),root.caseId());
        if (action==Action.UPLOAD) {
            access.holdMutation(actor.authentication(),scope,now);
            root=store.find(root.id(),true);
            if (store.current(root).state()!=EvaluationState.DRAFT) throw failure(409,"STATE_CONFLICT");
        }
        return Grant.owner(root.currentVersion(),scope.schoolOrgId().toString(),true);
    }
    @Override public boolean mayReadFile(ActorContext actor, String fileId, String uploadedBy, ResourceRef resource, FilePurpose purpose) {
        var root=store.find(UUID.fromString(resource.resourceId()),false);
        if(root==null)return false;
        var c=gradeAccess.caseRef(root.caseId());
        if(actor.actorId().equals(c.studentUserId().toString()))return grades.studentEvidence(actor.authentication(),c.caseId(),fileId);
        // A replacement Mentor can read attached evidence, but not another Mentor's unbound upload.
        return actor.actorId().equals(uploadedBy) || store.attached(UUID.fromString(resource.resourceId()),fileId);
    }
}
