package vn.utbridora.auth.modules.internship.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.utbridora.auth.modules.internship.api.CohortGroupDtos.CreateCohort;
import vn.utbridora.auth.modules.internship.api.TaskDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException.Kind.*;

class WorkspaceDomainTest {
    private final UUID enterprise = UUID.randomUUID(), school = UUID.randomUUID(), term = UUID.randomUUID();
    private final WorkspaceScope scope = new WorkspaceScope(enterprise, school, term);
    private final UUID cohort = UUID.randomUUID(), member = UUID.randomUUID();
    private InternshipGroup group(UUID id, List<UUID> members, long version) {
        return new InternshipGroup(id, cohort, " G ", members, List.of(), version);
    }
    private void fails(WorkspaceRuleException.Kind kind, org.junit.jupiter.api.function.Executable action) {
        assertEquals(kind, assertThrows(WorkspaceRuleException.class, action).kind());
    }
    @Test void cohortKeyIncludesEveryScopeDimensionAndNormalizedCode() {
        var a = Cohort.create(cohort, scope, new CreateCohort(term, " C ", " Name "));
        assertEquals(new Cohort.Key(scope, "C"), a.key());
        assertEquals("Name", a.name());
        assertEquals(1, a.version());
        assertNotEquals(a.key(), new Cohort.Key(new WorkspaceScope(UUID.randomUUID(), school, term), "C"));
        assertNotEquals(a.key(), new Cohort.Key(new WorkspaceScope(enterprise, UUID.randomUUID(), term), "C"));
        assertNotEquals(a.key(), new Cohort.Key(new WorkspaceScope(enterprise, school, UUID.randomUUID()), "C"));
        assertNotEquals(a.key(), new Cohort.Key(scope, "OTHER"));
    }
    @Test void cohortRejectsWrongTerm() {
        fails(SCOPE_MISMATCH, () -> Cohort.create(cohort, scope, new CreateCohort(UUID.randomUUID(), "C", "Name")));
    }
    @Test void invalidCohortNeverConstructed() {
        assertThrows(IllegalArgumentException.class, () -> new Cohort(cohort, scope, " ", "Name", 1));
        assertThrows(IllegalArgumentException.class, () -> new Cohort(cohort, scope, "C", "Name", -1));
    }
    @Test void scopeRejectsMissingDimension() {
        assertThrows(NullPointerException.class, () -> new WorkspaceScope(enterprise, null, term));
    }
    @Test void addProducesNewSnapshotAndIncrementsVersion() {
        var before = group(UUID.randomUUID(), List.of(), 4);
        var after = before.add(member, null, 4);
        assertEquals(List.of(member), after.caseIds());
        assertEquals(5, after.version());
        assertTrue(before.caseIds().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> after.caseIds().clear());
    }
    @Test void addRejectsMembershipInAnyCurrentGroup() {
        var g = group(UUID.randomUUID(), List.of(), 1);
        fails(GROUP_MEMBERSHIP_CONFLICT, () -> g.add(member, UUID.randomUUID(), 1));
    }
    @Test void addRejectsDuplicateEvenIfMembershipIndexIsInconsistent() {
        var g = group(UUID.randomUUID(), List.of(member), 1);
        fails(GROUP_MEMBERSHIP_CONFLICT, () -> g.add(member, null, 1));
    }
    @Test void staleVersionAndOverflowRejected() {
        fails(VERSION_CONFLICT, () -> group(UUID.randomUUID(), List.of(), 2).add(member, null, 1));
        fails(VERSION_CONFLICT, () -> group(UUID.randomUUID(), List.of(), 9_007_199_254_740_991L)
            .add(member, null, 9_007_199_254_740_991L));
    }
    @Test void movePreservesOriginalSnapshotsAndBumpsBothVersions() {
        var source = group(UUID.randomUUID(), List.of(member), 2);
        var target = group(UUID.randomUUID(), List.of(), 8);
        var result = source.moveTo(target, member, source.id(), 2, 8);
        assertTrue(result.source().caseIds().isEmpty());
        assertEquals(List.of(member), result.target().caseIds());
        assertEquals(3, result.source().version());
        assertEquals(9, result.target().version());
        assertEquals(List.of(member), source.caseIds());
        assertTrue(target.caseIds().isEmpty());
    }
    @Test void moveRejectsWrongSourceSameGroupAndDifferentCohort() {
        var source = group(UUID.randomUUID(), List.of(member), 2);
        var target = group(UUID.randomUUID(), List.of(), 8);
        fails(GROUP_MEMBERSHIP_CONFLICT, () -> source.moveTo(target, member, target.id(), 2, 8));
        fails(INVALID_OPERATION, () -> source.moveTo(source, member, source.id(), 2, 2));
        var foreign = new InternshipGroup(target.id(), UUID.randomUUID(), "G", List.of(), List.of(), 8);
        fails(SCOPE_MISMATCH, () -> source.moveTo(foreign, member, source.id(), 2, 8));
    }
    @Test void moveRejectsStaleTargetAndDuplicateTargetMember() {
        var source = group(UUID.randomUUID(), List.of(member), 2);
        var target = group(UUID.randomUUID(), List.of(member), 8);
        fails(VERSION_CONFLICT, () -> source.moveTo(target, member, source.id(), 2, 7));
        fails(GROUP_MEMBERSHIP_CONFLICT, () -> source.moveTo(target, member, source.id(), 2, 8));
    }
    @Test void taskInitialAssignmentsMustMatchInputAndBeNew() {
        UUID groupId = UUID.randomUUID(), student = UUID.randomUUID();
        var input = new CreateTask(groupId, "T", "D", Instant.EPOCH, TaskPriority.NORMAL, List.of(member), null);
        var assignment = new AssignmentDto(UUID.randomUUID(), member, student, AssignmentState.NEW, 1L, null);
        var task = InternshipTask.create(UUID.randomUUID(), input, List.of(assignment));
        assertTrue(task.value().lateAllowed());
        assertEquals(groupId, task.value().groupId());
        var started = new AssignmentDto(assignment.assignmentId(), member, student, AssignmentState.IN_PROGRESS, 1L, null);
        assertThrows(IllegalArgumentException.class, () -> InternshipTask.create(UUID.randomUUID(), input, List.of(started)));
        assertThrows(IllegalArgumentException.class, () -> InternshipTask.create(UUID.randomUUID(), input, List.of()));
    }
    @Test void taskRejectsDuplicateAssignmentIdentity() {
        UUID assignmentId = UUID.randomUUID(), student = UUID.randomUUID();
        var items = List.of(new AssignmentDto(assignmentId, member, student, AssignmentState.NEW, 1L, null),
            new AssignmentDto(assignmentId, UUID.randomUUID(), student, AssignmentState.NEW, 1L, null));
        var dto = new TaskDto(UUID.randomUUID(), UUID.randomUUID(), "T", "D", Instant.EPOCH,
            TaskPriority.NORMAL, true, 1L, items);
        assertThrows(IllegalArgumentException.class, () -> new InternshipTask(dto));
    }
    @Test void cohortKeyRejectsMissingScopeOrInvalidCodeAndKeepsCaseSensitiveEquality() {
        assertThrows(NullPointerException.class, () -> new Cohort.Key(null, "C"));
        assertThrows(IllegalArgumentException.class, () -> new Cohort.Key(scope, "\u00a0"));
        assertThrows(IllegalArgumentException.class, () -> new Cohort.Key(scope, "x".repeat(65)));
        assertEquals(new Cohort.Key(scope, "C"), new Cohort.Key(scope, "\u00a0C "));
        assertNotEquals(new Cohort.Key(scope, "C"), new Cohort.Key(scope, "c"));
    }
    @Test void taskCasesAreASetAndAssignmentOrderIsNotAnAuthorizationRule() {
        UUID second = UUID.randomUUID();
        var input = new CreateTask(UUID.randomUUID(), "T", "D", Instant.EPOCH,
            TaskPriority.LOW, List.of(member, second), false);
        var a = new AssignmentDto(UUID.randomUUID(), member, UUID.randomUUID(), AssignmentState.NEW, 1L, null);
        var b = new AssignmentDto(UUID.randomUUID(), second, UUID.randomUUID(), AssignmentState.NEW, 1L, null);
        var task = InternshipTask.create(UUID.randomUUID(), input, List.of(b, a));
        assertEquals(List.of(b, a), task.value().assignments());
        assertThrows(IllegalArgumentException.class, () -> InternshipTask.create(UUID.randomUUID(), input, List.of(a, a)));
    }
    @Test void taskRejectsNullElementsUnexpectedCaseAndPreexistingSubmission() {
        var input = new CreateTask(UUID.randomUUID(), "T", "D", Instant.EPOCH, TaskPriority.NORMAL, List.of(member), true);
        assertThrows(IllegalArgumentException.class, () -> InternshipTask.create(UUID.randomUUID(), input, null));
        assertThrows(IllegalArgumentException.class, () -> InternshipTask.create(UUID.randomUUID(), input,
            java.util.Arrays.asList((AssignmentDto) null)));
        var foreign = new AssignmentDto(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AssignmentState.NEW, 1L, null);
        assertThrows(IllegalArgumentException.class, () -> InternshipTask.create(UUID.randomUUID(), input, List.of(foreign)));
        var submitted = new AssignmentDto(UUID.randomUUID(), member, UUID.randomUUID(), AssignmentState.NEW, 1L, UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> InternshipTask.create(UUID.randomUUID(), input, List.of(submitted)));
    }
    @Test void failedTransferDoesNotModifyEitherSnapshotAtVersionOverflow() {
        var source = group(UUID.randomUUID(), List.of(member), 2);
        var target = group(UUID.randomUUID(), List.of(), 9_007_199_254_740_991L);
        fails(VERSION_CONFLICT, () -> source.moveTo(target, member, source.id(), 2, target.version()));
        assertEquals(List.of(member), source.caseIds());
        assertTrue(target.caseIds().isEmpty());
    }
}
