package vn.utbridora.auth.audit;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeeklyReportWorkflowAuditTest {
    @Test void reportActionsUseExactContractAllowlist() {
        var normal=new WorkflowAuditRegistry.Spec("weekly_report",Set.of("caseId","weekStart","revisionNo","reportVersion"));
        assertEquals(normal,WorkflowAuditRegistry.ACTIONS.get("WEEKLY_REPORT_DRAFT_SAVED"));
        assertEquals(normal,WorkflowAuditRegistry.ACTIONS.get("WEEKLY_REPORT_REVIEWED"));
        assertEquals(new WorkflowAuditRegistry.Spec("weekly_report",Set.of("caseId","weekStart","revisionNo","lateFlag")),
                WorkflowAuditRegistry.ACTIONS.get("WEEKLY_REPORT_SUBMITTED"));
        assertEquals(new WorkflowAuditRegistry.Spec("weekly_report",Set.of("caseId","weekStart","revisionNo","reportVersion","reasonCode")),
                WorkflowAuditRegistry.ACTIONS.get("WEEKLY_REPORT_REVISION_CREATED"));
    }
    @Test void freeTextNeverReachesAuditPersistence() {
        var repository=mock(AuditRepository.class);var audit=new AuditService(repository);
        for(String action:List.of("TASK_COMMENTED","WEEKLY_REPORT_DRAFT_SAVED","WEEKLY_REPORT_SUBMITTED","WEEKLY_REPORT_REVIEWED","WEEKLY_REPORT_REVISION_CREATED")) {
            for(String field:List.of("comment","completedWork","text","email","token")) {
                assertThrows(IllegalArgumentException.class,()->audit.workflow(UUID.randomUUID().toString(),action,
                        UUID.randomUUID().toString(),UUID.randomUUID().toString(),Map.of(field,"synthetic")));
            }
        }
        verifyNoInteractions(repository);
    }
}
