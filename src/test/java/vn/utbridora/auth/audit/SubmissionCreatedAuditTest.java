package vn.utbridora.auth.audit;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import vn.utbridora.auth.operations.OperationFailure;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The one Core registry extension explicitly authorized by Lead for T3-07 M2B. */
class SubmissionCreatedAuditTest {
    @Test void approvedSpecIsExactAndDoesNotEnableReviewOrTaskComment() {
        assertEquals(new WorkflowAuditRegistry.Spec("submission",Set.of("assignmentId","versionNo","fileId","fileIds","lateFlag")),
                WorkflowAuditRegistry.ACTIONS.get("SUBMISSION_CREATED"));
        assertFalse(WorkflowAuditRegistry.ACTIONS.containsKey("SUBMISSION_REVIEWED"));
        assertFalse(WorkflowAuditRegistry.ACTIONS.containsKey("TASK_COMMENTED"));
    }
    @Test void realAuditServiceStoresSubmissionResourceAndSafeMetadata() {
        var repository=repository();var service=new AuditService(repository);
        service.workflow(id(1),"SUBMISSION_CREATED",id(2),id(3),Map.of("assignmentId",id(4),"versionNo",1,"fileIds",List.of(),"lateFlag",false));
        var event=ArgumentCaptor.forClass(AuditLog.class);verify(repository).save(event.capture());verify(repository).flush();
        assertEquals("submission",event.getValue().resourceType);assertEquals(id(2),event.getValue().resourceId);
        assertEquals(id(3),event.getValue().organizationId);assertEquals("SUCCESS",event.getValue().result);
        assertFalse(event.getValue().metadataJson.contains("url"));assertFalse(event.getValue().metadataJson.contains("note"));
    }
    @ParameterizedTest @ValueSource(strings={"url","note","comment"})
    void freeTextIsRejectedBeforeSaving(String field) {
        var repository=repository();var service=new AuditService(repository);
        assertThrows(IllegalArgumentException.class,()->service.workflow(id(1),"SUBMISSION_CREATED",id(2),id(3),Map.of(field,"not permitted")));
        verifyNoInteractions(repository);
    }
    @Test void mandatoryFlushFailurePropagatesForTheDomainTransactionToRollback() {
        var repository=repository();var service=new AuditService(repository);
        doThrow(new DataAccessResourceFailureException("synthetic")).when(repository).flush();
        var error=assertThrows(OperationFailure.class,()->service.workflow(id(1),"SUBMISSION_CREATED",id(2),id(3),Map.of("versionNo",1)));
        assertEquals(500,error.status);assertEquals("AUDIT_WRITE_FAILED",error.code);
    }
    private static AuditRepository repository() {
        var repository=mock(AuditRepository.class);when(repository.save(any(AuditLog.class))).thenAnswer(i->i.getArgument(0));return repository;
    }
    private static String id(int i) { return new UUID(0,i).toString(); }
}
