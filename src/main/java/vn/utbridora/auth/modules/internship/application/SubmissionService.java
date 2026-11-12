package vn.utbridora.auth.modules.internship.application;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.audit.AuditAction;
import vn.utbridora.auth.modules.internship.api.SubmissionDtos.ReviewDto;
import vn.utbridora.auth.modules.internship.api.SubmissionDtos.ReviewInput;
import vn.utbridora.auth.modules.internship.api.SubmissionDtos.SubmissionDto;
import vn.utbridora.auth.modules.internship.api.SubmissionDtos.SubmissionInput;
import vn.utbridora.auth.modules.internship.api.TaskDtos.AssignmentDto;
import vn.utbridora.auth.modules.internship.application.SubmissionPorts.*;
import vn.utbridora.auth.modules.internship.application.WorkspacePorts.CaseChecks;
import vn.utbridora.auth.modules.internship.domain.SubmissionPolicy;
import vn.utbridora.auth.operations.OperationLedger;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** CP-WS2 URL version/history and Mentor Review flows. FR-17 belongs to DEV06.
 * Runtime requires approved schema and canonical mandatory Core audit support; no file-mode fallback.
 */
public final class SubmissionService {
    private final UnitOfWork transactions;
    private final Access access;
    private final CaseChecks cases;
    private final Clock clock;
    private final Supplier<UUID> identifiers;

    public SubmissionService(UnitOfWork transactions, Access access, CaseChecks cases, Clock clock,
                             Supplier<UUID> identifiers) {
        this.transactions = Objects.requireNonNull(transactions);
        this.access = Objects.requireNonNull(access);
        this.cases = Objects.requireNonNull(cases);
        this.clock = Objects.requireNonNull(clock);
        this.identifiers = Objects.requireNonNull(identifiers);
    }

    /** Compatibility entry point for the earlier M2A consumer; uses the same versioned flow. */
    public SubmissionDto submitUrlV1(Authentication auth, UUID assignmentId, String key, SubmissionInput input) {
        return submitUrl(auth, assignmentId, key, input);
    }

