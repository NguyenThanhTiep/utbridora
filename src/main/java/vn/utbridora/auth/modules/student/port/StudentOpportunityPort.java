package vn.utbridora.auth.modules.student.port;

import java.time.Instant;
import java.util.Optional;
import org.springframework.security.core.Authentication;

/**
 * BC22 consumer boundary for T2 Preference. T5 owns the Opportunity projection (opportunityId=cooperationId) and
 * provides the adapter; T2 never reads T5 tables. Until that adapter exists the fallback answers 503.
 */
public interface StudentOpportunityPort {
    record OpportunityRef(String opportunityId,String schoolOrgId,String enterpriseOrgId,String termId,Instant publishedAt,Instant closedAt,long version) {}
    /**
     * Published opportunity of an APPROVED cooperation in the actor student's school/term, including one already
     * past closedAt so T2 can answer OPPORTUNITY_CLOSED; empty when it is unknown, unpublished or out of scope.
     */
    Optional<OpportunityRef> publishedForStudent(Authentication student,String opportunityId);
}
