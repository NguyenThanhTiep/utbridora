package vn.utbridora.auth.modules.internship.application;

import java.time.*;
import java.util.*;
import java.util.function.*;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.modules.internship.api.ReportDtos.*;
import vn.utbridora.auth.modules.internship.domain.WeeklyReport;
import vn.utbridora.auth.modules.internship.domain.WeeklyReportPolicy;
import vn.utbridora.auth.modules.internship.domain.InternshipActivityPolicy;
import static vn.utbridora.auth.modules.internship.application.WeeklyReportPorts.*;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** Report use cases with current authorization, revision history, file enforcement and audit. Runtime composition is provided by Week3RuntimeConfiguration. */
public final class WeeklyReportService {
    private static final String STUDENT_WRITE = "internship:student:submission_write";
    private static final String MENTOR_WRITE = "internship:enterprise:task_write";
    private static final String READ = "t3:workspace:read";
    private final UnitOfWork transactions;
    private final Cases cases;
    private final Authority authority;
    private final Assignments assignments;
    private final Files files;
    private final Audit audit;
    private final Clock clock;
    private final Supplier<UUID> identifiers;

    /** All security/transaction/audit providers are mandatory. A file provider may be absent only
     * for text-only reports: attempting to attach/submit any files then fails closed with 503. */
    public WeeklyReportService(UnitOfWork transactions, Cases cases, Authority authority, Assignments assignments,
                                Files files, Audit audit, Clock clock, Supplier<UUID> identifiers) {
        this.transactions = available(transactions); this.cases = available(cases);
        this.authority = available(authority); this.assignments = available(assignments);
        this.files = files; this.audit = available(audit); this.clock = available(clock);
        this.identifiers = available(identifiers);
    }

    public ReportDto create(Authentication actor, ReportInput input) {
        Objects.requireNonNull(input);
        return execute(actor, session -> {
            Instant now = clock.instant(); UUID user = user(actor); var scope = scope(input.caseId());
            student(actor, user, scope, now); active(scope, now); cases.holdForMutation(scope);
            var checkedAt = clock.instant();
            student(actor, user, scope, checkedAt); active(scope, checkedAt);
            var report = WeeklyReport.create(available(identifiers.get()), scope.caseId(), input.weekStart(),
                    scope.startDate(), scope.endDate(), input.content());
            if (!session.insertIfAbsent(report)) throw failure(409, "REPORT_ALREADY_EXISTS");
            record(Action.WEEKLY_REPORT_DRAFT_SAVED, user, report, scope);
            return dto(report);
        });
    }

    public ReportDto update(Authentication actor, UUID reportId, UpdateReport input) {
        Objects.requireNonNull(input);
        return change(actor, reportId, input.expectedVersion(), false, Action.WEEKLY_REPORT_DRAFT_SAVED,
                (report, user, now) -> report.update(input.expectedVersion(), input.content()));
    }

    public ReportDto submit(Authentication actor, UUID reportId, SubmitReport input) {
        Objects.requireNonNull(input);
        return change(actor, reportId, input.expectedVersion(), false, Action.WEEKLY_REPORT_SUBMITTED,
                (report, user, now) -> report.submit(input.expectedVersion(), now));
    }

    public ReportDto review(Authentication actor, UUID reportId, ReportReview input) {
        Objects.requireNonNull(input);
        return change(actor, reportId, input.expectedVersion(), true, Action.WEEKLY_REPORT_REVIEWED,
                (report, user, now) -> report.review(input.expectedVersion(), user, input.comment(), now));
    }

    public ReportDto createRevision(Authentication actor, UUID reportId, CreateReportRevision input) {
        Objects.requireNonNull(input);
        return change(actor, reportId, input.expectedVersion(), false, Action.WEEKLY_REPORT_REVISION_CREATED,
                (report, user, now) -> report.revise(input.expectedVersion(), input.reasonCode()));
    }