    public SubmissionDto submitUrl(Authentication auth, UUID assignmentId, String key, SubmissionInput input) {
        UUID actorId = UUID.fromString(actor(auth));
        Objects.requireNonNull(assignmentId);
        Objects.requireNonNull(input);
        return transactions.execute(session -> {
            var target = load(session.assignment(assignmentId), assignmentId);
            requireOwner(actorId, target);
            access.requireSubmit(auth, target); // Current auth is required even for cached replay.
            if (input.url() == null || !input.fileIds().isEmpty()) {
                // Valid file input must wait for CLEAN/binding integration, never pretend it is clean.
                throw failure(503, "DEPENDENCY_UNAVAILABLE");
            }
            var command = new OperationLedger.Command(actorId.toString(), target.cohort().scope().schoolOrgId().toString(),
                    "T3_SUBMISSION_CREATE", key, Map.of("assignmentId", assignmentId, "request", input), 201);
            return session.idempotent(command, SubmissionDto.class, () -> {
                var guardNow = clock.instant();
                UUID student = cases.requireTaskParticipant(auth, target.cohort(), target.group(),
                        target.assignment().caseId(), guardNow);
                if (student == null || !student.equals(target.assignment().studentUserId())) throw unavailable();
                var current = load(session.lockAssignment(assignmentId), assignmentId);
                requireSameResource(target, current);
                requireOwner(actorId, current);
                access.requireSubmit(auth, current);
                var assignment = current.assignment();
                SubmissionPolicy.requireSubmission(assignment.state(), assignment.version(), input.expectedAssignmentVersion());
                SubmissionDto previous = null;
                int versionNo = 1;
                if (assignment.currentSubmissionId() == null) {
                    if (session.hasHistory(assignmentId)) throw failure(409, "STATE_CONFLICT");
                } else {
                    previous = session.submission(assignment.currentSubmissionId());
                    if (previous == null || !assignmentId.equals(previous.assignmentId())) throw unavailable();
                    var priorReview = session.reviewForSubmission(previous.submissionId());
                    if (priorReview == null || priorReview.verdict() != vn.utbridora.auth.modules.internship.api.SubmissionDtos.ReviewVerdict.REQUEST_REVISION) {
                        throw failure(409, "STATE_CONFLICT");
                    }
                    if (!priorReview.submissionId().equals(previous.submissionId())
                            || !priorReview.reviewedVersionNo().equals(previous.versionNo())) throw unavailable();
                    var latest = session.history(assignmentId, 0, 1);
                    if (latest == null || latest.content().size() != 1 || !previous.equals(latest.content().getFirst())) throw unavailable();
                    if (previous.versionNo() == Integer.MAX_VALUE) throw failure(409, "VERSION_CONFLICT");
                    versionNo = previous.versionNo() + 1;
                }
                var task = current.task().value();
                var now = clock.instant(); // Deadline uses server time after guards/lock waits.
                boolean late = SubmissionPolicy.lateFlag(task.dueAt(), task.lateAllowed(), now);
                var created = new SubmissionDto(Objects.requireNonNull(identifiers.get()), assignmentId, versionNo,
                        List.of(), input.url(), input.note(), now, late);
                if (previous == null) {
                    session.appendFirst(assignment, created);
                    session.audit(AuditAction.SUBMISSION_CREATED.name(), actorId, current.cohort().scope().schoolOrgId(),
                            created.submissionId(), Map.of("assignmentId", assignmentId.toString(), "versionNo", versionNo,
                                    "fileIds", List.of(), "lateFlag", late));
                } else {
                    session.appendRevision(assignment, previous, created);
                    session.audit(AuditAction.SUBMISSION_REVISED.name(), actorId, current.cohort().scope().schoolOrgId(),
                            created.submissionId(), Map.of("assignmentId", assignmentId.toString(), "versionNo", versionNo,
                                    "previousSubmissionId", previous.submissionId().toString(), "fileIds", List.of(), "lateFlag", late));
                }
                return created;
            }, result -> result.submissionId().toString());
        });
    }

    /** CP-WS2/FR-16/R21. Authorization precedes replay; guards/version/CAS/audit join the mutation.
     * Review comment is part of immutable ReviewDto, never an FR-17 Task Comment or audit field.
     */
    public ReviewDto review(Authentication auth, UUID submissionId, String key, ReviewInput input) {
        UUID reviewerId = UUID.fromString(actor(auth));
        Objects.requireNonNull(submissionId);
        Objects.requireNonNull(input);
        return transactions.execute(session -> {
            var submitted = loadSubmission(session.submission(submissionId), submissionId);
            var target = load(session.assignment(submitted.assignmentId()), submitted.assignmentId());
            access.requireReview(auth, target);
            var command = new OperationLedger.Command(reviewerId.toString(), target.cohort().scope().schoolOrgId().toString(),
                    "T3_SUBMISSION_REVIEW", key, Map.of("submissionId", submissionId, "request", input), 201);
            return session.idempotent(command, ReviewDto.class, () -> {
                UUID student = cases.requireTaskParticipant(auth, target.cohort(), target.group(),
                        target.assignment().caseId(), clock.instant());
                if (student == null || !student.equals(target.assignment().studentUserId())) throw unavailable();
                var current = load(session.lockAssignment(submitted.assignmentId()), submitted.assignmentId());
                requireSameResource(target, current);
                access.requireReview(auth, current);
                var artifact = loadSubmission(session.submission(submissionId), submissionId);
                if (!submitted.equals(artifact)) throw unavailable();
                var assignment = current.assignment();
                SubmissionPolicy.requireReview(assignment.state(), assignment.version(), input.expectedAssignmentVersion(),
                        submissionId, assignment.currentSubmissionId());
                if (session.hasReview(submissionId)) throw failure(409, "STATE_CONFLICT");
                var reviewed = new ReviewDto(Objects.requireNonNull(identifiers.get()), submissionId, artifact.versionNo(),
                        input.verdict(), input.comment(), reviewerId, clock.instant(), assignment.version() + 1);
                var updated = new AssignmentDto(assignment.assignmentId(), assignment.caseId(), assignment.studentUserId(),
                        SubmissionPolicy.reviewedState(input.verdict()), reviewed.assignmentVersion(), submissionId);
                session.appendReview(assignment, updated, reviewed);
                session.audit("SUBMISSION_REVIEWED", reviewerId, current.cohort().scope().schoolOrgId(), submissionId,
                        Map.of("assignmentId", assignment.assignmentId().toString(),
                                "reviewedVersion", reviewed.reviewedVersionNo(), "verdict", reviewed.verdict().name(),
                                "assignmentVersion", reviewed.assignmentVersion()));
                return reviewed;
            }, result -> result.reviewId().toString());
        });
    }

