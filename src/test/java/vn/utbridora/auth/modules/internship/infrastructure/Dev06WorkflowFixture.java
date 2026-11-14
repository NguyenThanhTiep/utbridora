package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.*;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.modules.internship.api.ReportDtos.*;
import vn.utbridora.auth.modules.internship.api.SubmissionDtos.*;
import vn.utbridora.auth.modules.internship.api.TaskDtos.*;
import vn.utbridora.auth.modules.internship.application.*;
import vn.utbridora.auth.modules.internship.domain.*;
import vn.utbridora.auth.operations.OperationLedger;
import vn.utbridora.auth.security.AppPrincipal;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** Synthetic DEV06 scenarios only. Reuses the existing Spring callback manager and REQUIRED
 * adapters. Memory locking/rollback is deliberately NOT evidence of MySQL isolation/durability.
 * No Spring context, bean, migration, external scanner or production grant is installed.
 */
class Dev06WorkflowFixture extends WeeklyReportAdapterTestSupport {
    final MutableClock clock = new MutableClock(now);
    final Store store = new Store();
    final FakeFileReadPort files = new FakeFileReadPort();
    final Set<UUID> revoked = new HashSet<>();
    final Set<String> deniedPermissions = new HashSet<>();
    final UUID outsider = UUID.randomUUID(), assignmentId = UUID.randomUUID();
    final Authentication studentActor = auth(student), mentorActor = auth(mentor);
    WeeklyReportPorts.CaseScope currentScope = scope;
    boolean mentorAssigned = true, caseFenceAvailable = true, assignmentFenceAvailable = true;
    Instant mentorValidUntil = Instant.MAX;
    Runnable duringCaseFence = () -> {};
    Runnable duringPermission = () -> {};
    TaskCommentPorts.Target target = sampleTarget();
    final WorkspacePorts.UnitOfWork workspace;
    final WeeklyReportPorts.UnitOfWork reportTransactions;
    final WeeklyReportService reports;
    final TaskCommentService comments;
    final WeeklyReportCommands reportCommands;
    final TaskCommentCommands commentCommands;
    final OperationLedger ledger = mock(OperationLedger.class);

    Dev06WorkflowFixture() {
        manager.setRollbackOnCommitFailure(true);
        var required = new TransactionalWorkspaceUnitOfWork(manager, () -> mock(WorkspacePorts.Session.class));
        workspace = new WorkspacePorts.UnitOfWork() {
            public <T> T execute(Function<WorkspacePorts.Session, T> operation) {
                return required.execute(session -> { store.enlist(); return operation.apply(session); });
            }
        };
        reportTransactions = new WeeklyReportUnitOfWork(workspace, () -> store);
        var cases = new WeeklyReportPorts.Cases() {
            public WeeklyReportPorts.CaseScope current(UUID id) {
                if (!id.equals(currentScope.caseId())) throw failure(404, "RESOURCE_NOT_FOUND");
                return currentScope;
            }
            public void holdForMutation(WeeklyReportPorts.CaseScope expected) {
                if (!caseFenceAvailable) throw failure(503, "DEPENDENCY_UNAVAILABLE");
                duringCaseFence.run();
                if (!expected.equals(currentScope)) throw failure(409, "VERSION_CONFLICT");
            }
        };
        var authority = new WeeklyReportPorts.Authority() {
            public UUID currentUser(Authentication actor) { return identity(actor); }
            public void requirePermission(Authentication actor, WeeklyReportPorts.CaseScope value, String permission, Instant at) {
                identity(actor);
                duringPermission.run();
                if (deniedPermissions.contains(permission)) throw failure(403, "PERMISSION_DENIED");
                if (!scope.schoolOrgId().equals(value.schoolOrgId()) || !scope.enterpriseOrgId().equals(value.enterpriseOrgId())
                        || !scope.termId().equals(value.termId())) throw failure(404, "RESOURCE_NOT_FOUND");
            }
        };
        var reportAssignments = new WeeklyReportPorts.Assignments() {
            public boolean isCurrentMentor(Authentication actor, WeeklyReportPorts.CaseScope value, Instant at) {
                return mentorAssigned && at.isBefore(mentorValidUntil) && identity(actor).equals(mentor);
            }
            public boolean canRead(Authentication actor, WeeklyReportPorts.CaseScope value, Instant at) {
                return isCurrentMentor(actor, value, at);
            }
        };
        var commentAccess = new TaskCommentPorts.Access() {
            public UUID currentUser(Authentication actor) { return identity(actor); }
            public void requireWrite(Authentication actor, TaskCommentPorts.Target value, WeeklyReportPorts.CaseScope context, Instant at) {
                authority.requirePermission(actor, context, "t3:comments:write", at);
                participant(actor);
            }
            public void requireRead(Authentication actor, TaskCommentPorts.Target value, WeeklyReportPorts.CaseScope context, Instant at) {
                authority.requirePermission(actor, context, "t3:workspace:read", at);
                participant(actor);
            }
            private void participant(Authentication actor) {
                var user = identity(actor);
                if (!user.equals(student) && !(user.equals(mentor) && mentorAssigned && clock.instant().isBefore(mentorValidUntil)))
                    throw failure(404, "RESOURCE_NOT_FOUND");
            }
        };
        var commentAssignments = new TaskCommentPorts.Assignments() {
            public TaskCommentPorts.Target current(UUID id) { return assignmentId.equals(id) ? target : null; }
            public TaskCommentPorts.Target holdForMutation(TaskCommentPorts.Target expected) {
                if (!assignmentFenceAvailable) throw failure(503, "DEPENDENCY_UNAVAILABLE");
                return target;
            }
        };
        reports = new WeeklyReportService(reportTransactions, cases, authority, reportAssignments, files, store, clock, UUID::randomUUID);
        comments = new TaskCommentService(workspace, () -> store, commentAssignments, cases,
                (actor, value, expected, at) -> cases.holdForMutation(expected), commentAccess, store, clock, UUID::randomUUID);
        // Only the already-committed replay behavior needed by these consumer scenarios is fake.
        // Real payload hashing is reused; expiry/reservation/DB races remain T1 runtime tests.
        doAnswer(invocation -> store.execute(invocation.getArgument(0), invocation.getArgument(1),
                invocation.getArgument(2), invocation.getArgument(3)))
                .when(ledger).execute(any(), any(), any(), any());
        reportCommands = new WeeklyReportCommands(reportTransactions, reports, ledger);
        commentCommands = new TaskCommentCommands(workspace, comments, ledger);
    }

