package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.files.FileContracts.*;
import vn.utbridora.auth.files.FileResourcePolicy;
import vn.utbridora.auth.modules.internship.domain.InternshipActivityPolicy;
import vn.utbridora.auth.modules.internship.domain.WeeklyReport;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** File ownership is bound to the report root; shared reads require a persisted revision reference. */
public final class WeeklyReportFilePolicy implements FileResourcePolicy {
    private final JdbcWeeklyReportSession reports;
    private final WeeklyReportCaseAdapter cases;
    private final WeeklyReportAccessAdapter access;
    private final JdbcTemplate jdbc;
    public WeeklyReportFilePolicy(JdbcWeeklyReportSession reports, WeeklyReportCaseAdapter cases,
                                  WeeklyReportAccessAdapter access, JdbcTemplate jdbc) {
        this.reports = reports; this.cases = cases; this.access = access; this.jdbc = jdbc;
    }
    @Override public ResourceType resourceType() { return ResourceType.WEEKLY_REPORT; }
    @Override public Grant check(ActorContext actor, ResourceRef resource, FilePurpose purpose, Action action, Instant now) {
        if (purpose != FilePurpose.WEEKLY_REPORT) return Grant.deny();
        UUID id = UUID.fromString(resource.resourceId());
        var report = reports.find(id);
        if (report == null) return Grant.deny();
        var scope = cases.current(report.key().caseId());
        var user = access.currentUser(actor.authentication());
        boolean owner = user.equals(scope.studentUserId());
        if (action == Action.UPLOAD) {
            if (!owner) return Grant.deny();
            access.requirePermission(actor.authentication(), scope, "internship:student:submission_write", now);
            cases.holdForMutation(scope);
            // Current root is locked after source/Case, before deciding whether it is still editable.
            Long version = jdbc.queryForObject("SELECT version FROM internship_weekly_reports WHERE id=? FOR UPDATE", Long.class, id.toString());
            if (version == null || version != report.version()) throw failure(409, "VERSION_CONFLICT");
            if (report.current().state() != WeeklyReport.State.DRAFT
                    || !InternshipActivityPolicy.hasRequiredStateAndStartTime(scope.caseState(), scope.placementState(), scope.effectiveAt(), now)
                    || scope.approvedAt() == null || scope.approvedAt().isAfter(now)) throw failure(409, "STATE_CONFLICT");
        } else {
            access.requirePermission(actor.authentication(), scope, "t3:workspace:read", now);
            if (!owner && !access.canRead(actor.authentication(), scope, now)) return Grant.deny();
        }
        return Grant.owner(report.version(), scope.schoolOrgId().toString(), !owner);
    }
    @Override public boolean mayReadFile(ActorContext actor, String fileId, String uploadedBy,
                                         ResourceRef resource, FilePurpose purpose) {
        var report = reports.find(UUID.fromString(resource.resourceId()));
        if (report == null) return false;
        var scope = cases.current(report.key().caseId());
        if (!scope.studentUserId().toString().equals(uploadedBy)) return false;
        if (actor.actorId().equals(uploadedBy)) return true;
        // A scoped Mentor cannot inspect an uploaded draft orphan that was never attached.
        return report.revisions().stream().anyMatch(revision -> revision.content().evidenceFileIds().contains(UUID.fromString(fileId)));
    }
}
