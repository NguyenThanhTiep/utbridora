package vn.utbridora.auth.modules.internship.infrastructure;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.Consumer;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.modules.internship.api.ReportDtos.*;
import vn.utbridora.auth.modules.internship.application.WeeklyReportPorts.UnitOfWork;
import vn.utbridora.auth.modules.internship.application.WeeklyReportService;
import vn.utbridora.auth.modules.internship.application.WeeklyReportService.MutationScope;
import vn.utbridora.auth.operations.OperationLedger;
import static vn.utbridora.auth.modules.internship.infrastructure.WeeklyReportRuntimeChecks.*;

/** Internal command boundary over T1's existing ledger. Not a controller or bean.
 * Current authentication/authorization always runs before ledger replay; expectedVersion and
 * workflow state are checked only inside the new-effect callback. Payload includes target ID.
 * Outer REQUIRED transaction owns preflight locks, effect, revisions, audit and ledger together.
 */
public final class WeeklyReportCommands {
    private final UnitOfWork transactions;
    private final WeeklyReportService reports;
    private final OperationLedger ledger;
    public WeeklyReportCommands(UnitOfWork transactions, WeeklyReportService reports, OperationLedger ledger) {
        this.transactions = available(transactions); this.reports = available(reports); this.ledger = available(ledger);
    }
    public ReportDto create(Authentication actor, UUID key, ReportInput input) {
        available(input);
        return execute(key, "T3_REPORT_CREATE", input, 201, () -> reports.authorizeCreate(actor, input),
                () -> reports.create(actor, input), result -> {
                    if (!input.caseId().equals(result.caseId()) || !input.weekStart().equals(result.weekStart())) throw unavailable();
                });
    }
    public ReportDto update(Authentication actor, UUID id, UUID key, UpdateReport input) {
        return change(actor, id, key, "T3_REPORT_UPDATE", available(input), 200, false, () -> reports.update(actor, id, input));
    }
    public ReportDto submit(Authentication actor, UUID id, UUID key, SubmitReport input) {
        return change(actor, id, key, "T3_REPORT_SUBMIT", available(input), 200, false, () -> reports.submit(actor, id, input));
    }
    public ReportDto review(Authentication actor, UUID id, UUID key, ReportReview input) {
        return change(actor, id, key, "T3_REPORT_REVIEW", available(input), 200, true, () -> reports.review(actor, id, input));
    }
    public ReportDto revision(Authentication actor, UUID id, UUID key, CreateReportRevision input) {
        return change(actor, id, key, "T3_REPORT_REVISION_CREATE", available(input), 201, false, () -> reports.createRevision(actor, id, input));
    }
    private ReportDto change(Authentication actor, UUID id, UUID key, String operation, Object input,
                              int status, boolean mentor, Supplier<ReportDto> mutation) {
        available(id);
        return execute(key, operation, Map.of("reportId", id, "request", input), status,
                () -> reports.authorizeChange(actor, id, mentor), mutation, result -> {
                    if (!id.equals(result.reportId())) throw unavailable();
                });
    }
    private ReportDto execute(UUID key, String operation, Object payload, int status,
                               Supplier<MutationScope> authorize, Supplier<ReportDto> mutation, Consumer<ReportDto> checkResult) {
        if (key == null) throw vn.utbridora.auth.operations.WorkflowValues.failure(400, "INVALID_REQUEST");
        return available(transactions.execute(session -> {
            mutationTransaction();
            var scope = available(authorize.get());
            var command = new OperationLedger.Command(available(scope.actorId()).toString(), available(scope.organizationId()).toString(),
                    operation, key.toString(), payload, status);
            var result = available(ledger.execute(command, ReportDto.class, mutation, value -> value.reportId().toString()));
            checkResult.accept(result);
            return result;
        }));
    }
}
