package vn.utbridora.auth.modules.internship.api;

import java.time.*;
import java.util.*;
import vn.utbridora.auth.modules.internship.domain.WeeklyReport;
import vn.utbridora.auth.modules.internship.domain.WeeklyReport.State;
import static vn.utbridora.auth.modules.internship.domain.WeeklyReportPolicy.*;

/** CP-WS3 v1.0 wire values only. No controller/bean exposes these until runtime guards are integrated. */
public final class ReportDtos {
    private ReportDtos() {}
    public record ReportInput(UUID caseId, LocalDate weekStart, String completedWork, String difficulties,
                              String nextPlan, List<UUID> evidenceFileIds) {
        public ReportInput {
            required(caseId, "caseId"); required(weekStart, "weekStart");
            var content = new WeeklyReport.Content(completedWork, difficulties, nextPlan, evidenceFileIds);
            completedWork = content.completedWork(); difficulties = content.difficulties();
            nextPlan = content.nextPlan(); evidenceFileIds = content.evidenceFileIds();
        }
        public WeeklyReport.Content content() { return new WeeklyReport.Content(completedWork, difficulties, nextPlan, evidenceFileIds); }
    }
    public record UpdateReport(Long expectedVersion, String completedWork, String difficulties,
                               String nextPlan, List<UUID> evidenceFileIds) {
        public UpdateReport {
            version(expectedVersion);
            var content = new WeeklyReport.Content(completedWork, difficulties, nextPlan, evidenceFileIds);
            completedWork = content.completedWork(); difficulties = content.difficulties();
            nextPlan = content.nextPlan(); evidenceFileIds = content.evidenceFileIds();
        }
        public WeeklyReport.Content content() { return new WeeklyReport.Content(completedWork, difficulties, nextPlan, evidenceFileIds); }
    }
    public record SubmitReport(Long expectedVersion) {
        public SubmitReport { version(expectedVersion); }
    }
    public record ReportReview(Long expectedVersion, String comment) {
        public ReportReview { version(expectedVersion); comment = text(comment, 1, 2_000, "comment"); }
    }
    public record CreateReportRevision(Long expectedVersion, String reasonCode) {
        public CreateReportRevision { version(expectedVersion); reasonCode = reason(reasonCode); }
    }
    public record ReportQuery(UUID caseId, State state, Integer page, Integer size) {
        public ReportQuery {
            required(caseId, "caseId"); page = CohortGroupDtos.page(page); size = CohortGroupDtos.pageSize(size);
        }
    }
    public record ReportReadQuery(Integer revisionNo) {
        public ReportReadQuery {
            if (revisionNo != null && revisionNo < 1) throw new IllegalArgumentException("Invalid revisionNo");
        }
    }
    public record ReportDto(UUID reportId, UUID caseId, LocalDate weekStart, Integer revisionNo, Long version,
                            State state, String completedWork, String difficulties, String nextPlan,
                            List<UUID> evidenceFileIds, Instant dueAt, Instant submittedAt, Instant reviewedAt,
                            Boolean lateFlag) {
        public ReportDto {
            required(reportId, "reportId"); required(caseId, "caseId"); required(weekStart, "weekStart");
            required(revisionNo, "revisionNo"); required(state, "state"); required(dueAt, "dueAt");
            required(lateFlag, "lateFlag");
            vn.utbridora.auth.modules.internship.domain.WeeklyReportPolicy.version(version);
            if (revisionNo < 1) throw new IllegalArgumentException("Invalid revisionNo");
            var content = new WeeklyReport.Content(completedWork, difficulties, nextPlan, evidenceFileIds);
            completedWork = content.completedWork(); difficulties = content.difficulties();
            nextPlan = content.nextPlan(); evidenceFileIds = content.evidenceFileIds();
            if (state == State.DRAFT && (submittedAt != null || reviewedAt != null || lateFlag)
                    || state == State.SUBMITTED && (submittedAt == null || reviewedAt != null)
                    || state == State.REVIEWED && (submittedAt == null || reviewedAt == null)) {
                throw new IllegalArgumentException("Inconsistent report response");
            }
        }
        public static ReportDto from(WeeklyReport report, WeeklyReport.Revision revision) {
            var c = revision.content();
            return new ReportDto(report.reportId(), report.key().caseId(), report.key().weekStart(),
                    revision.revisionNo(), revision.version(), revision.state(), c.completedWork(), c.difficulties(),
                    c.nextPlan(), c.evidenceFileIds(), report.dueAt(), revision.submittedAt(),
                    revision.review() == null ? null : revision.review().createdAt(), revision.lateFlag());
        }
    }
    public record ReportPage(List<ReportDto> content, int page, int size, long totalElements, int totalPages) {
        public ReportPage {
            content = List.copyOf(required(content, "content"));
            CohortGroupDtos.page(page); CohortGroupDtos.pageSize(size);
            if (totalElements < 0 || totalPages < 0) throw new IllegalArgumentException("Invalid page totals");
        }
    }
}
