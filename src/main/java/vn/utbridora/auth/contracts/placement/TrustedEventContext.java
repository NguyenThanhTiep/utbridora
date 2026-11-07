package vn.utbridora.auth.contracts.placement;

import java.util.Set;

/**
 * Server-created BC41 delivery metadata; never bind this record from a browser body.
 *
 * <p>Constructor validation checks syntax only, not trust or permissions. The server
 * adapter must authenticate the producer and establish its allowed operation/scope.
 * T3 and T5 are both valid in the shared contract; Placement delivery to T3 requires T5.
 */
public record TrustedEventContext(String producerModule, String deliveryOperationId, String requestId) {
    private static final Set<String> PRODUCERS = Set.of("T3", "T5");

    public TrustedEventContext {
        if (producerModule == null || !PRODUCERS.contains(producerModule)) {
            throw new IllegalArgumentException("Invalid producerModule");
        }
        PlacementLifecycleEvent.requireUuid(deliveryOperationId, "deliveryOperationId");
        PlacementLifecycleEvent.requireUuid(requestId, "requestId");
    }
}