    /** Server-only preflight for the common ledger, inside the caller's still-open transaction.
     * Revalidate current authority before replay, but leave state/version/effect checks to mutation.
     * Never return a public response, trust client organization/actor, or check expectedVersion here. */
    public record MutationScope(UUID actorId, UUID organizationId) {}
    public MutationScope authorizeCreate(Authentication actor, ReportInput input) {
        Objects.requireNonNull(input);
        return execute(actor, session -> {
            var now = clock.instant(); var user = user(actor); var scope = scope(input.caseId());
            student(actor, user, scope, now);
            return new MutationScope(user, scope.schoolOrgId());
        });
    }
    public MutationScope authorizeChange(Authentication actor, UUID reportId, boolean mentorReview) {
        return execute(actor, session -> {
            var now = clock.instant(); var user = user(actor); var report = load(session, reportId);
            var scope = scope(report.key().caseId());
            requireWriter(actor, user, scope, mentorReview, now);
            return new MutationScope(user, mentorReview ? scope.enterpriseOrgId() : scope.schoolOrgId());
        });
    }

    public ReportDto read(Authentication actor, UUID reportId, ReportReadQuery query) {
        Objects.requireNonNull(query);
        return execute(actor, session -> {
            Instant now = clock.instant(); UUID user = user(actor); var report = load(session, reportId);
            readable(actor, user, scope(report.key().caseId()), now);
            var revision = query.revisionNo() == null ? report.current() : report.revision(query.revisionNo());
            return ReportDto.from(report, revision);
        });
    }

    public ReportReviewDto readReview(Authentication actor, UUID reportId, ReportReadQuery query) {
        Objects.requireNonNull(query);
        return execute(actor, session -> {
            var report = load(session, reportId);
            readable(actor, user(actor), scope(report.key().caseId()), clock.instant());
            var revision = query.revisionNo() == null ? report.current() : report.revision(query.revisionNo());
            var review = revision.review();
            if (review == null) throw failure(404, "RESOURCE_NOT_FOUND");
            return new ReportReviewDto(reportId, revision.revisionNo(), review.reviewerUserId(), review.comment(), review.createdAt());
        });
    }

    /** History is obtained through read(..., revisionNo), as declared by CP-WS3. */
    public ReportPage list(Authentication actor, ReportQuery query) {
        Objects.requireNonNull(query);
        return execute(actor, session -> {
            Instant now = clock.instant(); UUID user = user(actor);
            readable(actor, user, scope(query.caseId()), now);
            var slice = available(session.list(query.caseId(), query.state(), query.page(), query.size()));
            if (slice.content().size() > query.size() || slice.totalElements() < slice.content().size()) throw unavailable();
            var result = new ArrayList<ReportDto>();
            var rootIds = new HashSet<UUID>();
            var order = Comparator.comparing((WeeklyReport r) -> r.key().weekStart()).reversed()
                    .thenComparing(r -> r.reportId().toString());
            WeeklyReport previous = null;
            for (var report : slice.content()) {
                if (!rootIds.add(report.reportId()) || !query.caseId().equals(report.key().caseId())
                        || query.state() != null && query.state() != report.current().state()
                        || previous != null && order.compare(previous, report) >= 0) throw unavailable();
                result.add(dto(report)); previous = report;
            }
            long pages = slice.totalElements() / query.size() + (slice.totalElements() % query.size() == 0 ? 0 : 1);
            if (pages > Integer.MAX_VALUE) throw unavailable();
            return new ReportPage(result, query.page(), query.size(), slice.totalElements(), (int) pages);
        });
    }

