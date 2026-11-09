package vn.utbridora.auth.modules.internship.application;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.modules.internship.domain.*;

/** DEV06 consumer-side abstractions, distinct from canonical T1/T3-01/T5 wire contracts.
 * Runtime providers are registered in WorkspaceConfiguration.
 */
public final class WorkspacePorts {
    private WorkspacePorts() {}

    /** Must commit guards/version checks, writes, history and success audit in one transaction.
     * Exceptions must roll back every write. No committed reservation or partial move is allowed.
     */
    public interface UnitOfWork {
        <T> T execute(Function<Session, T> operation);
    }
    public interface Session {
        Cohort cohort(UUID id);
        Cohort cohortByKey(Cohort.Key key);
        InternshipGroup group(UUID id);
        InternshipTask task(UUID id);
        UUID currentGroup(UUID cohortId, UUID caseId);
        /** Atomic uniqueness of enterprise/school/term/code; duplicate throws DUPLICATE_COHORT. */
        void insertCohort(Cohort cohort);
        /** Recheck group + Case versions and uniqueness; preserve membership history and audit.
         * Case changes must use its owner's public mutation port, never the owner's repository.
         */
        void addMember(InternshipGroup updated, UUID caseId, long expectedCaseVersion, long expectedGroupVersion);
        /** CAS both groups and Case through its owner port, preserve old Task snapshots;
         * append reason/history and audit. Never implement by writing another owner's repository.
         */
        void moveMember(InternshipGroup.Transfer transfer, UUID caseId, long expectedCaseVersion,
                        long expectedSourceVersion, long expectedTargetVersion, String reasonCode);
        /** Persist task + assignments + audit atomically with current-source/assignment guards. */
        void insertTask(InternshipTask task);
    }
    public interface Access {
        /** Resolve selected verified HR context + TermReference; enforce exact permission and assignment. */
        WorkspaceScope cohortCreationScope(Authentication actor, UUID termId);
        /** Revalidate account, membership, HR validity/revoke and t3:cohorts:manage in resource scope. */
        void requireManageGroup(Authentication actor, Cohort cohort, InternshipGroup group);
        /** Exact task_write permission plus current Mentor assignment for this group AND case. */
        void requireCreateTask(Authentication actor, Cohort cohort, InternshipGroup group, UUID caseId);
        /** Read permission + scoped assignment/self/delegation; filter each assignment separately. */
        boolean canReadAssignment(Authentication actor, Cohort cohort, InternshipTask task, UUID caseId);
    }
    public interface CaseChecks {
        /** Read authoritative Case scope/cohort/current version; lock/recheck through transaction commit. */
        void requireMembership(Authentication actor, Cohort cohort, UUID caseId, long expectedVersion);
        /** Return student identity only after current authoritative Case and Placement checks:
         * case belongs to scope/group, IN_PROGRESS, Placement ACTIVE, effectiveAt <= now.
         * An Activated event or cached projection is never sufficient. Prevent source races at commit.
         */
        UUID requireTaskParticipant(Authentication actor, Cohort cohort, InternshipGroup group, UUID caseId, Instant now);
    }
    /** Explicit safe option until the real adapter exists. Never returns fabricated Case data. */
    public static CaseChecks unavailableCases() {
        return new CaseChecks() {
            public void requireMembership(Authentication actor, Cohort cohort, UUID caseId, long version) {
                throw new WorkspaceRuleException(WorkspaceRuleException.Kind.UNAVAILABLE);
            }
            public UUID requireTaskParticipant(Authentication actor, Cohort cohort, InternshipGroup group,
                                                UUID caseId, Instant now) {
                throw new WorkspaceRuleException(WorkspaceRuleException.Kind.UNAVAILABLE);
            }
        };
    }
}
