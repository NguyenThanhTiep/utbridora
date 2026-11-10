package vn.utbridora.auth.modules.internship.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.modules.internship.api.CohortGroupDtos.*;
import vn.utbridora.auth.modules.internship.api.TaskDtos.*;
import vn.utbridora.auth.modules.internship.domain.*;
import static vn.utbridora.auth.modules.internship.application.WorkspacePorts.*;
import static vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException.Kind.*;

/** T3-02 application logic, wired by WorkspaceConfiguration to the transactional runtime adapters. */
public final class WorkspaceService {
    private final UnitOfWork transactions;
    private final Access access;
    private final CaseChecks cases;
    private final Clock clock;
    private final Supplier<UUID> identifiers;

    public WorkspaceService(UnitOfWork transactions, Access access, CaseChecks cases, Clock clock,
                            Supplier<UUID> identifiers) {
        this.transactions = Objects.requireNonNull(transactions);
        this.access = Objects.requireNonNull(access);
        this.cases = Objects.requireNonNull(cases);
        this.clock = Objects.requireNonNull(clock);
        this.identifiers = Objects.requireNonNull(identifiers);
    }
    public CohortDto createCohort(Authentication actor, CreateCohort input) {
        authenticated(actor);
        Objects.requireNonNull(input);
        return transactions.execute(session -> {
            var scope = access.cohortCreationScope(actor, input.termId());
            if (scope == null) throw new WorkspaceRuleException(UNAVAILABLE);
            var cohort = Cohort.create(identifiers.get(), scope, input);
            if (session.cohortByKey(cohort.key()) != null) {
                throw new WorkspaceRuleException(DUPLICATE_COHORT);
            }
            // The insert must still enforce uniqueness atomically against competing requests.
            session.insertCohort(cohort);
            return cohort.toDto();
        });
    }
    public GroupDto addMember(Authentication actor, UUID groupId, AssignGroupMember input) {
        authenticated(actor);
        Objects.requireNonNull(input);
        return transactions.execute(session -> {
            var group = loadGroup(session, groupId);
            var cohort = loadCohort(session, group.cohortId());
            access.requireManageGroup(actor, cohort, group);
            cases.requireMembership(actor, cohort, input.caseId(), input.expectedCaseVersion());
            var updated = group.add(input.caseId(), session.currentGroup(cohort.id(), input.caseId()),
                input.expectedGroupVersion());
            session.addMember(updated, input.caseId(), input.expectedCaseVersion(), input.expectedGroupVersion());
            return updated.toDto();
        });
    }
    public InternshipGroup.Transfer moveMember(Authentication actor, UUID fromGroupId, MoveCase input) {
        authenticated(actor);
        Objects.requireNonNull(input);
        return transactions.execute(session -> {
            var source = loadGroup(session, fromGroupId);
            var target = loadGroup(session, input.toGroupId());
            var cohort = loadCohort(session, source.cohortId());
            var targetCohort = loadCohort(session, target.cohortId());
            access.requireManageGroup(actor, cohort, source);
            access.requireManageGroup(actor, targetCohort, target);
            cohort.scope().requireSame(targetCohort.scope());
            cases.requireMembership(actor, cohort, input.caseId(), input.expectedCaseVersion());
            var transfer = source.moveTo(target, input.caseId(), session.currentGroup(cohort.id(), input.caseId()),
                input.expectedFromGroupVersion(), input.expectedToGroupVersion());
            session.moveMember(transfer, input.caseId(), input.expectedCaseVersion(),
                input.expectedFromGroupVersion(), input.expectedToGroupVersion(), input.reasonCode());
            return transfer;
        });
    }
    public TaskDto createTask(Authentication actor, CreateTask input) {
        authenticated(actor);
        Objects.requireNonNull(input);
        return transactions.execute(session -> {
            var group = loadGroup(session, input.groupId());
            var cohort = loadCohort(session, group.cohortId());
            var assignments = new ArrayList<AssignmentDto>();
            var now = clock.instant();
            for (var caseId : input.caseIds()) {
                access.requireCreateTask(actor, cohort, group, caseId);
                if (!group.caseIds().contains(caseId)
                        || !group.id().equals(session.currentGroup(cohort.id(), caseId))) {
                    throw new WorkspaceRuleException(GROUP_MEMBERSHIP_CONFLICT);
                }
                var student = cases.requireTaskParticipant(actor, cohort, group, caseId, now);
                if (student == null) throw new WorkspaceRuleException(UNAVAILABLE);
                assignments.add(new AssignmentDto(identifiers.get(), caseId, student, AssignmentState.NEW, 1L, null));
            }
            var task = InternshipTask.create(identifiers.get(), input, assignments);
            session.insertTask(task);
            return task.value();
        });
    }
    public TaskDto readTask(Authentication actor, UUID taskId) {
        authenticated(actor);
        return transactions.execute(session -> {
            var task = found(session.task(Objects.requireNonNull(taskId)));
            if (!taskId.equals(task.value().taskId())) throw new WorkspaceRuleException(UNAVAILABLE);
            var group = loadGroup(session, task.value().groupId());
            var cohort = loadCohort(session, group.cohortId());
            var visible = task.value().assignments().stream()
                .filter(a -> access.canReadAssignment(actor, cohort, task, a.caseId())).toList();
            if (visible.isEmpty()) throw new WorkspaceRuleException(DENIED);
            var value = task.value();
            return new TaskDto(value.taskId(), value.groupId(), value.title(), value.description(), value.dueAt(),
                value.priority(), value.lateAllowed(), value.version(), visible);
        });
    }
    private static void authenticated(Authentication actor) {
        if (actor == null || !actor.isAuthenticated()
                || actor instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            throw new WorkspaceRuleException(UNAUTHENTICATED);
        }
    }
    private static <T> T found(T value) {
        if (value == null) throw new WorkspaceRuleException(NOT_FOUND);
        return value;
    }
    private static InternshipGroup loadGroup(Session session, UUID id) {
        var group = found(session.group(Objects.requireNonNull(id, "groupId")));
        if (!id.equals(group.id())) throw new WorkspaceRuleException(UNAVAILABLE);
        return group;
    }
    private static Cohort loadCohort(Session session, UUID id) {
        var cohort = found(session.cohort(id));
        if (!id.equals(cohort.id())) throw new WorkspaceRuleException(UNAVAILABLE);
        return cohort;
    }
}
