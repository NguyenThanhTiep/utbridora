package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.utbridora.auth.audit.*;
import vn.utbridora.auth.modules.internship.application.WeeklyReportPorts.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeeklyReportAuditAdapterTest extends WeeklyReportAdapterTestSupport {
    private final AuditService audit = mock(AuditService.class);
    private final WeeklyReportAuditAdapter adapter = new WeeklyReportAuditAdapter(audit);
    private AuditEvent event(Action action) {
        return new AuditEvent(action, student, UUID.randomUUID(), caseId, LocalDate.parse("2026-11-02"),
                2, 4, false, "FIX_REPORT", school);
    }
    @Test void missingWeeklyActionsFailClosedAndNeverFallBackToTaskCommentOrOtherActions() {
        Set<String> runtime = new HashSet<>(); for (AuditAction action : AuditAction.values()) runtime.add(action.name());
        for (Action action : Action.values()) {
            var event = event(action);
            if (!runtime.contains(action.name())) {
                code(503, "DEPENDENCY_UNAVAILABLE", () -> inTransaction(() -> { adapter.append(event); return true; }));
                verifyNoInteractions(audit);
            } else {
                inTransaction(() -> { adapter.append(event); return true; });
                verify(audit).workflow(student.toString(), action.name(), event.reportId().toString(), school.toString(), event.metadata());
                clearInvocations(audit);
            }
        }
    }
    @Test void exactMetadataDoesNotLeakWorkOrReviewComment() {
        assertEquals(Set.of("caseId", "weekStart", "revisionNo", "reportVersion"), event(Action.WEEKLY_REPORT_REVIEWED).metadata().keySet());
        assertEquals(Set.of("caseId", "weekStart", "revisionNo", "lateFlag"), event(Action.WEEKLY_REPORT_SUBMITTED).metadata().keySet());
        assertEquals(Set.of("caseId", "weekStart", "revisionNo", "reportVersion", "reasonCode"), event(Action.WEEKLY_REPORT_REVISION_CREATED).metadata().keySet());
        assertThrows(IllegalArgumentException.class, () -> new AuditEvent(Action.WEEKLY_REPORT_REVISION_CREATED, student, UUID.randomUUID(), caseId,
                LocalDate.parse("2026-11-02"), 2, 4, false, null, school));
    }
    @Test void auditCannotRunOutsideDomainTransaction() {
        code(503, "DEPENDENCY_UNAVAILABLE", () -> adapter.append(event(Action.WEEKLY_REPORT_DRAFT_SAVED)));
        verifyNoInteractions(audit);
    }
}
