package vn.utbridora.auth.modules.cooperation.integration;

import vn.utbridora.auth.modules.cooperation.domain.DemandModels.DemandInput;

/** Owner T5/academic provider validates school, term, majors and academicCatalogVersion.
 * No default permissive implementation. This seam must not duplicate their registries.
 */
public interface DemandAcademicPort {
    void validate(String enterpriseOrgId,DemandInput input);
}
