package vn.utbridora.auth.audit;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class AdvisoryAuditTest {
    String id="a17e2026-1121-4000-8000-000000000001";
    AuditRepository repository(){var repo=mock(AuditRepository.class);when(repo.save(any())).thenAnswer(i->i.getArgument(0));return repo;}
    @Test void newAuditIsPersistedWithSystemActorAndSafeMetadata() {var repo=repository();var service=new AuditService(repo);service.workflow(null,"AI_ADVISORY_COMPLETED",id,id,Map.of("matchRunId",id,"state","READY","eligibleCount",2,"scoredCount",1,"policyVersion","semantic-desc-canonical-tie-v1","modelRevision","614241f622f53c4eeff9890bdc4f31cfecc418b3"));var c=ArgumentCaptor.forClass(AuditLog.class);verify(repo).save(c.capture());verify(repo).flush();assertNull(c.getValue().actorId);assertEquals("matching_advisory",c.getValue().resourceType);assertFalse(c.getValue().metadataJson.contains("semanticScore"));}
    @Test void sourceAndScoreMetadataAreRejectedBeforeSaving() {var repo=repository();var service=new AuditService(repo);for(String field:List.of("text","semanticScore","query","studentUserIds","inputHash"))assertThrows(IllegalArgumentException.class,()->service.workflow(id,"AI_ADVISORY_VIEWED",id,id,Map.of(field,"sensitive")));verifyNoInteractions(repo);}
    @Test void failureUsesFailureOutcomeWithoutWeakeningUnknownActionGuard() {var repo=repository();var service=new AuditService(repo);service.workflowFailure(null,"AI_ADVISORY_FAILED",id,id,Map.of("matchRunId",id,"failureCode","WORK_TIMEOUT","attempt",3));var c=ArgumentCaptor.forClass(AuditLog.class);verify(repo).save(c.capture());assertEquals("FAILURE",c.getValue().result);assertThrows(IllegalArgumentException.class,()->service.write(id,"AI_ADVISORY_UNKNOWN","matching_advisory",id,"SUCCESS"));}
}