    private interface Change { WeeklyReport apply(WeeklyReport report, UUID user, Instant now); }
    private ReportDto change(Authentication actor, UUID reportId, long expectedVersion, boolean mentor,
                              Action action, Change change) {
        return execute(actor, session -> {
            Instant now = clock.instant(); UUID user = user(actor); var current = load(session, reportId);
            var scope = scope(current.key().caseId());
            requireWriter(actor, user, scope, mentor, now);
            active(scope, now); cases.holdForMutation(scope);
            WeeklyReportPolicy.requireWeek(current.key().weekStart(), scope.startDate(), scope.endDate());
            var updated = change.apply(current, user, clock.instant());
            // Preserve invalid state/version precedence; file checks never create partial writes.
            if (!mentor && !updated.current().content().evidenceFileIds().isEmpty()) {
                available(files).requireBound(actor, scope.studentUserId(), current.reportId(),
                        updated.current().content().evidenceFileIds(), action == Action.WEEKLY_REPORT_SUBMITTED);
            }
            // Source/file guards may wait across dueAt. Persist the transition time after those
            // checks, not the request-start time. Change is a pure immutable domain operation.
            var checkedAt = clock.instant();
            requireWriter(actor, user, scope, mentor, checkedAt); active(scope, checkedAt);
            updated = change.apply(current, user, clock.instant());
            if (!session.replace(updated, expectedVersion)) throw failure(409, "VERSION_CONFLICT");
            record(action, user, updated, scope);
            return dto(updated);
        });
    }

    private <T> T execute(Authentication actor, Function<Session, T> operation) {
        if (actor == null || !actor.isAuthenticated() || actor instanceof AnonymousAuthenticationToken) {
            throw failure(401, "SESSION_EXPIRED");
        }
        return available(transactions.execute(session -> operation.apply(available(session))));
    }
    private UUID user(Authentication actor) { return available(authority.currentUser(actor)); }
    private CaseScope scope(UUID id) {
        var scope = available(cases.current(Objects.requireNonNull(id)));
        if (!id.equals(scope.caseId())) throw unavailable();
        return scope;
    }
    private static WeeklyReport load(Session session, UUID id) {
        var report = session.find(Objects.requireNonNull(id));
        if (report == null) throw hidden();
        if (!id.equals(report.reportId())) throw unavailable();
        return report;
    }
    private void student(Authentication actor, UUID user, CaseScope scope, Instant now) {
        authority.requirePermission(actor, scope, STUDENT_WRITE, now);
        if (!user.equals(scope.studentUserId())) throw hidden();
    }
    private void readable(Authentication actor, UUID user, CaseScope scope, Instant now) {
        authority.requirePermission(actor, scope, READ, now);
        if (!user.equals(scope.studentUserId()) && !assignments.canRead(actor, scope, now)) throw hidden();
    }
    private void requireWriter(Authentication actor, UUID user, CaseScope scope, boolean mentor, Instant now) {
        if (mentor) {
            authority.requirePermission(actor, scope, MENTOR_WRITE, now);
            if (!assignments.isCurrentMentor(actor, scope, now)) throw hidden();
        } else student(actor, user, scope, now);
    }
    private static void active(CaseScope scope, Instant now) {
        if (!InternshipActivityPolicy.hasRequiredStateAndStartTime(scope.caseState(), scope.placementState(), scope.effectiveAt(), now)
                || scope.approvedAt() == null || scope.approvedAt().isAfter(now)) throw failure(409, "STATE_CONFLICT");
    }
    private void record(Action action, UUID user, WeeklyReport report, CaseScope scope) {
        var revision = report.current();
        audit.append(new AuditEvent(action, user, report.reportId(), report.key().caseId(), report.key().weekStart(),
                revision.revisionNo(), revision.version(), revision.lateFlag(), revision.reasonCode(), scope.schoolOrgId()));
    }
    private static ReportDto dto(WeeklyReport report) { return ReportDto.from(report, report.current()); }
    private static vn.utbridora.auth.operations.OperationFailure hidden() { return failure(404, "RESOURCE_NOT_FOUND"); }
    private static vn.utbridora.auth.operations.OperationFailure unavailable() { return failure(503, "DEPENDENCY_UNAVAILABLE"); }
    private static <T> T available(T provider) { if (provider == null) throw unavailable(); return provider; }
}
