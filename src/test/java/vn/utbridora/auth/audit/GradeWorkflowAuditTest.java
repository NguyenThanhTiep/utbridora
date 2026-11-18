package vn.utbridora.auth.audit;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GradeWorkflowAuditTest {
    @Test void exactContractMetadataDoesNotIncludeScoresOrComments(){
        assertEquals(new WorkflowAuditRegistry.Spec("evaluation",Set.of("caseId","evaluationVersion","decisionRef")),WorkflowAuditRegistry.ACTIONS.get("EVALUATION_PUBLISHED"));
        assertEquals(new WorkflowAuditRegistry.Spec("evaluation",Set.of("caseId","previousEvaluationId","revisionNo","decisionRef")),WorkflowAuditRegistry.ACTIONS.get("EVALUATION_REVISION_CREATED"));
        assertEquals(new WorkflowAuditRegistry.Spec("grade_visibility",Set.of("enterpriseOrgId","caseId","enabled","version")),WorkflowAuditRegistry.ACTIONS.get("GRADE_VISIBILITY_CHANGED"));
        assertEquals(new WorkflowAuditRegistry.Spec("school_result_policy",Set.of("schoolOrgId","termId","enabled","version","decisionRef")),WorkflowAuditRegistry.ACTIONS.get("SCHOOL_RESULT_VISIBILITY_CHANGED"));
        var repository=mock(AuditRepository.class);var audit=new AuditService(repository);
        for(String action:List.of("EVALUATION_PUBLISHED","EVALUATION_REVISION_CREATED","GRADE_VISIBILITY_CHANGED","SCHOOL_RESULT_VISIBILITY_CHANGED"))
            for(String field:List.of("scores","total100","comment","token","evidenceFileIds"))
                assertThrows(IllegalArgumentException.class,()->audit.workflow(UUID.randomUUID().toString(),action,UUID.randomUUID().toString(),UUID.randomUUID().toString(),Map.of(field,"private")));
        verifyNoInteractions(repository);
    }
}
