package vn.utbridora.auth.modules.internship.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import vn.utbridora.auth.modules.internship.api.CohortGroupDtos.*;
import vn.utbridora.auth.modules.internship.domain.*;
import static vn.utbridora.auth.modules.internship.application.WorkspacePorts.*;
import static vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException.Kind.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/** Deterministic consumer interleavings with CAS/unique TEST adapters; not MySQL evidence. */
class WorkspaceConcurrencyTest {
    private final Session session = mock(Session.class);
    private final Access access = mock(Access.class);
    private final CaseChecks cases = mock(CaseChecks.class);
    private final WorkspaceScope scope = new WorkspaceScope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    private final Cohort cohort = new Cohort(UUID.randomUUID(), scope, "C", "Name", 1);
    private final org.springframework.security.core.Authentication actor =
        UsernamePasswordAuthenticationToken.authenticated("test-actor", null, List.of());
    private final UnitOfWork work = new UnitOfWork() {
        public <T> T execute(Function<Session, T> operation) { return operation.apply(session); }
    };
    private WorkspaceService service() { return new WorkspaceService(work, access, cases, Clock.systemUTC(), UUID::randomUUID); }
    private String outcome(Supplier<?> operation) {
        try { operation.get(); return "SUCCESS"; }
        catch (WorkspaceRuleException failure) { return failure.kind().name(); }
    }
    @Test void competingAddsFromSameSnapshotReturnOneSuccessAndOneVersionConflict() throws Exception {
        UUID groupId = UUID.randomUUID();
        var stored = new AtomicReference<>(new InternshipGroup(groupId, cohort.id(), "G", List.of(), List.of(), 1));
        when(session.group(groupId)).thenAnswer(call -> stored.get());
        when(session.cohort(cohort.id())).thenReturn(cohort);
        var bothRead = new CyclicBarrier(2);
        when(session.currentGroup(eq(cohort.id()), any())).thenAnswer(call -> {
            bothRead.await(5, TimeUnit.SECONDS);
            return null;
        });
        Object lock = new Object();
        doAnswer(call -> {
            InternshipGroup updated = call.getArgument(0);
            long expectedGroupVersion = call.getArgument(3);
            synchronized (lock) {
                if (stored.get().version() != expectedGroupVersion) throw new WorkspaceRuleException(VERSION_CONFLICT);
                stored.set(updated);
            }
            return null;
        }).when(session).addMember(any(), any(), anyLong(), anyLong());
        var service = service();
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = threads.submit(() -> outcome(() -> service.addMember(actor, groupId,
                new AssignGroupMember(UUID.randomUUID(), 1L, 1L))));
            var second = threads.submit(() -> outcome(() -> service.addMember(actor, groupId,
                new AssignGroupMember(UUID.randomUUID(), 1L, 1L))));
            var outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("SUCCESS"::equals).count());
            assertEquals(1, outcomes.stream().filter("VERSION_CONFLICT"::equals).count());
        }
        assertEquals(1, stored.get().caseIds().size());
        assertEquals(2, stored.get().version());
    }
    @Test void competingCreatesPastPrecheckStillPropagateAtomicDuplicate() throws Exception {
        var stored = new ConcurrentHashMap<Cohort.Key, Cohort>();
        var bothRead = new CyclicBarrier(2);
        when(access.cohortCreationScope(actor, scope.termId())).thenReturn(scope);
        when(session.cohortByKey(any())).thenAnswer(call -> {
            bothRead.await(5, TimeUnit.SECONDS);
            return null; // Both requests observed no row before either insert.
        });
        doAnswer(call -> {
            Cohort created = call.getArgument(0);
            if (stored.putIfAbsent(created.key(), created) != null) throw new WorkspaceRuleException(DUPLICATE_COHORT);
            return null;
        }).when(session).insertCohort(any());
        var service = service();
        var input = new CreateCohort(scope.termId(), "C", "Name");
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = threads.submit(() -> outcome(() -> service.createCohort(actor, input)));
            var second = threads.submit(() -> outcome(() -> service.createCohort(actor, input)));
            var outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("SUCCESS"::equals).count());
            assertEquals(1, outcomes.stream().filter("DUPLICATE_COHORT"::equals).count());
        }
        assertEquals(1, stored.size());
    }
}
