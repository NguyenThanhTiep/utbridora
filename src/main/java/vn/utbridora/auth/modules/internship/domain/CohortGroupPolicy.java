package vn.utbridora.auth.modules.internship.domain;

import java.util.Objects;
import java.util.UUID;
import vn.utbridora.auth.modules.internship.api.CohortGroupDtos.CohortDto;
import vn.utbridora.auth.modules.internship.api.CohortGroupDtos.GroupDto;

/** CP-WS1 predicates on supplied snapshots, never an authorization or concurrency guard.
 * Callers must obtain authoritative data and recheck/lock it in the eventual transaction.
 */
public final class CohortGroupPolicy {
    private CohortGroupPolicy() {}

    public static boolean matchesScope(CohortDto cohort, UUID schoolOrgId,
                                       UUID enterpriseOrgId, UUID termId) {
        return cohort != null
                && cohort.schoolOrgId().equals(schoolOrgId)
                && cohort.enterpriseOrgId().equals(enterpriseOrgId)
                && cohort.termId().equals(termId);
    }

    public static boolean belongsToCohort(CohortDto cohort, GroupDto group) {
        return cohort != null && group != null && cohort.cohortId().equals(group.cohortId());
    }

    /** currentActiveGroupId must be the current membership in this cohort, not another cohort. */
    public static boolean canAddMember(GroupDto group, UUID caseId, UUID currentActiveGroupId) {
        return group != null && caseId != null && currentActiveGroupId == null
                && !group.caseIds().contains(caseId);
    }

    public static boolean matchesVersion(Long expected, Long current) {
        return expected != null && expected >= 0 && expected <= 9_007_199_254_740_991L
                && Objects.equals(expected, current);
    }
}
