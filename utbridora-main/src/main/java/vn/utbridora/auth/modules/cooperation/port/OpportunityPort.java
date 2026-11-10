package vn.utbridora.auth.modules.cooperation.port;

import java.time.Instant;
import java.util.List;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.DateRange;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page;

/** T5 owns the opportunity projection; consumers cannot derive it from demand or cooperation tables. */
public interface OpportunityPort {
    record OpportunityView(String opportunityId, String cooperationId, String demandId, String schoolOrgId,
                           String enterpriseOrgId, String termId, String enterpriseName, String title,
                           List<String> majorCodes, DateRange dateRange, String benefits, String location,
                           Instant publishedAt, Instant closedAt, long version) {
        public OpportunityView {
            majorCodes = List.copyOf(majorCodes);
        }
    }

    record Query(String termId, String q, String majorCode, String enterpriseOrgId, int page, int size) {}

    Page<OpportunityView> listMine(Authentication actor, Query request);
    OpportunityView readMine(Authentication actor, String opportunityId);
    OpportunityView readForPreference(Authentication actor, String opportunityId);
}
