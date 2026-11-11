package vn.utbridora.auth.modules.internship.contract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.modules.internship.api.CohortGroupDtos.CohortDto;
import vn.utbridora.auth.modules.internship.api.CohortGroupDtos.GroupDto;
import vn.utbridora.auth.modules.internship.domain.CohortGroupPolicy;
import vn.utbridora.auth.modules.internship.domain.Cohort;
import vn.utbridora.auth.modules.internship.domain.InternshipGroup;
import vn.utbridora.auth.modules.internship.domain.WorkspaceScope;
import static org.junit.jupiter.api.Assertions.*;

/** DEV07 consumer compatibility with DEV06 DTOs/domain, not HTTP/authorization evidence.
 * The same synthetic JSON is consumed by the frontend scope and browser tests.
 */
class WorkspaceFrontendContractTest {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();

    private static JsonNode fixture() throws IOException {
        return JSON.readTree(Files.readString(Path.of(System.getProperty("basedir", "."),
                "web-demo", "tests", "fixtures", "internship-wire.json")));
    }

    @Test void frontendCohortFixtureRoundTripsThroughOwnerDtoWithoutLosingFields() throws IOException {
        var expected = fixture().get("cohort");
        var cohort = JSON.treeToValue(expected, CohortDto.class);
        assertEquals(expected, JSON.readTree(JSON.writeValueAsString(cohort)));
        assertNotEquals(cohort.enterpriseOrgId(), cohort.schoolOrgId());
        assertNotEquals(cohort.schoolOrgId(), cohort.termId());
        assertTrue(CohortGroupPolicy.matchesScope(cohort,
                cohort.schoolOrgId(), cohort.enterpriseOrgId(), cohort.termId()));
        // Exercise DEV06's new domain-to-wire conversion against the very same
        // fixture used by frontend scope selection; swapped scope IDs fail here.
        var domain = new Cohort(cohort.cohortId(),
                new WorkspaceScope(cohort.enterpriseOrgId(), cohort.schoolOrgId(), cohort.termId()),
                cohort.code(), cohort.name(), cohort.version());
        assertEquals(expected, JSON.readTree(JSON.writeValueAsString(domain.toDto())));
    }

    @Test void frontendGroupFixturesPreserveListsAndVersionBoundariesOfOwnerDto() throws IOException {
        var examples = fixture();
        var cohort = JSON.treeToValue(examples.get("cohort"), CohortDto.class);
        for (String name : List.of("group", "groupWithRepeatedMentors", "emptyGroup")) {
            var expected = examples.get(name);
            var group = JSON.treeToValue(expected, GroupDto.class);
            assertEquals(expected, JSON.readTree(JSON.writeValueAsString(group)), name);
            assertTrue(CohortGroupPolicy.belongsToCohort(cohort, group), name);
            var domain = new InternshipGroup(group.groupId(), group.cohortId(), group.code(),
                    group.caseIds(), group.mentorUserIds(), group.version());
            assertEquals(expected, JSON.readTree(JSON.writeValueAsString(domain.toDto())), name);
        }
        var repeated = JSON.treeToValue(examples.get("groupWithRepeatedMentors"), GroupDto.class);
        assertEquals(3, repeated.mentorUserIds().size());
        assertEquals(2, repeated.mentorUserIds().stream().distinct().count());
        assertEquals(9_007_199_254_740_991L, repeated.version());
        var empty = JSON.treeToValue(examples.get("emptyGroup"), GroupDto.class);
        assertEquals(0L, empty.version());
        assertTrue(empty.caseIds().isEmpty());
        assertTrue(empty.mentorUserIds().isEmpty());
    }
}
