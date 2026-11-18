package vn.utbridora.auth.modules.internship.application;

import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.files.*;
import vn.utbridora.auth.files.FileContracts.*;
import vn.utbridora.auth.modules.internship.api.EvaluationDtos.*;
import vn.utbridora.auth.modules.internship.domain.EvaluationPolicy;
import vn.utbridora.auth.modules.internship.infrastructure.*;
import vn.utbridora.auth.modules.internship.infrastructure.WorkspaceRuntime.Page;
import vn.utbridora.auth.operations.OperationLedger;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** T3-10 owner. Auth before replay; source/Case -> root locking, history/ledger/audit in one TX. */
@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class EvaluationService {
    private final JdbcEvaluationStore store;
    private final EvaluationAccess access;
    private final OperationLedger ledger;
    private final AuditService audit;
    private final FileService files;
    public EvaluationService(JdbcEvaluationStore store, EvaluationAccess access, OperationLedger ledger, AuditService audit, FileService files) {
        this.store=store; this.access=access; this.ledger=ledger; this.audit=audit; this.files=files;
    }
    public Page<RubricTemplate> templates(Authentication auth, UUID term, int page, int size) {
        page(page,size);
        return store.templates(access.templateSchool(auth,term),term,page,size);
    }
    public EvaluationDto create(Authentication auth, String key, EvaluationInput input) {
        var scope=access.mentor(auth,input.caseId());
        return ledger.execute(command(auth,scope,"T3_EVALUATION_CREATE",key,input,201),EvaluationDto.class,() -> {
            access.holdMutation(auth,scope,Instant.now());
            if (store.idForCase(input.caseId())!=null) throw failure(409,"EVALUATION_ALREADY_EXISTS");
            if (input.scores().stream().anyMatch(s -> !s.evidenceFileIds().isEmpty())) throw failure(422,"FILE_BINDING_INVALID");
            var template=store.template(scope.schoolOrgId(),scope.termId(),input.templateId(),input.templateVersion());
            var value=new EvaluationDto(UUID.randomUUID(),scope.caseId(),template,input.scores(),
                    EvaluationPolicy.total(template,input.scores()),EvaluationState.DRAFT,1,1L,null,null);
            store.create(value,access.user(auth),Instant.now());
            audit(auth,scope,value,"EVALUATION_DRAFT_SAVED");
            return value;
        }, v -> v.evaluationId().toString());
    }
    public EvaluationDto update(Authentication auth, UUID id, String key, UpdateEvaluation input) {
        return change(auth,id,key,input,input.expectedVersion(),input.scores(),false);
    }
    public EvaluationDto submit(Authentication auth, UUID id, String key, SubmitEvaluation input) {
        return change(auth,id,key,input,input.expectedVersion(),null,true);
    }
    private EvaluationDto change(Authentication auth, UUID id, String key, Object input, long expected, List<Score> scores, boolean submit) {
        var root=requireRoot(id,false);
        var scope=access.mentor(auth,root.caseId());
        return ledger.execute(command(auth,scope,submit?"T3_EVALUATION_SUBMIT":"T3_EVALUATION_UPDATE",key,
                Map.of("evaluationId",id,"request",input),200),EvaluationDto.class,() -> {
            access.holdMutation(auth,scope,Instant.now());
            var current=store.current(requireRoot(id,true));
            var next=EvaluationPolicy.change(current,expected,submit?current.scores():scores,submit);
            checkFiles(auth,id,next.scores(),submit);
            store.append(next,expected,access.user(auth),Instant.now());
            audit(auth,scope,next,submit?"EVALUATION_SUBMITTED":"EVALUATION_DRAFT_SAVED");
            return next;
        }, v -> v.evaluationId().toString());
    }
    public EvaluationDto read(Authentication auth, UUID id, Long version) {
        var root=requireRoot(id,false);
        access.mentor(auth,root.caseId());
        root=requireRoot(id,true);
        return version==null?store.current(root):store.version(root,version);
    }
    public Page<EvaluationDto> history(Authentication auth, UUID id, int page, int size) {
        page(page,size); var root=requireRoot(id,false); access.mentor(auth,root.caseId());
        return store.history(requireRoot(id,true),page,size);
    }
    public EvaluationDto forCase(Authentication auth, UUID caseId) {
        access.mentor(auth,caseId);
        UUID id=store.idForCase(caseId);
        return id==null?null:store.current(requireRoot(id,true));
    }
    private JdbcEvaluationStore.Root requireRoot(UUID id, boolean lock) {
        var root=store.find(id,lock);
        if (root==null) throw failure(404,"RESOURCE_NOT_FOUND");
        return root;
    }
    private void checkFiles(Authentication auth, UUID id, List<Score> scores, boolean clean) {
        var resource=new ResourceRef(ResourceType.EVALUATION,id.toString());
        var actor=ActorContext.fromAuthenticatedWorkflow(auth,null);
        for (var fileId : scores.stream().flatMap(s -> s.evidenceFileIds().stream()).distinct().sorted().toList()) {
            var file=clean?files.requireCleanBound(fileId.toString(),resource,FilePurpose.EVIDENCE,actor):files.metadata(auth,fileId.toString());
            if (!resource.equals(file.resource()) || file.purpose()!=FilePurpose.EVIDENCE || file.deletedAt()!=null)
                throw failure(422,"FILE_BINDING_INVALID");
            if (file.scanStatus()!=FileMetadata.ScanStatus.CLEAN && file.scanStatus()!=FileMetadata.ScanStatus.PENDING)
                throw failure(409,"FILE_NOT_CLEAN");
        }
    }
    private OperationLedger.Command command(Authentication auth, CaseRuntime.CaseRef scope, String operation, String key, Object input, int status) {
        return new OperationLedger.Command(actor(auth),scope.schoolOrgId().toString(),operation,uuid(key),input,status);
    }
    private void audit(Authentication auth, CaseRuntime.CaseRef scope, EvaluationDto v, String action) {
        audit.workflow(actor(auth),action,v.evaluationId().toString(),scope.schoolOrgId().toString(),
                Map.of("caseId",scope.caseId().toString(),"templateVersion",v.template().templateVersion(),"evaluationVersion",v.version()));
    }
}
