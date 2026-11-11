package vn.utbridora.auth.modules.internship.integration;

import vn.utbridora.auth.contracts.placement.EventConsumeResult;
import vn.utbridora.auth.contracts.placement.PlacementLifecycleEvent;
import vn.utbridora.auth.contracts.placement.TrustedEventContext;

/**
 * Internal T5-to-T3 service port specified by BC41 v1.0; no runtime is supplied here.
 *
 * <p>The server adapter must reject malformed input before calling this port and
 * verify that context represents the trusted T5 producer. Record validation does
 * not establish trust. Null arguments must be rejected by the receiving runtime.
 * Never expose this port as a browser callback or derive current state from eventType.
 *
 * <p>An implementation must read the authoritative T5 source, check scope/version,
 * deduplicate and commit inbox/Case/audit atomically before reporting ACK_APPLIED.
 * It must not create, update or approve Placement. Task eligibility still requires
 * its own current-source read and state/time/authorization checks at mutation time.
 */
@FunctionalInterface
public interface PlacementLifecycleConsumer {
    /**
     * @param context non-null server-verified T5 delivery context
     * @param event non-null payload whose value constraints passed constructor validation
     * @return non-null BC41 result describing actual processing, never a fabricated success
     */
    EventConsumeResult consume(TrustedEventContext context, PlacementLifecycleEvent event);
}