    private UUID identity(Authentication actor) {
        if (actor == null || !actor.isAuthenticated() || !(actor.getPrincipal() instanceof AppPrincipal principal)
                || principal.securityVersion() != 4) throw failure(401, "SESSION_EXPIRED");
        var user = UUID.fromString(principal.userId());
        if (revoked.contains(user)) throw failure(401, "SESSION_EXPIRED");
        return user;
    }
    private TaskCommentPorts.Target sampleTarget() {
        var cohort = new Cohort(UUID.randomUUID(), new WorkspaceScope(enterprise, school, term), "SYN", "Synthetic cohort", 1);
        var group = new InternshipGroup(UUID.randomUUID(), cohort.id(), "Synthetic group", List.of(caseId), List.of(mentor), 1);
        var assignment = new AssignmentDto(assignmentId, caseId, student, AssignmentState.NEW, 3L, null);
        var task = new InternshipTask(new TaskDto(UUID.randomUUID(), group.id(), "Synthetic task", "Unit fixture only",
                now.plusSeconds(3600), TaskPriority.NORMAL, true, 1L, List.of(assignment)));
        return new TaskCommentPorts.Target(cohort, group, task, assignment);
    }
    ReportInput sampleReport() {
        return new ReportInput(caseId, LocalDate.parse("2026-11-02"), "Implemented report domain", "Awaiting owner adapters",
                "Integrate approved persistence", List.of());
    }
    ReportDto draft() { return reportCommands.create(studentActor, UUID.randomUUID(), sampleReport()); }
    CommentDto comment(UUID key, String text) { return commentCommands.create(studentActor, assignmentId, key, new CommentInput(text)); }
    void placementState(String state) {
        var s = currentScope;
        currentScope = new WeeklyReportPorts.CaseScope(s.caseId(), s.placementId(), s.studentUserId(), s.schoolOrgId(), s.enterpriseOrgId(),
                s.termId(), s.caseState(), state, s.effectiveAt(), s.approvedAt(), s.startDate(), s.endDate(), s.caseVersion(), s.placementVersion() + 1);
    }

    static final class MutableClock extends Clock {
        volatile Instant instant;
        MutableClock(Instant instant) { this.instant = instant; }
        public Instant instant() { return instant; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) {
            Objects.requireNonNull(zone);
            return new Clock() {
                public Instant instant() { return MutableClock.this.instant(); }
                public ZoneId getZone() { return zone; }
                public Clock withZone(ZoneId requested) { return MutableClock.this.withZone(requested); }
            };
        }
    }

    /** Consumer fake until T1 FileReadPort is available at this checkout. Not a copied T1 SPI.
     * PENDING draft references are separate from requireCleanBound at submit; no download grant.
     */
    final class FakeFileReadPort implements WeeklyReportPorts.Files {
        record File(UUID owner, UUID root, String purpose, String scanStatus) {}
        final Map<UUID, File> values = new HashMap<>();
        boolean available = true;
        Runnable duringRead = () -> {};
        UUID add(UUID root, String status) {
            var id = UUID.randomUUID(); values.put(id, new File(student, root, "WEEKLY_REPORT", status)); return id;
        }
        public void requireBound(Authentication actor, UUID owner, UUID root, List<UUID> ids, boolean requireClean) {
            if (!identity(actor).equals(owner)) throw failure(404, "RESOURCE_NOT_FOUND");
            if (!available) throw failure(503, "DEPENDENCY_UNAVAILABLE");
            duringRead.run();
            for (var id : ids) {
                var file = values.get(id);
                if (file == null || !owner.equals(file.owner()) || !root.equals(file.root()) || !"WEEKLY_REPORT".equals(file.purpose()))
                    throw failure(422, "FILE_BINDING_INVALID");
                if (requireClean && !"CLEAN".equals(file.scanStatus())) throw failure(409, "FILE_NOT_CLEAN");
            }
        }
    }

