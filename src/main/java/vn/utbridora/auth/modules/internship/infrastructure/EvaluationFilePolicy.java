package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.files.FileContracts.*;
import vn.utbridora.auth.files.FileResourcePolicy;
import vn.utbridora.auth.modules.internship.api.EvaluationDtos.EvaluationState;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** EVALUATION owner policy. Student/HR reads remain closed until T3-11 supplies its publication gate. */
@Component
public class EvaluationFilePolicy implements FileResourcePolicy {
    private final JdbcEvaluationStore store;
    private final EvaluationAccess access;
    public EvaluationFilePolicy(JdbcEvaluationStore store, EvaluationAccess access) { this.store=store; this.access=access; }
    @Override public ResourceType resourceType() { return ResourceType.EVALUATION; }
    @Override public Grant check(ActorContext actor, ResourceRef resource, FilePurpose purpose, Action action, Instant now) {
        if (purpose != FilePurpose.EVIDENCE) return Grant.deny();
        var root=store.find(UUID.fromString(resource.resourceId()),false);
        if (root==null) return Grant.deny();
        var scope=access.mentor(actor.authentication(),root.caseId());
        if (action==Action.UPLOAD) {
            access.holdMutation(actor.authentication(),scope,now);
            root=store.find(root.id(),true);
            if (store.current(root).state()!=EvaluationState.DRAFT) throw failure(409,"STATE_CONFLICT");
        }
        return Grant.owner(root.currentVersion(),scope.schoolOrgId().toString(),true);
    }
    @Override public boolean mayReadFile(ActorContext actor, String fileId, String uploadedBy, ResourceRef resource, FilePurpose purpose) {
        // A replacement Mentor can read attached evidence, but not another Mentor's unbound upload.
        return actor.actorId().equals(uploadedBy) || store.attached(UUID.fromString(resource.resourceId()),fileId);
    }
}
