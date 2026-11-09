package vn.utbridora.auth.modules.cooperation.integration;

import org.springframework.security.core.Authentication;

import vn.utbridora.auth.modules.cooperation.domain.DemandModels.DemandCriteriaSnapshot;

/** In-process evaluation seam. Authentication is server-owned; no browser supplied ActorContext. */
public interface DemandCriteriaPort {
    enum Purpose { EVALUATE, NOMINATE, PLACEMENT }
    DemandCriteriaSnapshot read(Authentication actor,String enterpriseOrgId,String demandId,Long expectedCriteriaVersion,Purpose purpose);
    default DemandCriteriaSnapshot readForSchool(Authentication actor,String demandId,Long expectedCriteriaVersion) {
        throw vn.utbridora.auth.operations.WorkflowValues.failure(503,"DEPENDENCY_UNAVAILABLE");
    }
}
