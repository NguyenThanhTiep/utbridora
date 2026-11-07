package vn.utbridora.auth.modules.internship.domain;

import vn.utbridora.auth.contracts.placement.PlacementLifecycleEvent;

/** Event type/version prerequisites from Contract Pack v1.0, C.0 and BC41. */
public final class PlacementEventEnvelopePolicy {
    private PlacementEventEnvelopePolicy() {}

    /**
     * Checks the exact lifecycle event type, schema version 1 and equal aggregate/
     * placement versions within the inclusive C.0 range. Missing inputs fail closed.
     *
     * <p>This pure check is not full envelope validation or permission to apply an
     * event. The receiving adapter must validate the wire types, required/unknown
     * fields, identifiers and trusted context. Source re-read, scope, deduplication,
     * state transitions and transactional persistence remain separate responsibilities.
     * No data is read or changed and no ACK/error result is emitted here.
     *
     * @param eventType exact BC41 lifecycle event name, without normalization
     * @param schemaVersion event schema version, not the contract pack version
     * @param aggregateVersion aggregate version from the event
     * @param placementVersion placement version from the event
     * @return whether the event type/version prerequisites alone are satisfied
     */
    public static boolean hasSupportedTypeAndVersions(
            String eventType, Integer schemaVersion, Long aggregateVersion, Long placementVersion) {
        return PlacementLifecycleEvent.hasSupportedTypeAndVersions(
                eventType, schemaVersion, aggregateVersion, placementVersion);
    }
}
