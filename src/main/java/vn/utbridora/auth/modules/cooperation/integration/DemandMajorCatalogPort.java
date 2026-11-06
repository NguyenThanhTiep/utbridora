package vn.utbridora.auth.modules.cooperation.integration;

import java.util.Set;

/** T2-owned academic source. Provider must check authorized scope and return one consistent
 * current catalog snapshot, stable until the caller's transaction completes (use the
 * owner's catalog mutation lock/version mechanism). No local list of majors substitutes for this source.
 * Snapshot contains ACTIVE canonical major codes only, preserving case.
 */
public interface DemandMajorCatalogPort {
    record Catalog(String schoolOrgId,String termId,String version,Set<String> activeMajorCodes) {
        public Catalog { activeMajorCodes=Set.copyOf(activeMajorCodes); }
    }
    Catalog read(String enterpriseOrgId,String schoolOrgId,String termId);
}
