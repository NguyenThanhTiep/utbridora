package vn.utbridora.auth.modules.cooperation.integration;

import vn.utbridora.auth.modules.cooperation.domain.DemandModels.DemandInput;

/** Owner adapters must validate current school/term relationships, catalog versions and canonical skill codes.
 * Implementations must fail closed on missing/stale dependencies; a permissive production adapter is not valid.
 */
public interface DemandReferencePort {
    void validate(String enterpriseOrgId,DemandInput input);
}