    /** Read the immutable review of any visible artifact, including older versions.
     * A null result means the authorized artifact has not been reviewed yet.
     * Like history, this uses current read scope without mutation/state guards.
     */
    public ReviewDto readReview(Authentication auth, UUID submissionId) {
        actor(auth);
        Objects.requireNonNull(submissionId);
        return transactions.execute(session -> {
            var submitted = loadSubmission(session.submission(submissionId), submissionId);
            var target = load(session.assignment(submitted.assignmentId()), submitted.assignmentId());
            access.requireRead(auth, target);
            var review = session.reviewForSubmission(submissionId);
            if (review != null && (!submissionId.equals(review.submissionId())
                    || !submitted.versionNo().equals(review.reviewedVersionNo()))) throw unavailable();
            return review;
        });
    }

    public HistoryPage history(Authentication auth, UUID assignmentId, int page, int size) {
        actor(auth);
        Objects.requireNonNull(assignmentId);
        page(page, size);
        return transactions.execute(session -> {
            var target = load(session.assignment(assignmentId), assignmentId);
            access.requireRead(auth, target);
            var result = session.history(assignmentId, page, size);
            if (result == null || result.content().size() > size
                    || result.content().stream().anyMatch(row -> !assignmentId.equals(row.assignmentId()))) {
                throw unavailable();
            }
            var order = Comparator.comparing(SubmissionDto::versionNo).reversed()
                    .thenComparing(row -> row.submissionId().toString());
            if (!result.content().equals(result.content().stream().sorted(order).toList())) throw unavailable();
            // History is readable after terminal Case/Placement state if current read authorization permits.
            return result;
        });
    }

    private void requireOwner(UUID actorId, AssignmentContext target) {
        if (!actorId.equals(target.assignment().studentUserId())) throw failure(404, "RESOURCE_NOT_FOUND");
    }

    private static AssignmentContext load(AssignmentContext target, UUID expectedId) {
        if (target == null) throw failure(404, "RESOURCE_NOT_FOUND");
        if (!expectedId.equals(target.assignment().assignmentId())) throw unavailable();
        return target;
    }

    private static SubmissionDto loadSubmission(SubmissionDto value, UUID expectedId) {
        if (value == null) throw failure(404, "RESOURCE_NOT_FOUND");
        if (!expectedId.equals(value.submissionId())) throw unavailable();
        return value;
    }

    private static void requireSameResource(AssignmentContext before, AssignmentContext after) {
        if (!before.cohort().id().equals(after.cohort().id())
                || !before.cohort().scope().equals(after.cohort().scope())
                || !before.group().id().equals(after.group().id())
                || !before.task().value().taskId().equals(after.task().value().taskId())
                || !before.assignment().caseId().equals(after.assignment().caseId())
                || !before.assignment().studentUserId().equals(after.assignment().studentUserId())) throw unavailable();
    }

    private static RuntimeException unavailable() { return failure(503, "DEPENDENCY_UNAVAILABLE"); }
}
