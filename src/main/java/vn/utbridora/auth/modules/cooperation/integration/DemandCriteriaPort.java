package vn.utbridora.auth.modules.cooperation.integration;

import org.springframework.security.core.Authentication;

import vn.utbridora.auth.modules.cooperation.domain.DemandModels.DemandCriteriaSnapshot;

/** In-process evaluation seam. Authentication is server-owned; no browser supplied ActorContext. */
public interface DemandCriteriaPort {
    enum Purpose { EVALUATE, NOMINATE, PLACEMENT }
    DemandCriteriaSnapshot read(Authentication actor,String enterpriseOrgId,String demandId,Long expectedCriteriaVersion,Purpose purpose);
}
