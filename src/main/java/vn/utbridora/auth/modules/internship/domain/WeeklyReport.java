package vn.utbridora.auth.modules.internship.domain;

import java.time.*;
import java.util.*;
import static vn.utbridora.auth.modules.internship.domain.WeeklyReportPolicy.*;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** Immutable report aggregate. All operations return a new value; history never aliases mutable data.
 * Version is a root-wide CAS token (never reset on revision); revisionNo identifies content history.
 * Runtime must enforce Key uniqueness and persist the aggregate with audit atomically.
 */
public final class WeeklyReport {
    public enum State { DRAFT, SUBMITTED, REVIEWED }
    public record Key(UUID caseId, LocalDate weekStart) {
        public Key { required(caseId, "caseId"); required(weekStart, "weekStart"); }
    }
    public record Content(String completedWork, String difficulties, String nextPlan, List<UUID> evidenceFileIds) {
        public Content {
            completedWork = text(completedWork, 1, 10_000, "completedWork");
            difficulties = text(difficulties, 0, 5_000, "difficulties");
            nextPlan = text(nextPlan, 1, 5_000, "nextPlan");
            evidenceFileIds = files(evidenceFileIds);
        }
    }
    public record Review(UUID reviewerUserId, String comment, Instant createdAt) {
        public Review {
            required(reviewerUserId, "reviewerUserId"); required(createdAt, "createdAt");
            comment = text(comment, 1, 2_000, "comment");
        }
    }
    public record Revision(int revisionNo, long version, State state, Content content, String reasonCode,
                           Instant submittedAt, Review review, boolean lateFlag) {
        public Revision {
            if (revisionNo < 1) throw new IllegalArgumentException("Invalid revisionNo");
            WeeklyReportPolicy.version(version); required(state, "state"); required(content, "content");
            if (reasonCode != null) reason(reasonCode);
            if (revisionNo > 1 && reasonCode == null) throw new IllegalArgumentException("Revision reason is required");
            if (state == State.DRAFT && (submittedAt != null || review != null || lateFlag)
                    || state == State.SUBMITTED && (submittedAt == null || review != null)
                    || state == State.REVIEWED && (submittedAt == null || review == null)) {
                throw new IllegalArgumentException("Inconsistent report state");
            }
        }
    }

    private final UUID reportId;
    private final Key key;
    private final Instant dueAt;
    private final List<Revision> revisions;

    private WeeklyReport(UUID reportId, Key key, Instant dueAt, List<Revision> revisions) {
        this.reportId = required(reportId, "reportId"); this.key = required(key, "key");
        this.dueAt = required(dueAt, "dueAt"); this.revisions = List.copyOf(revisions);
    }

    public static WeeklyReport create(UUID id, UUID caseId, LocalDate weekStart, LocalDate startDate,
                                      LocalDate endDate, Content content) {
        requireWeek(weekStart, startDate, endDate); required(content, "content");
        if (!content.evidenceFileIds().isEmpty()) throw failure(422, "FILE_BINDING_INVALID");
        return new WeeklyReport(id, new Key(caseId, weekStart), WeeklyReportPolicy.dueAt(weekStart),
                List.of(new Revision(1, 1, State.DRAFT, content, null, null, null, false)));
    }

    /** Rehydrate trusted storage values without replaying commands or creating a second root.
     * Reject corrupt history before it can be projected or changed. This is not a database adapter
     * or an authorization/file guard; callers must still resolve current Case/Placement/authority.
     * Version gaps are valid (draft edits); revisions must be contiguous and versions increasing.
     */
    public static WeeklyReport restore(UUID id, Key key, Instant storedDueAt, long storedVersion,
                                        List<Revision> storedRevisions) {
        required(id, "reportId"); required(key, "key"); required(storedDueAt, "dueAt");
        WeeklyReportPolicy.version(storedVersion); required(storedRevisions, "revisions");
        if (storedRevisions.isEmpty() || storedRevisions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Invalid stored report history");
        }
        var history = List.copyOf(storedRevisions);
        Instant expectedDueAt = WeeklyReportPolicy.dueAt(key.weekStart());
        if (!expectedDueAt.equals(storedDueAt)) throw new IllegalArgumentException("Inconsistent stored deadline");
        long previousVersion = 0;
        for (int index = 0; index < history.size(); index++) {
            var revision = history.get(index);
            if (revision.revisionNo() != index + 1 || revision.version() <= previousVersion
                    || index == 0 && revision.reasonCode() != null
                    || index < history.size() - 1 && revision.state() == State.DRAFT
                    || revision.state() != State.DRAFT
                        && revision.lateFlag() != WeeklyReportPolicy.lateFlag(storedDueAt, revision.submittedAt())) {
                throw new IllegalArgumentException("Inconsistent stored revision");
            }
            previousVersion = revision.version();
        }
        if (previousVersion != storedVersion) throw new IllegalArgumentException("Inconsistent stored root version");
        return new WeeklyReport(id, key, storedDueAt, history);
    }

    public UUID reportId() { return reportId; }
    public Key key() { return key; }
    public Instant dueAt() { return dueAt; }
    public List<Revision> revisions() { return revisions; }
    public Revision current() { return revisions.getLast(); }
    public long version() { return current().version(); }
    public Revision revision(int number) {
        if (number < 1 || number > revisions.size()) throw failure(404, "RESOURCE_NOT_FOUND");
        return revisions.get(number - 1);
    }

    public WeeklyReport update(long expectedVersion, Content content) {
        long next = nextVersion(version(), expectedVersion); requireState(State.DRAFT);
        return replace(new Revision(current().revisionNo(), next, State.DRAFT, content,
                current().reasonCode(), null, null, false));
    }

    public WeeklyReport submit(long expectedVersion, Instant now) {
        long next = nextVersion(version(), expectedVersion); requireState(State.DRAFT);
        return replace(new Revision(current().revisionNo(), next, State.SUBMITTED, current().content(),
                current().reasonCode(), required(now, "now"), null, WeeklyReportPolicy.lateFlag(dueAt, now)));
    }

    public WeeklyReport review(long expectedVersion, UUID reviewer, String comment, Instant now) {
        long next = nextVersion(version(), expectedVersion); requireState(State.SUBMITTED);
        return replace(new Revision(current().revisionNo(), next, State.REVIEWED, current().content(),
                current().reasonCode(), current().submittedAt(), new Review(reviewer, comment, now), current().lateFlag()));
    }

    public WeeklyReport revise(long expectedVersion, String reasonCode) {
        long next = nextVersion(version(), expectedVersion);
        if (current().state() == State.DRAFT) throw failure(409, "STATE_CONFLICT");
        if (current().revisionNo() == Integer.MAX_VALUE) throw failure(409, "VERSION_CONFLICT");
        var history = new ArrayList<>(revisions);
        history.add(new Revision(current().revisionNo() + 1, next, State.DRAFT, current().content(),
                reason(reasonCode), null, null, false));
        return new WeeklyReport(reportId, key, dueAt, history);
    }

    private WeeklyReport replace(Revision replacement) {
        var history = new ArrayList<>(revisions); history.set(history.size() - 1, replacement);
        return new WeeklyReport(reportId, key, dueAt, history);
    }
    private void requireState(State expected) {
        if (current().state() != expected) throw failure(409, "STATE_CONFLICT");
    }
}
