# T3-07 runtime integration - DEV05

2026-11-12 follow-up: [persisted review GET API and DEV07 handoff](T3_07_REVIEW_READ_HANDOFF.md).
Use that document for the current endpoint, dependency findings and retest evidence.
The integration checkpoint below records the original 2026-11-11 delivery.

Updated 2026-11-11. This document supersedes the earlier URL-v1 Preparation checkpoint.
The current user request authorizes implementation, local integration of T1-06, V29,
and the exact Submission revision/review audit subset. Historical approval gates
in earlier reports are not current blockers.

## Scope and source of truth

- Plan: CAP NHAT CHUNG row 45; T3 - THUC TAP rows 17, 18, 37: DEV05 / FR-15, FR-16, R21.
- FR-17 Task Comment and Weekly Report remain DEV06 (row 46 / T3 row 19).
  ReviewInput.comment belongs to DEV05 and is not CommentInput.text.
- UI integration is DEV07/T3-09 and T2; no frontend implementation in this task.
- SRS v1.2.4, CP-WS2/C.0/D/E from the user-approved v1.0 contract govern behavior.
  Generating source hashes in the v1.1 verification bundle does not approve its Candidate diff.

## Integrated implementation

- T3 base: 3e6a573a303a7d1801324901b6783dfbb110851b, feature/t3-week3.
- T1-06: 065c5be15f19df2fe8100c42248c5bd6d9c4a956, integrated in full locally, without a commit.
- Common merge base: f1ef2176041c5b0892d5bb107f01d601616755c2.
- origin/main at inspection: c3d0dbc67b66ba21af84a7d4095def5d32324696.
  Its separate demo-import change is not a deletion by T3 and was not needed for this runtime.
- Existing uncommitted Submission/Review work was copied into the integration worktree
  and retained. No production ledger, Task, Assignment, Placement or auth replacement.
- SubmissionController calls submitUrl for every version. SubmissionReviewController
  exposes CP-WS2 Review. CoreSubmissionAccess uses current identity/scope/handover.
- JdbcSubmissionSession persists artifacts and reviews, CAS assignment state/version,
  and reads ordered persisted history. All effects join the existing JPA/JDBC transaction,
  OperationLedger and mandatory AuditService.workflow.
- SubmissionConfiguration supplies a microsecond UTC Clock, replaceable only by test
  configuration for deterministic deadline checks. No request can supply server time.
- V28 is the unchanged T1 migration; V29 adds Submission, Review, artifact-reference
  and Comment schema without duplicating Task/Assignment. See schema document below.
- SUBMISSION_CREATED is retained; SUBMISSION_REVISED and SUBMISSION_REVIEWED now have
  their exact contract registry metadata. Existing FILE_* actions remain intact.

## API for DEV07 / T2

Use the existing authenticated session/cookies. Mutation requests need the CSRF header
returned by GET /api/v1/auth/csrf and a UUID Idempotency-Key. Mentor requests select the
current valid X-Context-Assignment-Id. The header selects a grant; it does not create one.

| Step | API | Body / result |
|---|---|---|
| Read task/version | GET /api/v1/t3/tasks/{taskId} | Own/authorized AssignmentDto with version/state |
| Start or restart | PATCH /api/v1/t3/assignments/{id}/status | {expectedVersion, state:IN_PROGRESS}; existing week-2 endpoint |
| Submit URL | POST /api/v1/t3/assignments/{id}/submissions | {expectedAssignmentVersion, fileIds:[], url:https URL, note:null or string}; 201 SubmissionDto |
| History | GET /api/v1/t3/assignments/{id}/submissions?page=0&size=25 | 200 Page<SubmissionDto>, versionNo DESC, submissionId ASC |
| Review | POST /api/v1/t3/submissions/{id}/reviews | {expectedAssignmentVersion, verdict:ACCEPT or REQUEST_REVISION, comment:nonblank}; 201 ReviewDto |
| Read persisted review | GET /api/v1/t3/submissions/{id}/reviews | 200 ReviewDto; 204 with empty body when not yet reviewed; see follow-up handoff |

NEW -> start -> IN_PROGRESS -> submit v1 -> SUBMITTED -> REQUEST_REVISION ->
REVISION_REQUIRED -> start -> IN_PROGRESS -> submit v2 -> SUBMITTED -> ACCEPT -> COMPLETED.
Read the latest assignment version before each new mutation; ReviewDto also returns the
resulting assignmentVersion. Retry an uncertain request with its original key and payload.
Never PATCH SUBMITTED/COMPLETED to imitate a submit/review. A new action gets a new key.

History is immutable and does not require an active Placement when current read permission
still allows access. Mutation always rereads current Placement ACTIVE/effectiveAt and Case
IN_PROGRESS. Student ownership and Mentor current group/handover are checked at the server.
HR cannot review just because it can read workspace data.

409 STALE_SUBMISSION / VERSION_CONFLICT: refresh current data, preserve unsent input.
422 SUBMISSION_LATE: late submission was disabled. File-only input stays 503
DEPENDENCY_UNAVAILABLE until the full CLEAN/binding workflow is integrated; no scan bypass.

## Evidence and limitations

See [current DEV evidence](T3_07_COMPLETION_EVIDENCE.md) for actual commands/results and
[shared V29 schema](T3_07_V29_SCHEMA_PROPOSAL.md) for physical columns and ownership.
Controller/bean presence alone is not proof of runtime readiness. Unit/mock evidence is
separate from real MySQL/HTTP/transaction tests. Fixture ACTIVE Placement and grants are
synthetic prerequisites, not acceptance of T5 approval or Mentor onboarding workflows.
Human QA03/Lead signoff, CI for this uncommitted snapshot and shared deployment remain separate.
No shared database seed/import/migrate/restart, Plan/SRS edits, commit, push or main merge.
