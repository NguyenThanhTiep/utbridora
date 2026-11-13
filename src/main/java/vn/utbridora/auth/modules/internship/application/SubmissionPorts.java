package vn.utbridora.auth.modules.internship.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.modules.internship.api.SubmissionDtos.ReviewDto;
import vn.utbridora.auth.modules.internship.api.SubmissionDtos.SubmissionDto;
import vn.utbridora.auth.modules.internship.api.TaskDtos.AssignmentDto;
import vn.utbridora.auth.modules.internship.domain.Cohort;
import vn.utbridora.auth.modules.internship.domain.InternshipGroup;
import vn.utbridora.auth.modules.internship.domain.InternshipTask;
import vn.utbridora.auth.operations.OperationLedger;

/** T3-07 application boundaries. Runtime adapters reuse Core ledger, current authorization
 * and authoritative Case/Placement providers; their schema/audit dependencies remain mandatory.
 */
public final class SubmissionPorts {
    private SubmissionPorts() {}

    /** Trusted persistence projection, never deserialized from a browser request. */
    public record AssignmentContext(Cohort cohort, InternshipGroup group, InternshipTask task,
                                    AssignmentDto assignment) {
        public AssignmentContext {
            Objects.requireNonNull(cohort);
            Objects.requireNonNull(group);
            Objects.requireNonNull(task);
            Objects.requireNonNull(assignment);
            if (!cohort.id().equals(group.cohortId()) || !group.id().equals(task.value().groupId())
                    || !task.value().assignments().contains(assignment)) {
                throw new IllegalArgumentException("Inconsistent stored assignment context");
            }
            // Do not require current group membership: old Task snapshots survive a group move.
        }
    }

    /** Internal query result, not a replacement for the CP-WS2 HTTP Page envelope. */
    public record HistoryPage(List<SubmissionDto> content, long totalElements) {
        public HistoryPage {
            content = List.copyOf(content);
            if (totalElements < content.size()) throw new IllegalArgumentException("Invalid history count");
        }
    }

    public interface UnitOfWork {
        /** One REQUIRED transaction including guards, ledger, append/CAS and mandatory success audit.
         * Any exception rolls back all effects and ledger state. No independent reservation commit.
         */
        <T> T execute(Function<Session, T> operation);
    }

    public interface Access {
        /** Re-read current account/security version and school STUDENT membership with exact
         * internship:student:submission_write permission. Owner comes from stored assignment.
         * Hold/recheck current authorization through commit; conceal foreign resources with 404.
         */
        void requireSubmit(Authentication actor, AssignmentContext target);

        /** t3:workspace:read plus self / currently assigned Mentor / delegated scoped viewer.
         * Reuse WorkspacePorts.Access and current handover rules; no implicit HR/ADMIN bypass.
         * Deny with existing Core/domain errors; artifact download authorization stays separate.
         */
        void requireRead(Authentication actor, AssignmentContext target);

        /** Exact internship:enterprise:task_write permission in the stored enterprise scope AND
         * current Mentor assignment/handover for this Task/group/Case. Revalidate account,
         * membership, security version, parent grants/revocation and scope through commit.
         * Student ownership, HR membership or ADMIN alone never grants Review authority.
         */
        void requireReview(Authentication actor, AssignmentContext target);
    }

    public interface Session {
        /** Read stored assignment + original Task/group/cohort scope; null means not found. */
        AssignmentContext assignment(UUID assignmentId);

        /** Re-read and lock assignment AFTER existing CaseChecks locks T5 source then Case.
         * Task identity, scope, owner and deadline must remain consistent through commit.
         */
        AssignmentContext lockAssignment(UUID assignmentId);

        /** Stored immutable artifact version, not a browser snapshot; null means not found. */
        SubmissionDto submission(UUID submissionId);

        /** Delegate to OperationLedger.execute(command, responseType, mutation, effectReference)
         * in the SAME transaction. Submission uses submissionId, Review uses reviewId as effect ref.
         * Never implement a second production ledger/cache; no mutation callback on replay.
         */
        <T> T idempotent(OperationLedger.Command command, Class<T> responseType,
                         Supplier<T> mutation, Function<T, String> effectReference);

        /** Detect existing history even if a corrupt/stale currentSubmissionId is null. */
        boolean hasHistory(UUID assignmentId);

        /** INSERT only: enforce UNIQUE(assignmentId,versionNo), no existing history, and CAS
         * expected assignment version + IN_PROGRESS + currentSubmissionId=null. Set SUBMITTED,
         * increment assignment version once, set currentSubmissionId; never UPDATE old artifacts.
         * Throw VERSION_CONFLICT / STATE_CONFLICT using existing error conventions on a race.
         */
        void appendFirst(AssignmentDto expected, SubmissionDto submission);

        /** Append the next immutable URL artifact after REQUEST_REVISION + the existing Student
         * Start transition. CAS the stored current pointer/version/state; never rewrite previous.
         */
        void appendRevision(AssignmentDto expected, SubmissionDto previous, SubmissionDto next);

        /** Immutable authoritative Review of this artifact; null if it has not been reviewed. */
        ReviewDto reviewForSubmission(UUID submissionId);

        /** True after any committed Review of this submission; never overwrite/reuse a Review row. */
        boolean hasReview(UUID submissionId);

        /** INSERT immutable Review, enforce uniqueness per submission, then CAS expected assignment
         * version + SUBMITTED + currentSubmissionId=review.submissionId. Increment version once;
         * ACCEPT -> COMPLETED, REQUEST_REVISION -> REVISION_REQUIRED. Keep artifact/history and
         * currentSubmissionId unchanged. Conflict/audit failure must roll back every effect.
         */
        void appendReview(AssignmentDto expected, AssignmentDto updated, ReviewDto review);

        /** Mandatory AuditService adapter using canonical E action names and allowlisted metadata.
         * Fail closed/roll back if registry support is missing or audit persistence fails.
         * No URL, note, free text or request payload in metadata.
         * String matches AuditService.workflow's boundary; it does not add a Core enum/registry entry.
         * SUBMISSION_REVIEWED is specified by v1.0 but unsupported in this baseline's Core:
         * real wiring must wait for owner support, never alias it to another action or skip audit.
         */
        void audit(String action, UUID actorId, UUID schoolOrgId, UUID submissionId,
                   Map<String, Object> metadata);

        /** Immutable stored snapshots, assignment-filtered, ORDER BY versionNo DESC,submissionId ASC,
         * paginated in storage with a consistent count. Never reconstruct from current assignment.
         */
        HistoryPage history(UUID assignmentId, int page, int size);
    }
}