    /** One memory state for report/comment/audit/cache, enlisted once in the real Spring boundary.
     * Global test lock intentionally serializes memory operations; it does not emulate DB locks.
     */
    final class Store implements WeeklyReportPorts.Session, TaskCommentPorts.Session, WeeklyReportPorts.Audit, TaskCommentPorts.Audit {
        final Map<UUID, WeeklyReport> roots = new HashMap<>();
        final List<CommentDto> comments = new ArrayList<>();
        final List<Object> events = new ArrayList<>();
        final Map<CacheKey, Cached> cache = new HashMap<>();
        final ReentrantLock lock = new ReentrantLock(true);
        final ThreadLocal<Boolean> enlisted = new ThreadLocal<>();
        final JsonMapper json = JsonMapper.builder().build();
        boolean failAudit;
        void enlist() {
            if (Boolean.TRUE.equals(enlisted.get())) return;
            lock.lock();
            var oldRoots = new HashMap<>(roots); var oldComments = new ArrayList<>(comments);
            var oldEvents = new ArrayList<>(events); var oldCache = new HashMap<>(cache);
            enlisted.set(true);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                public void afterCompletion(int status) {
                    try {
                        if (status != STATUS_COMMITTED) {
                            roots.clear(); roots.putAll(oldRoots); comments.clear(); comments.addAll(oldComments);
                            events.clear(); events.addAll(oldEvents); cache.clear(); cache.putAll(oldCache);
                        }
                    } finally { enlisted.remove(); lock.unlock(); }
                }
            });
        }
        public WeeklyReport find(UUID id) { return roots.get(id); }
        public boolean insertIfAbsent(WeeklyReport report) {
            if (roots.containsKey(report.reportId()) || roots.values().stream().anyMatch(r -> r.key().equals(report.key()))) return false;
            roots.put(report.reportId(), report); return true;
        }
        public boolean replace(WeeklyReport report, long expected) {
            var old = roots.get(report.reportId());
            if (old == null || old.version() != expected) return false;
            roots.put(report.reportId(), report); return true;
        }
        public WeeklyReportPorts.PageSlice list(UUID id, WeeklyReport.State state, int page, int size) {
            var all = roots.values().stream().filter(r -> r.key().caseId().equals(id) && (state == null || r.current().state() == state))
                    .sorted(Comparator.comparing((WeeklyReport r) -> r.key().weekStart()).reversed().thenComparing(r -> r.reportId().toString())).toList();
            return new WeeklyReportPorts.PageSlice(all.stream().skip((long) page * size).limit(size).toList(), all.size());
        }
        public TaskCommentPorts.AppendReceipt append(TaskCommentPorts.Target target, TaskComment comment) {
            comments.add(comment.value());
            // Receipt fixture preserves version; whether real append bumps it remains an owner decision.
            return new TaskCommentPorts.AppendReceipt(comment.value(), target.assignment().version());
        }
        public TaskCommentPorts.CommentPage comments(UUID id, int page, int size) {
            var all = comments.stream().filter(c -> c.assignmentId().equals(id))
                    .sorted(Comparator.comparing(CommentDto::createdAt).thenComparing(c -> c.commentId().toString())).toList();
            return new TaskCommentPorts.CommentPage(all.stream().skip((long) page * size).limit(size).toList(), page, size, all.size(), (all.size() + size - 1) / size);
        }
        public void append(WeeklyReportPorts.AuditEvent event) { audit(event); }
        public void append(TaskCommentPorts.AuditEvent event) { audit(event); }
        private void audit(Object event) {
            events.add(event); // Failure AFTER append also has to roll back.
            if (failAudit) throw failure(503, "DEPENDENCY_UNAVAILABLE");
        }
        <T> T execute(OperationLedger.Command command, Class<T> type, Supplier<T> mutation, Function<T, String> reference) {
            var key = new CacheKey(command.actorId(), command.organizationId(), command.operationCode(), command.key());
            String hash = OperationLedger.payloadHash(command.payload());
            var previous = cache.get(key);
            if (previous != null) {
                if (!hash.equals(previous.hash())) throw failure(409, "IDEMPOTENCY_KEY_REUSED");
                return json.readValue(previous.response(), type);
            }
            T response = mutation.get();
            cache.put(key, new Cached(hash, json.writeValueAsString(response), reference.apply(response)));
            return response;
        }
    }
    record CacheKey(String actor, String organization, String operation, String key) {}
    record Cached(String hash, String response, String effect) {}
}
