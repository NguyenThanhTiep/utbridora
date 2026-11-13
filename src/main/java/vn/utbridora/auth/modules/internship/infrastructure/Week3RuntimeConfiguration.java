package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.contracts.placement.PlacementReadPort;
import vn.utbridora.auth.files.FileService;
import vn.utbridora.auth.modules.internship.application.*;
import vn.utbridora.auth.operations.OperationLedger;

/** Explicit activation only after the migration is installed. No DDL or fallback provider. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.t3.week3.http.enabled", havingValue = "true")
public class Week3RuntimeConfiguration {
    @Bean Clock week3Clock() { return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000)); }
    @Bean Week3WorkspaceTransactions week3Transactions(PlatformTransactionManager manager, JdbcWorkspaceSession workspace) {
        return new Week3WorkspaceTransactions(manager, workspace);
    }
    @Bean WeeklyReportCaseAdapter weeklyReportCases(CaseRuntime cases, PlacementReadPort placements) {
        return new WeeklyReportCaseAdapter(cases, placements, cases::holdForReport);
    }
    @Bean WeeklyReportAccessAdapter weeklyReportAccess(AuthorizationPolicy core, WorkspaceAuthority authority, WorkspaceRuntime runtime) {
        return new WeeklyReportAccessAdapter(core, authority, runtime);
    }
    @Bean JdbcWeeklyReportSession weeklyReportSession(JdbcTemplate jdbc) {
        return new JdbcWeeklyReportSession(jdbc, () -> {
            jdbc.queryForList("SELECT id,case_id,week_start,due_at,current_revision_no,version FROM internship_weekly_reports WHERE 1=0");
            jdbc.queryForList("SELECT report_id,revision_no,submitted_submicro_nanos FROM internship_weekly_report_revisions WHERE 1=0");
            jdbc.queryForList("SELECT report_id,revision_no,reviewed_submicro_nanos FROM internship_weekly_report_reviews WHERE 1=0");
            jdbc.queryForList("SELECT report_id,revision_no,ordinal_no,file_id FROM internship_weekly_report_files WHERE 1=0");
        });
    }
    @Bean WeeklyReportUnitOfWork weeklyReportTransactions(Week3WorkspaceTransactions work, JdbcWeeklyReportSession session) {
        return new WeeklyReportUnitOfWork(work, () -> session);
    }
    @Bean WeeklyReportService weeklyReportService(WeeklyReportUnitOfWork work, WeeklyReportCaseAdapter cases,
            WeeklyReportAccessAdapter access, AuditService audit, FileService files, @Qualifier("week3Clock") Clock clock) {
        return new WeeklyReportService(work, cases, access, access, new WeeklyReportFileAdapter(files),
                new WeeklyReportAuditAdapter(audit), clock, UUID::randomUUID);
    }
    @Bean WeeklyReportFilePolicy weeklyReportFilePolicy(JdbcWeeklyReportSession reports, WeeklyReportCaseAdapter cases,
            WeeklyReportAccessAdapter access, JdbcTemplate jdbc) {
        return new WeeklyReportFilePolicy(reports, cases, access, jdbc);
    }
    @Bean WeeklyReportCommands weeklyReportCommands(WeeklyReportUnitOfWork work, WeeklyReportService reports, OperationLedger ledger) {
        return new WeeklyReportCommands(work, reports, ledger);
    }
    @Bean JdbcTaskCommentSession taskCommentSession(JdbcTemplate jdbc) {
        var layout = new JdbcTaskCommentSession.Layout("internship_task_comments", "id", "assignment_id",
                "author_user_id", "text", "created_at", null);
        // Comment append does not transition or rewrite an Assignment. Its locked version is audit metadata.
        return new JdbcTaskCommentSession(jdbc, layout, target -> target.assignment().version(),
                () -> jdbc.queryForList("SELECT id,assignment_id,author_user_id,text,created_at FROM internship_task_comments WHERE 1=0"));
    }
    @Bean TaskCommentService taskCommentService(Week3WorkspaceTransactions work, JdbcTaskCommentSession session,
            JdbcSubmissionSession assignments, WeeklyReportCaseAdapter cases, CaseRuntime caseOwner,
            WeeklyReportAccessAdapter identity, AuthorizationPolicy core, WorkspaceAuthority authority,
            WorkspaceRuntime runtime, AuditService audit, @Qualifier("week3Clock") Clock clock) {
        return new TaskCommentService(work, () -> session, new TaskCommentAssignmentsAdapter(assignments), cases,
                new TaskCommentCaseGuard(caseOwner, cases), new TaskCommentAccessAdapter(identity, core, authority, runtime),
                new TaskCommentAuditAdapter(audit), clock, UUID::randomUUID);
    }
    @Bean TaskCommentCommands taskCommentCommands(Week3WorkspaceTransactions work, TaskCommentService comments, OperationLedger ledger) {
        return new TaskCommentCommands(work, comments, ledger);
    }
}
