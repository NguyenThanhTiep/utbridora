package vn.utbridora.auth.modules.internship.infrastructure;

import java.util.UUID;
import vn.utbridora.auth.contracts.placement.PlacementReadPort;
import vn.utbridora.auth.modules.internship.application.WeeklyReportPorts.*;
import static vn.utbridora.auth.modules.internship.infrastructure.WeeklyReportRuntimeChecks.*;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** Composes public CaseRuntime reads with T5's authoritative, transaction-held Placement read.
 * Does not copy Task/Cohort guards or query/update DEV05/T5 tables directly. No bean is registered.
 */
public final class WeeklyReportCaseAdapter implements Cases {
    /** Consumer requirement for DEV05/Lead review. Existing Task-specific private current() is not
     * callable safely for an independent Report. Owner must hold Case/current version until commit
     * and return current data; do not bump membership version or invent a Task/Cohort. */
    public interface CaseFence {
        CaseRuntime.CaseRef hold(UUID caseId, long expectedVersion);
    }
    private final CaseRuntime cases;
    private final PlacementReadPort placements;
    private final CaseFence fence;
    public WeeklyReportCaseAdapter(CaseRuntime cases, PlacementReadPort placements, CaseFence fence) {
        this.cases = available(cases); this.placements = available(placements); this.fence = fence;
    }
    @Override public CaseScope current(UUID id) {
        transaction();
        var first = workspace(() -> available(cases.read(available(id))));
        available(first.placementId()); available(first.studentUserId()); available(first.schoolOrgId());
        available(first.enterpriseOrgId()); available(first.termId()); available(first.state());
        if (!id.equals(first.caseId())) throw unavailable();
        var source = available(placements.readCurrentForGuard(first.placementId().toString()))
                .orElseThrow(WeeklyReportRuntimeChecks::unavailable);
        // A second snapshot catches observed drift; it is deliberately NOT presented as a write fence.
        var current = workspace(() -> available(cases.read(id)));
        if (!first.equals(current)) throw failure(409, "VERSION_CONFLICT");
        if (!first.placementId().toString().equals(source.placementId())
                || !first.studentUserId().toString().equals(source.studentUserId())
                || !first.schoolOrgId().toString().equals(source.schoolOrgId())
                || !first.enterpriseOrgId().toString().equals(source.enterpriseOrgId())
                || !first.termId().toString().equals(source.termId())) throw failure(404, "RESOURCE_NOT_FOUND");
        try {
            return new CaseScope(first.caseId(), first.placementId(), first.studentUserId(), first.schoolOrgId(),
                    first.enterpriseOrgId(), first.termId(), first.state(), source.state(), source.effectiveAt(),
                    source.approvedAt(), source.startDate(), source.endDate(), first.version(), source.version());
        } catch (IllegalArgumentException invalidProvider) { throw unavailable(); }
    }
    @Override public void holdForMutation(CaseScope scope) {
        mutationTransaction(); available(scope);
        var locked = workspace(() -> available(available(fence).hold(scope.caseId(), scope.caseVersion())));
        if (!scope.caseId().equals(locked.caseId()) || !scope.placementId().equals(locked.placementId())
                || !scope.studentUserId().equals(locked.studentUserId()) || !scope.schoolOrgId().equals(locked.schoolOrgId())
                || !scope.enterpriseOrgId().equals(locked.enterpriseOrgId()) || !scope.termId().equals(locked.termId())) {
            throw failure(404, "RESOURCE_NOT_FOUND");
        }
        if (scope.caseVersion() != locked.version() || !scope.caseState().equals(locked.state())) {
            throw failure(409, "VERSION_CONFLICT");
        }
    }
}
