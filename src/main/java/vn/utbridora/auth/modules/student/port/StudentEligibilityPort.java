package vn.utbridora.auth.modules.student.port;

import org.springframework.security.core.Authentication;
public interface StudentEligibilityPort {
    /** Used during nomination (matching phase). */
    void assertEligibleForNomination(Authentication actor, String student, String school, String term);

    /**
     * Used during Placement create/approve (BC21).
     * Locks the eligibility record until commit; throws OperationFailure on stale version,
     * unapproved or term mismatch.
     */
    void assertEligibleForPlacement(Authentication actor, String student, String school,
                                     String term, long expectedVersion);
}
