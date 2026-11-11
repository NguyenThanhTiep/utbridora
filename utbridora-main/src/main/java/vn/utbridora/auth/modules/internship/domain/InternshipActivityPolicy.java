package vn.utbridora.auth.modules.internship.domain;

import java.time.Instant;

/** State/time prerequisite from Contract Pack v1.0, CP-WS2 and BC41. */
public final class InternshipActivityPolicy {
    private InternshipActivityPolicy() {}

    /**
     * Checks only whether the case is IN_PROGRESS, the placement is ACTIVE and
     * its effective time has arrived. Missing or unrecognized inputs fail closed.
     *
     * <p>A true result is not authorization to create a task or submission.
     * Callers must independently verify identity, scope and assignment, re-read
     * the authoritative placement through the T5 port for each mutation, and
     * ensure transaction consistency. Local events or cached state are insufficient.
     * This method neither reads nor changes any case or placement.
     *
     * @param caseState exact case state code from CP-WS1
     * @param placementState exact placement state code from BC41
     * @param effectiveAt authoritative placement effective time
     * @param now server time captured by the caller, never a client-supplied time
     * @return whether the state/time prerequisite alone is satisfied
     */
    public static boolean hasRequiredStateAndStartTime(
            String caseState, String placementState, Instant effectiveAt, Instant now) {
        return "IN_PROGRESS".equals(caseState)
                && "ACTIVE".equals(placementState)
                && effectiveAt != null
                && now != null
                && !effectiveAt.isAfter(now);
    }
}
