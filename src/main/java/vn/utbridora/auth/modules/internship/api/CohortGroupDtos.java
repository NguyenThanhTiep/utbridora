package vn.utbridora.auth.modules.internship.api;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** CP-WS1 v1.0 baseline values; v1.1 is supplementary technical reference only.
 * No HTTP binding, persistence or authorization is provided here.
 */
public final class CohortGroupDtos {
    private static final long MAX_VERSION = 9_007_199_254_740_991L;

    private CohortGroupDtos() {}

    /** Effective query values; omitted pagination defaults follow C.0. */
    public record CohortQuery(UUID termId, Integer page, Integer size) {
        public CohortQuery {
            page = CohortGroupDtos.page(page);
            size = pageSize(size);
        }
    }

    public record CreateCohort(UUID termId, String code, String name) {
        public CreateCohort {
            termId = required(termId, "termId");
            code = text(code, 64, "code");
            name = text(name, 200, "name");
        }
    }

    public record CohortDto(UUID cohortId, UUID enterpriseOrgId, UUID schoolOrgId,
                            UUID termId, String code, String name, Long version) {
        public CohortDto {
            cohortId = required(cohortId, "cohortId");
            enterpriseOrgId = required(enterpriseOrgId, "enterpriseOrgId");
            schoolOrgId = required(schoolOrgId, "schoolOrgId");
            termId = required(termId, "termId");
            code = text(code, 64, "code");
            name = text(name, 200, "name");
            version = CohortGroupDtos.version(version, "version");
        }
    }

    public record GroupDto(UUID groupId, UUID cohortId, String code, List<UUID> caseIds,
                           List<UUID> mentorUserIds, Long version) {
        public GroupDto {
            groupId = required(groupId, "groupId");
            cohortId = required(cohortId, "cohortId");
            code = text(code, 64, "code");
            caseIds = ids(caseIds, "caseIds");
            if (new HashSet<>(caseIds).size() != caseIds.size()) {
                throw new IllegalArgumentException("caseIds must be distinct");
            }
            // CP-WS1 v1.0 does not impose a distinct constraint on mentorUserIds.
            mentorUserIds = ids(mentorUserIds, "mentorUserIds");
            version = CohortGroupDtos.version(version, "version");
        }
    }

    public record AssignGroupMember(UUID caseId, Long expectedCaseVersion, Long expectedGroupVersion) {
        public AssignGroupMember {
            caseId = required(caseId, "caseId");
            expectedCaseVersion = version(expectedCaseVersion, "expectedCaseVersion");
            expectedGroupVersion = version(expectedGroupVersion, "expectedGroupVersion");
        }
    }

    public record MoveCase(UUID caseId, UUID toGroupId, Long expectedCaseVersion,
                           Long expectedFromGroupVersion, Long expectedToGroupVersion,
                           String reasonCode) {
        public MoveCase {
            caseId = required(caseId, "caseId");
            toGroupId = required(toGroupId, "toGroupId");
            expectedCaseVersion = version(expectedCaseVersion, "expectedCaseVersion");
            expectedFromGroupVersion = version(expectedFromGroupVersion, "expectedFromGroupVersion");
            expectedToGroupVersion = version(expectedToGroupVersion, "expectedToGroupVersion");
            if (reasonCode == null || !reasonCode.matches("[A-Z0-9_]{3,64}")) {
                throw new IllegalArgumentException("reasonCode must match Reason");
            }
        }
    }

    static <T> T required(T value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    static Long version(Long value, String field) {
        required(value, field);
        if (value < 0 || value > MAX_VERSION) {
            throw new IllegalArgumentException(field + " is outside contract V range");
        }
        return value;
    }

    static List<UUID> ids(List<UUID> values, String field) {
        required(values, field);
        if (values.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException(field + " must contain UUIDs");
        }
        return List.copyOf(values);
    }

    static String text(String value, int maximum, String field) {
        required(value, field);
        // Unicode whitespace, including NBSP; length is codepoints, not UTF-16 units.
        String normalized = value.replaceAll("(?U)^\\s+|\\s+$", "");
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > maximum) {
            throw new IllegalArgumentException(field + " is outside contract Text range");
        }
        return normalized;
    }

    static Integer page(Integer value) {
        if (value == null) return 0;
        if (value < 0 || value > 10_000) throw new IllegalArgumentException("page is outside contract range");
        return value;
    }

    static Integer pageSize(Integer value) {
        if (value == null) return 25;
        if (value < 1 || value > 100) throw new IllegalArgumentException("size is outside contract range");
        return value;
    }
}
