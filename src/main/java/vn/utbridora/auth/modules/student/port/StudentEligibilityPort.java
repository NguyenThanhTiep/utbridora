package vn.utbridora.auth.modules.student.port;

import org.springframework.security.core.Authentication;
public interface StudentEligibilityPort {
    record NominationEligibility(String studentUserId,String studentCode,String majorCode,long version,boolean eligible,String reasonCode) {}
    default NominationEligibility readForNomination(Authentication actor,String student,String school,String term) {
        throw vn.utbridora.auth.operations.WorkflowValues.failure(503,"DEPENDENCY_UNAVAILABLE");
    }
    void assertEligibleForNomination(Authentication actor,String student,String school,String term);

    /**
     * Used during Placement create/approve (BC21).
     * Locks the eligibility record until commit; throws OperationFailure on stale version,
     * unapproved or term mismatch.
     */
    void assertEligibleForPlacement(Authentication actor, String student, String school,
                                     String term, long expectedVersion);
    default void assertCurrentForPlacement(Authentication actor,String student,String school,String term) {
        throw vn.utbridora.auth.operations.WorkflowValues.failure(503,"DEPENDENCY_UNAVAILABLE");
    }
}
