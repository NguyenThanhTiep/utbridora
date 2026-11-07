package vn.utbridora.auth.modules.internship.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import vn.utbridora.auth.modules.internship.api.CohortGroupDtos.GroupDto;
import static vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException.Kind.*;

/** Immutable group snapshot. The adapter enforces cohort-wide membership uniqueness atomically. */
public record InternshipGroup(UUID id, UUID cohortId, String code, List<UUID> caseIds,
                              List<UUID> mentorUserIds, long version) {
    public InternshipGroup {
        var validated = new GroupDto(id, cohortId, code, caseIds, mentorUserIds, version);
        code = validated.code();
        caseIds = validated.caseIds();
        mentorUserIds = validated.mentorUserIds();
    }
    public void requireVersion(long expected) {
        if (expected != version) throw new WorkspaceRuleException(VERSION_CONFLICT);
    }
    public InternshipGroup add(UUID caseId, UUID currentGroupId, long expectedVersion) {
        Objects.requireNonNull(caseId, "caseId");
        requireVersion(expectedVersion);
        if (currentGroupId != null || caseIds.contains(caseId)) {
            throw new WorkspaceRuleException(GROUP_MEMBERSHIP_CONFLICT);
        }
        var members = new ArrayList<>(caseIds);
        members.add(caseId);
        return copy(members);
    }
    public Transfer moveTo(InternshipGroup target, UUID caseId, UUID currentGroupId,
                           long expectedSourceVersion, long expectedTargetVersion) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(caseId, "caseId");
        requireVersion(expectedSourceVersion);
        target.requireVersion(expectedTargetVersion);
        if (!cohortId.equals(target.cohortId)) throw new WorkspaceRuleException(SCOPE_MISMATCH);
        if (id.equals(target.id)) throw new WorkspaceRuleException(INVALID_OPERATION);
        if (!id.equals(currentGroupId) || !caseIds.contains(caseId) || target.caseIds.contains(caseId)) {
            throw new WorkspaceRuleException(GROUP_MEMBERSHIP_CONFLICT);
        }
        var sourceMembers = new ArrayList<>(caseIds);
        sourceMembers.remove(caseId);
        var targetMembers = new ArrayList<>(target.caseIds);
        targetMembers.add(caseId);
        return new Transfer(copy(sourceMembers), target.copy(targetMembers));
    }
    private InternshipGroup copy(List<UUID> members) {
        if (version == 9_007_199_254_740_991L) throw new WorkspaceRuleException(VERSION_CONFLICT);
        return new InternshipGroup(id, cohortId, code, members, mentorUserIds, version + 1);
    }
    public GroupDto toDto() { return new GroupDto(id, cohortId, code, caseIds, mentorUserIds, version); }
    public record Transfer(InternshipGroup source, InternshipGroup target) {}
}
