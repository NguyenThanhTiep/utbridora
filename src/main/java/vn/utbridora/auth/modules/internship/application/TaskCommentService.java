package vn.utbridora.auth.modules.internship.application;

import java.time.*;
import java.util.*;
import java.util.function.*;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.modules.internship.api.SubmissionDtos.*;
import vn.utbridora.auth.modules.internship.domain.*;
import static vn.utbridora.auth.modules.internship.application.TaskCommentPorts.*;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** DEV06 FR-17 use cases. Reuses existing transaction and Case/Placement abstractions.
 * No Spring bean/HTTP route, schema, permission grant or fake provider is created. Use the common
 * ledger boundary before production exposure. Report Review and Submission Review stay separate.
 */
public final class TaskCommentService {
    private final WorkspacePorts.UnitOfWork transactions;
    private final Supplier<Session> sessions;
    private final Assignments assignments;
    private final WeeklyReportPorts.Cases cases;
    private final CaseGuard caseGuard;
    private final Access access;
    private final Audit audit;
    private final Clock clock;
    private final Supplier<UUID> identifiers;
    public TaskCommentService(WorkspacePorts.UnitOfWork transactions, Supplier<Session> sessions, Assignments assignments,
                               WeeklyReportPorts.Cases cases, CaseGuard caseGuard, Access access, Audit audit, Clock clock, Supplier<UUID> identifiers) {
        this.transactions = available(transactions); this.sessions = available(sessions); this.assignments = available(assignments);
        this.cases = available(cases); this.caseGuard = available(caseGuard); this.access = available(access); this.audit = available(audit);
        this.clock = available(clock); this.identifiers = available(identifiers);
    }
    public CommentDto create(Authentication actor, UUID assignmentId, CommentInput input) {
        Objects.requireNonNull(input);
        return execute(actor, session -> {
            Instant now = clock.instant(); UUID user = available(access.currentUser(actor));
            var target = target(assignmentId); var scope = scope(target);
            access.requireWrite(actor, target, scope, now);
            if (!InternshipActivityPolicy.hasRequiredStateAndStartTime(scope.caseState(), scope.placementState(), scope.effectiveAt(), now)
                    || scope.approvedAt() == null || scope.approvedAt().isAfter(now)) throw failure(409, "STATE_CONFLICT");
            caseGuard.hold(actor, target, scope, clock.instant());
            var locked = available(assignments.holdForMutation(target));
            requireSameTarget(target, locked);
            access.requireWrite(actor, locked, scope, clock.instant());
            var comment = TaskComment.create(available(identifiers.get()), assignmentId, user, input, clock.instant());
            var receipt = available(session.append(locked, comment));
            if (!comment.value().equals(receipt.comment()) || receipt.assignmentVersion() < locked.assignment().version()) throw unavailable();
            audit.append(new AuditEvent(user, assignmentId, comment.value().commentId(), scope.schoolOrgId(), receipt.assignmentVersion()));
            return comment.value();
        });
    }
    /** Ledger preflight intentionally does not impose a submission state or client expectedVersion. */
    public record MutationScope(UUID actorId, UUID schoolOrgId) {}
    public MutationScope authorizeCreate(Authentication actor, UUID assignmentId) {
        return execute(actor, session -> {
            var user = available(access.currentUser(actor)); var target = target(assignmentId); var scope = scope(target);
            access.requireWrite(actor, target, scope, clock.instant());
            return new MutationScope(user, scope.schoolOrgId());
        });
    }
    public CommentPage list(Authentication actor, UUID assignmentId, Integer page, Integer size) {
        int requestedPage = page == null ? 0 : page, requestedSize = size == null ? 25 : size;
        vn.utbridora.auth.operations.WorkflowValues.page(requestedPage, requestedSize);
        return execute(actor, session -> {
            available(access.currentUser(actor)); var target = target(assignmentId); var scope = scope(target);
            access.requireRead(actor, target, scope, clock.instant());
            // Historical comments remain readable after Case/Placement terminal state if currently authorized.
            var result = available(session.comments(assignmentId, requestedPage, requestedSize));
            if (result.page() != requestedPage || result.size() != requestedSize || result.content().size() > requestedSize
                    || result.totalElements() < result.content().size()) throw unavailable();
            long pages = result.totalElements() / requestedSize + (result.totalElements() % requestedSize == 0 ? 0 : 1);
            if (pages > Integer.MAX_VALUE || result.totalPages() != pages) throw unavailable();
            var ids = new HashSet<UUID>(); CommentDto previous = null;
            var order = Comparator.comparing(CommentDto::createdAt).thenComparing(c -> c.commentId().toString());
            for (var comment : result.content()) {
                if (!assignmentId.equals(comment.assignmentId()) || !ids.add(comment.commentId())
                        || previous != null && order.compare(previous, comment) >= 0) throw unavailable();
                previous = comment;
            }
            return result;
        });
    }
    private Target target(UUID id) {
        Objects.requireNonNull(id, "assignmentId");
        var target = assignments.current(id);
        if (target == null) throw failure(404, "RESOURCE_NOT_FOUND");
        if (!id.equals(target.assignment().assignmentId())) throw unavailable();
        return target;
    }
    private WeeklyReportPorts.CaseScope scope(Target target) {
        var value = available(cases.current(target.assignment().caseId()));
        if (!target.matches(value)) throw failure(404, "RESOURCE_NOT_FOUND");
        return value;
    }
    private static void requireSameTarget(Target before, Target after) {
        if (!before.cohort().id().equals(after.cohort().id()) || !before.cohort().scope().equals(after.cohort().scope())
                || !before.group().id().equals(after.group().id()) || !before.task().value().taskId().equals(after.task().value().taskId())
                || !before.assignment().assignmentId().equals(after.assignment().assignmentId())
                || !before.assignment().caseId().equals(after.assignment().caseId())
                || !before.assignment().studentUserId().equals(after.assignment().studentUserId())) throw unavailable();
        if (!before.assignment().version().equals(after.assignment().version())) throw failure(409, "VERSION_CONFLICT");
    }
    private <T> T execute(Authentication actor, Function<Session, T> operation) {
        if (actor == null || !actor.isAuthenticated() || actor instanceof AnonymousAuthenticationToken) throw failure(401, "SESSION_EXPIRED");
        return available(transactions.execute(workspace -> {
            available(workspace); return available(operation.apply(available(sessions.get())));
        }));
    }
    private static vn.utbridora.auth.operations.OperationFailure unavailable() { return failure(503, "DEPENDENCY_UNAVAILABLE"); }
    private static <T> T available(T value) { if (value == null) throw unavailable(); return value; }
}
