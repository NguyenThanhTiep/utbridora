package vn.utbridora.auth.modules.internship.application;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.modules.internship.domain.WeeklyReport;
import vn.utbridora.auth.modules.internship.domain.WeeklyReport.State;
import static vn.utbridora.auth.modules.internship.domain.WeeklyReportPolicy.required;

/** T3-08 consumer ports, not new public/wire contracts. No production providers are registered here.
 * Before HTTP exposure, integrate C.0 OperationLedger (current authority before cached replay).
 * All ports must join the same transaction. Lock authoritative Placement, then Case, then report;
 * keep account/membership/assignment/file decisions stable until commit. Audit failure rolls back
 * root/revisions/review/file refs and ledger effect together. Never write T5 repositories from T3.
 */
public final class WeeklyReportPorts {
    private WeeklyReportPorts() {}
    public interface UnitOfWork {
        <T> T execute(Function<Session, T> operation);
    }
    public interface Session {
        /** Lookup only; do not take a report write lock before authoritative source locks. */
        WeeklyReport find(UUID reportId);
        /** Enforce UNIQUE(caseId,weekStart) atomically, not just a preceding existence query. */
        boolean insertIfAbsent(WeeklyReport report);
        /** CAS root version, retain old revisions and atomically append/replace the current revision. */
        boolean replace(WeeklyReport report, long expectedVersion);
        /** Current revision per root, filter state before paging; sort weekStart DESC,reportId ASC. */
        PageSlice list(UUID caseId, State state, int page, int size);
    }
    public record PageSlice(List<WeeklyReport> content, long totalElements) {
        public PageSlice {
            content = List.copyOf(required(content, "content"));
            if (totalElements < 0) throw new IllegalArgumentException("Invalid totalElements");
        }
    }
    /** Server-owned data, never accepted from a report request or an old lifecycle event. */
    public record CaseScope(UUID caseId, UUID placementId, UUID studentUserId, UUID schoolOrgId,
                            UUID enterpriseOrgId, UUID termId, String caseState, String placementState,
                            Instant effectiveAt, Instant approvedAt, LocalDate startDate, LocalDate endDate,
                            long caseVersion, long placementVersion) {
        public CaseScope {
            required(caseId, "caseId"); required(placementId, "placementId"); required(studentUserId, "studentUserId");
            required(schoolOrgId, "schoolOrgId"); required(enterpriseOrgId, "enterpriseOrgId"); required(termId, "termId");
            required(caseState, "caseState"); required(placementState, "placementState");
            required(startDate, "startDate"); required(endDate, "endDate");
            if (startDate.isAfter(endDate)) throw new IllegalArgumentException("Invalid Placement dates");
            vn.utbridora.auth.modules.internship.domain.WeeklyReportPolicy.version(caseVersion);
            vn.utbridora.auth.modules.internship.domain.WeeklyReportPolicy.version(placementVersion);
        }
    }
    public interface Cases {
        /** Read Case and T5 PlacementReadPort; verify identity/scope matches both sources.
         * A read snapshot alone does not establish a stable mutation guard. */
        CaseScope current(UUID caseId);
        /** Hold authoritative Case version/state stable until commit, after the Placement lock.
         * Implement explicitly; a read-only adapter must never silently authorize mutations. */
        default void holdForMutation(CaseScope scope) {
            throw vn.utbridora.auth.operations.WorkflowValues.failure(503, "DEPENDENCY_UNAVAILABLE");
        }
    }
    public interface Authority {
        /** Resolve server identity and revalidate current account/securityVersion; never trust actor.getName(). */
        UUID currentUser(Authentication actor);
        /** Exact permission + current org membership/delegation. ADMIN is not a business bypass. */
        void requirePermission(Authentication actor, CaseScope scope, String permission, Instant now);
    }
    public interface Assignments {
        /** Current MENTOR for this Case/current group, including parent chain/time/revoke/handover.
         * HR or merely holding task_write must return false. */
        boolean isCurrentMentor(Authentication actor, CaseScope scope, Instant now);
        /** Authorized HR/assignment or school delegation, including scope/time/parent/revoke checks. */
        boolean canRead(Authentication actor, CaseScope scope, Instant now);
    }
    public interface Files {
        /** resourceType/purpose WEEKLY_REPORT, stable report root ID and owner must match.
         * Draft permits PENDING/CLEAN; submit requires every file CLEAN. Invalid binding ->422.
         * Missing scanner/provider ->503. These checks do not grant download permission. */
        void requireBound(UUID studentUserId, UUID reportId, List<UUID> fileIds, boolean requireClean);
    }
    public enum Action {
        WEEKLY_REPORT_DRAFT_SAVED, WEEKLY_REPORT_REVISION_CREATED, WEEKLY_REPORT_SUBMITTED, WEEKLY_REPORT_REVIEWED
    }
    /** Exact E.3 metadata only; no work text, reviewer comment, storage path or other PII. */
    public record AuditEvent(Action action, UUID actorId, UUID reportId, UUID caseId, LocalDate weekStart,
                             int revisionNo, long reportVersion, boolean lateFlag, String reasonCode, UUID schoolOrgId) {
        public AuditEvent {
            required(action, "action"); required(actorId, "actorId"); required(reportId, "reportId");
            required(caseId, "caseId"); required(weekStart, "weekStart"); required(schoolOrgId, "schoolOrgId");
            if (revisionNo < 1) throw new IllegalArgumentException("Invalid audit revision");
            vn.utbridora.auth.modules.internship.domain.WeeklyReportPolicy.version(reportVersion);
            if (action == Action.WEEKLY_REPORT_REVISION_CREATED) {
                vn.utbridora.auth.modules.internship.domain.WeeklyReportPolicy.reason(reasonCode);
            }
        }
        public Map<String, Object> metadata() {
            var fields = new LinkedHashMap<String, Object>();
            fields.put("caseId", caseId.toString()); fields.put("weekStart", weekStart.toString());
            fields.put("revisionNo", revisionNo);
            if (action == Action.WEEKLY_REPORT_SUBMITTED) fields.put("lateFlag", lateFlag);
            else fields.put("reportVersion", reportVersion);
            if (action == Action.WEEKLY_REPORT_REVISION_CREATED) fields.put("reasonCode", reasonCode);
            return Map.copyOf(fields);
        }
    }
    public interface Audit {
        /** Append SUCCESS weekly_report via T1 AuditService in the caller's transaction or fail. */
        void append(AuditEvent event);
    }
}
