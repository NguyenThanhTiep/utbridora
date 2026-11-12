package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.LocalDate;
import java.util.*;
import java.util.function.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.utbridora.auth.modules.internship.api.ReportDtos.*;
import vn.utbridora.auth.modules.internship.application.*;
import vn.utbridora.auth.modules.internship.domain.WeeklyReport;
import vn.utbridora.auth.operations.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Tests delegation to the real common ledger API, not a replacement implementation of its DB/cache. */
class WeeklyReportCommandsTest extends WeeklyReportAdapterTestSupport {
    private final WeeklyReportService reports = mock(WeeklyReportService.class);
    private final OperationLedger ledger = mock(OperationLedger.class);
    private final Authentication actor = auth(student);
    private final UUID reportId = UUID.randomUUID(), key = UUID.randomUUID();
    private final ReportInput input = new ReportInput(caseId, LocalDate.parse("2026-11-02"), "Work", "", "Plan", List.of());
    private final ReportDto dto = ReportDto.from(WeeklyReport.create(reportId, caseId, input.weekStart(), scope.startDate(), scope.endDate(), input.content()),
            WeeklyReport.create(reportId, caseId, input.weekStart(), scope.startDate(), scope.endDate(), input.content()).current());
    private WeeklyReportCommands commands;
    @BeforeEach void setup() {
        var existing = new TransactionalWorkspaceUnitOfWork(manager, () -> mock(WorkspacePorts.Session.class));
        var work = new WeeklyReportUnitOfWork(existing, () -> mock(WeeklyReportPorts.Session.class));
        commands = new WeeklyReportCommands(work, reports, ledger);
        when(reports.authorizeCreate(actor, input)).thenReturn(new WeeklyReportService.MutationScope(student, school));
        when(reports.authorizeChange(actor, reportId, false)).thenReturn(new WeeklyReportService.MutationScope(student, school));
        when(reports.authorizeChange(actor, reportId, true)).thenReturn(new WeeklyReportService.MutationScope(student, enterprise));
    }
    private void executeNewEffect() {
        when(ledger.execute(any(), eq(ReportDto.class), any(), any())).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            ReportDto result = ((Supplier<ReportDto>) call.getArgument(2)).get();
            assertEquals(reportId.toString(), ((Function<ReportDto, String>) call.getArgument(3)).apply(result));
            return result;
        });
    }
    @Test void createAuthorizesBeforeLedgerAndExecutesEffectWithinTheSameBoundary() {
        executeNewEffect(); when(reports.create(actor, input)).thenReturn(dto);
        assertSame(dto, commands.create(actor, key, input));
        var ordered = inOrder(reports, ledger);
        ordered.verify(reports).authorizeCreate(actor, input);
        var captured = ArgumentCaptor.forClass(OperationLedger.Command.class);
        ordered.verify(ledger).execute(captured.capture(), eq(ReportDto.class), any(), any());
        ordered.verify(reports).create(actor, input);
        assertEquals("T3_REPORT_CREATE", captured.getValue().operationCode());
        assertEquals(student.toString(), captured.getValue().actorId());
        assertEquals(school.toString(), captured.getValue().organizationId());
        assertEquals(key.toString(), captured.getValue().key()); assertEquals(201, captured.getValue().responseStatus());
        assertEquals(1, manager.commits);
    }
    @Test void cachedReplayStillChecksCurrentAuthorityButDoesNotRepeatStateChecksOrEffect() {
        when(ledger.execute(any(), eq(ReportDto.class), any(), any())).thenReturn(dto);
        assertSame(dto, commands.submit(actor, reportId, key, new SubmitReport(1L)));
        var ordered = inOrder(reports, ledger);
        ordered.verify(reports).authorizeChange(actor, reportId, false);
        ordered.verify(ledger).execute(any(), eq(ReportDto.class), any(), any());
        verify(reports, never()).submit(any(), any(), any());
    }
    @Test void revokedAuthorityCannotReadCachedPrivateResult() {
        when(reports.authorizeChange(actor, reportId, false)).thenThrow(new OperationFailure(404, "RESOURCE_NOT_FOUND", "Denied", null));
        code(404, "RESOURCE_NOT_FOUND", () -> commands.submit(actor, reportId, key, new SubmitReport(1L)));
        verifyNoInteractions(ledger); assertEquals(1, manager.rollbacks);
    }
    @Test void everyCommandUsesItsOwnCodeStatusAndServerScopeAndIncludesTargetIdInHashPayload() {
        executeNewEffect();
        var update = new UpdateReport(1L, "Work", "", "Next", List.of());
        var submit = new SubmitReport(2L); var review = new ReportReview(3L, "Good"); var revision = new CreateReportRevision(4L, "FIX");
        when(reports.update(actor, reportId, update)).thenReturn(dto);
        when(reports.submit(actor, reportId, submit)).thenReturn(dto);
        when(reports.review(actor, reportId, review)).thenReturn(dto);
        when(reports.createRevision(actor, reportId, revision)).thenReturn(dto);
        commands.update(actor, reportId, key, update); commands.submit(actor, reportId, key, submit);
        commands.review(actor, reportId, key, review); commands.revision(actor, reportId, key, revision);
        var captured = ArgumentCaptor.forClass(OperationLedger.Command.class);
        verify(ledger, times(4)).execute(captured.capture(), eq(ReportDto.class), any(), any());
        var all = captured.getAllValues();
        assertEquals(List.of("T3_REPORT_UPDATE", "T3_REPORT_SUBMIT", "T3_REPORT_REVIEW", "T3_REPORT_REVISION_CREATE"), all.stream().map(OperationLedger.Command::operationCode).toList());
        assertEquals(List.of(200, 200, 200, 201), all.stream().map(OperationLedger.Command::responseStatus).toList());
        assertEquals(enterprise.toString(), all.get(2).organizationId());
        for (var command : all) assertEquals(reportId, ((Map<?, ?>) command.payload()).get("reportId"));
        var changedTarget = new OperationLedger.Command(student.toString(), school.toString(), "T3_REPORT_UPDATE", key.toString(),
                Map.of("reportId", UUID.randomUUID(), "request", update), 200);
        assertNotEquals(OperationLedger.payloadHash(all.getFirst().payload()), OperationLedger.payloadHash(changedTarget.payload()));
    }
    @Test void ledgerOrMutationFailurePropagatesAndRollsBackWithoutSuccess() {
        executeNewEffect(); when(reports.create(actor, input)).thenThrow(new OperationFailure(503, "DEPENDENCY_UNAVAILABLE", "Missing audit", null));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> commands.create(actor, key, input));
        assertEquals(1, manager.rollbacks); assertEquals(0, manager.commits);
    }
    @Test void missingIdempotencyKeyCannotEnterTransactionOrLedger() {
        code(400, "INVALID_REQUEST", () -> commands.create(actor, null, input));
        verifyNoInteractions(ledger); assertEquals(0, manager.begins);
    }
    @Test void readOnlyOuterTransactionCannotRunAMutationOrReplayCommand() {
        when(ledger.execute(any(), eq(ReportDto.class), any(), any())).thenReturn(dto);
        code(503, "DEPENDENCY_UNAVAILABLE", () -> inReadOnlyTransaction(() -> commands.create(actor, key, input)));
        verifyNoInteractions(reports, ledger);
    }
    @Test void cachedResponseForAnotherReportIsRejectedBeforeCommit() {
        var other = WeeklyReport.create(UUID.randomUUID(), caseId, input.weekStart(), scope.startDate(), scope.endDate(), input.content());
        when(ledger.execute(any(), eq(ReportDto.class), any(), any())).thenReturn(ReportDto.from(other, other.current()));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> commands.submit(actor, reportId, key, new SubmitReport(1L)));
        verify(reports, never()).submit(any(), any(), any());
        assertEquals(0, manager.commits); assertEquals(1, manager.rollbacks);
    }
    @Test void createResultMustMatchRequestedCaseAndWeek() {
        for (boolean wrongCase : List.of(true, false)) {
            var other = WeeklyReport.create(UUID.randomUUID(), wrongCase ? UUID.randomUUID() : caseId,
                    wrongCase ? input.weekStart() : input.weekStart().plusWeeks(1), scope.startDate(), scope.endDate(), input.content());
            when(ledger.execute(any(), eq(ReportDto.class), any(), any())).thenReturn(ReportDto.from(other, other.current()));
            code(503, "DEPENDENCY_UNAVAILABLE", () -> commands.create(actor, key, input));
        }
        assertEquals(0, manager.commits); assertEquals(2, manager.rollbacks);
    }
}
