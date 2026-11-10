package vn.utbridora.auth.contracts.placement;

import java.time.*;
import java.util.Optional;

/** Internal T5 authority for T3. No HTTP callback and no client-provided Placement snapshot. */
public interface PlacementReadPort {
    record PlacementView(String placementId,String studentUserId,String schoolOrgId,String enterpriseOrgId,
        String termId,String demandId,String cooperationId,String proposalItemId,String state,long version,
        Instant effectiveAt,LocalDate startDate,LocalDate endDate,Instant approvedAt,Instant cancelledAt,
        Instant completedAt,String decisionRef) {}
    /** Must join the caller's transaction and keep the source stable through its commit. */
    Optional<PlacementView> readCurrentForGuard(String placementId);
    /** Establishes provenance from the T5 committed outbox, not merely producerModule text. */
    boolean authenticDelivery(TrustedEventContext context,PlacementLifecycleEvent event);
}
