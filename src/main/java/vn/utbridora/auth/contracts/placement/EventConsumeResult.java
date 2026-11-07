package vn.utbridora.auth.contracts.placement;

import java.util.Set;

/**
 * BC41 internal delivery result, not an HTTP response or proof of a committed mutation.
 *
 * <p>Only declared v1.0 value constraints are enforced here. Outcome/reason pairing,
 * retry scheduling and applied-version semantics belong to the receiving runtime.
 * Nullable fields remain nullable; v1.0 declares retryAfterSeconds as int or null.
 */
public record EventConsumeResult(
        String eventId,
        String outcome,
        Long appliedAggregateVersion,
        String reasonCode,
        Integer retryAfterSeconds) {

    private static final long MAX_VERSION = 9_007_199_254_740_991L;
    private static final Set<String> OUTCOMES = Set.of(
            "ACK_APPLIED", "ACK_DUPLICATE", "ACK_STALE", "RETRY", "REJECT");
    private static final Set<String> REASONS = Set.of(
            "APPLIED", "DUPLICATE", "STALE", "SOURCE_NOT_FOUND", "SCOPE_MISMATCH",
            "INVALID_SCHEMA", "SOURCE_UNAVAILABLE", "STATE_CONFLICT");

    public EventConsumeResult {
        PlacementLifecycleEvent.requireUuid(eventId, "eventId");
        if (outcome == null || !OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("Invalid outcome");
        }
        if (reasonCode == null || !REASONS.contains(reasonCode)) {
            throw new IllegalArgumentException("Invalid reasonCode");
        }
        if (appliedAggregateVersion != null
                && (appliedAggregateVersion < 0 || appliedAggregateVersion > MAX_VERSION)) {
            throw new IllegalArgumentException("Invalid appliedAggregateVersion");
        }
    }
}
