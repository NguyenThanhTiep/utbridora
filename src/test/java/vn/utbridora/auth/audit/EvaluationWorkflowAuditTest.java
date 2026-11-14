package vn.utbridora.auth.audit;

import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvaluationWorkflowAuditTest {
    @ParameterizedTest @ValueSource(strings={"EVALUATION_DRAFT_SAVED","EVALUATION_SUBMITTED"})
    void onlyIdentifiersAndVersionsCanReachAudit(String action) {
        assertEquals(new WorkflowAuditRegistry.Spec("evaluation",Set.of("caseId","templateVersion","evaluationVersion")),
                WorkflowAuditRegistry.ACTIONS.get(action));
        var repository=mock(AuditRepository.class);var audit=new AuditService(repository);
        for(String field:List.of("scores","total100","comment","template","evidenceFileIds","url")) {
            assertThrows(IllegalArgumentException.class,()->audit.workflow(UUID.randomUUID().toString(),action,
                    UUID.randomUUID().toString(),UUID.randomUUID().toString(),Map.of(field,"private")));
        }
        verifyNoInteractions(repository);
    }
}
