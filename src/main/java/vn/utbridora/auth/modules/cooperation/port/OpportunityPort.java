package vn.utbridora.auth.modules.cooperation.port;

import java.time.Instant;
import vn.utbridora.auth.contracts.ActorContext;

/** T5-owned publication metadata consumed by T2's preference workflow. */
public interface OpportunityPort {
    record OpportunityReference(String opportunityId, String schoolOrgId,
            String enterpriseOrgId, String termId, Instant publishedAt,
            Instant closedAt, long version) {}

    /**
     * Reads an approved, published opportunity in the student's school/term,
     * including scheduled or closed publication windows. T2 decides whether the
     * window is open and maps a closed window to OPPORTUNITY_CLOSED.
     * Joins the caller's write transaction and holds the publication stable
     * until commit. Unknown, unpublished and foreign-scope records return 404.
     */
    OpportunityReference readForPreference(ActorContext actor, String opportunityId);
}
