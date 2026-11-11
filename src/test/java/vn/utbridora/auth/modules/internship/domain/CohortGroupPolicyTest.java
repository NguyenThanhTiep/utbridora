package vn.utbridora.auth.modules.internship.domain;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.utbridora.auth.modules.internship.api.CohortGroupDtos.*;
import static org.junit.jupiter.api.Assertions.*;

class CohortGroupPolicyTest {
    private static final UUID COHORT = UUID.fromString("00000000-0000-4000-8000-000000000061");
    private static final UUID SCHOOL = UUID.fromString("00000000-0000-4000-8000-000000000011");
    private static final UUID ENTERPRISE = UUID.fromString("00000000-0000-4000-8000-000000000012");
    private static final UUID TERM = UUID.fromString("00000000-0000-4000-8000-000000000021");
    private static final UUID CASE = UUID.fromString("00000000-0000-4000-8000-000000000031");
    private static final CohortDto VIEW = new CohortDto(COHORT, ENTERPRISE, SCHOOL, TERM, "C", "N", 1L);

    @Test void everyScopeDimensionMustMatchWithoutNullWildcard() {
        assertTrue(CohortGroupPolicy.matchesScope(VIEW, SCHOOL, ENTERPRISE, TERM));
        assertFalse(CohortGroupPolicy.matchesScope(VIEW, CASE, ENTERPRISE, TERM));
        assertFalse(CohortGroupPolicy.matchesScope(VIEW, SCHOOL, CASE, TERM));
        assertFalse(CohortGroupPolicy.matchesScope(VIEW, SCHOOL, ENTERPRISE, CASE));
        assertFalse(CohortGroupPolicy.matchesScope(VIEW, null, ENTERPRISE, TERM));
        assertFalse(CohortGroupPolicy.matchesScope(VIEW, SCHOOL, null, TERM));
        assertFalse(CohortGroupPolicy.matchesScope(VIEW, SCHOOL, ENTERPRISE, null));
        assertFalse(CohortGroupPolicy.matchesScope(null, SCHOOL, ENTERPRISE, TERM));
    }

    @Test void groupMustBelongToSelectedCohort() {
        assertTrue(CohortGroupPolicy.belongsToCohort(VIEW, group(COHORT, List.of())));
        assertFalse(CohortGroupPolicy.belongsToCohort(VIEW, group(CASE, List.of())));
        assertFalse(CohortGroupPolicy.belongsToCohort(VIEW, null));
        assertFalse(CohortGroupPolicy.belongsToCohort(null, group(COHORT, List.of())));
    }

    @Test void existingMembershipInSameOrAnotherGroupBlocksAddition() {
        var empty = group(COHORT, List.of());
        assertTrue(CohortGroupPolicy.canAddMember(empty, CASE, null));
        assertFalse(CohortGroupPolicy.canAddMember(empty, CASE, empty.groupId()));
        assertFalse(CohortGroupPolicy.canAddMember(empty, CASE, SCHOOL));
        assertFalse(CohortGroupPolicy.canAddMember(group(COHORT, List.of(CASE)), CASE, null));
        assertFalse(CohortGroupPolicy.canAddMember(empty, null, null));
        assertFalse(CohortGroupPolicy.canAddMember(null, CASE, null));
    }

    @Test void staleMissingAndInvalidVersionsDoNotMatch() {
        assertTrue(CohortGroupPolicy.matchesVersion(0L, 0L));
        assertTrue(CohortGroupPolicy.matchesVersion(9_007_199_254_740_991L, 9_007_199_254_740_991L));
        assertFalse(CohortGroupPolicy.matchesVersion(1L, 2L));
        assertFalse(CohortGroupPolicy.matchesVersion(null, 1L));
        assertFalse(CohortGroupPolicy.matchesVersion(1L, null));
        assertFalse(CohortGroupPolicy.matchesVersion(-1L, -1L));
        assertFalse(CohortGroupPolicy.matchesVersion(9_007_199_254_740_992L, 9_007_199_254_740_992L));
    }

    private static GroupDto group(UUID cohortId, List<UUID> cases) {
        return new GroupDto(ENTERPRISE, cohortId, "G", cases, List.of(), 1L);
    }
}
