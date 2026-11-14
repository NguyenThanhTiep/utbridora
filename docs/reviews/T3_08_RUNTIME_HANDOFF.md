# DEV06 — T3-08 runtime handoff, 2026-11-12

> Historical checkpoint before the completion request. Superseded by
> [T3_08_COMPLETION_EVIDENCE.md](T3_08_COMPLETION_EVIDENCE.md), including V31 and Report DB tests.

Owner: Lê Nguyễn Nhật Duy. Scope: FR-17 Comment, FR-18 Weekly Report, FR-19 Report Review.
Status: PARTIAL implementation; not task completion or QA acceptance.

## Working copy

Implementation is in `D:\TeamCodeHKDN\.work\t3-08-integration`.
Detached worktree based on `91697b4`, with an uncommitted integration of
`origin/feature/t3-week3` at `000c8e9d4b2f55818c55089ae9747c3bba0b2d6c`.
The merge is pending. No commit or push was made. Staged dependency changes belong to DEV05/T1;
unstaged/new DEV06 changes must be reviewed separately.
The original checkout remains intact: all 46 files in the source manifest retained their hashes.
Manifest: `D:\TeamCodeHKDN\.work\t3-08-source-manifest.json`.

## Implemented

- Real Spring bean wiring behind `app.t3.week3.http.enabled` (default OFF).
- Shared transaction manager, READ_COMMITTED workspace transactions and real JDBC sessions.
- Comment Assignment adapter uses DEV05 lookup/lock ports; Comment preserves Assignment.version.
- Comment persists against V29, with access guards, replay, audit and atomic rollback.
- Independent Report Placement -> Case locking fence, without inventing a Task/Cohort.
- Report service/JDBC/commands wiring, audit registry actions and authenticated PATCH route.
- T1 FileService adapter and Weekly Report resource policy; original Authentication is retained;
  draft may use PENDING/CLEAN, submission requires T1 CLEAN validation.
- Review-only SQL candidate, not a Flyway migration and not applied to any database.

## Verification

Final source was compiled and exercised using:

```powershell
.\mvnw.cmd -o '-Dtest=*WeeklyReport*Test,*TaskComment*Test,*Report*Test,Week3HttpControllerTest,Dev06*Test,*Submission*Test,AuthorizationPolicyTest' '-Dit.test=T308CommentRuntimeIT' test failsafe:integration-test failsafe:verify
```

- 751 selected regression tests: zero failures/errors/skips.
- 7 Comment HTTP/MySQL 8.4 Testcontainers tests: zero failures/errors/skips.
- Maven log reports BUILD SUCCESS at 2026-11-12 12:12:31 +07.
- Log: `D:\TeamCodeHKDN\.work\t3-08-final-verify.log`.
- Real Comment cases cover persistence/replay/payload mismatch, access scope, inactive source with
  readable history, CSRF/spoof rejection, 20 concurrent requests, audit failure rollback/retry,
  and parent grant revocation blocking cached replay (Core returns 403).
- Comment permission is inserted ONLY as an explicit test prerequisite. This is not production
  permission provisioning. Source Placement/user data are synthetic prerequisites.
- Standard contract builder/check and verifier: 868/868 PASS. Regenerated bundle changes only
  manifest source hashes, including newly discovered DEV06 files and updated AuditAction,
  WorkflowAuditRegistry, CaseRuntime and SecurityConfig; no wire contract changes.
  Report: `target/contract-verification-t308.json`.
- Full Maven `verify` packaging was not completed: offline jar plugin dependency was missing;
  online dependency download stalled. Direct test/Failsafe goals above succeeded.
- No shared database was modified. These tests do not establish Report DB/file-scanner acceptance.

## Required before completion

1. T1/lead must approve `T3_08_WEEKLY_REPORT_SCHEMA_PROPOSAL.md` and allocate the global Flyway
   migration number. Do not invent V30. Candidate SQL is `T3_08_SCHEMA_CANDIDATE.sql`.
2. Install the approved Report schema and `t3:comments:write` permission/role ceilings through
   an allocated migration, then verify clean install and upgrade in disposable MySQL.
3. Run real Report HTTP/DB tests: draft/update/submit/review/revision, current/history, uniqueness,
   optimistic concurrency, replay, access revocation, source changes and rollback.
4. Run real T1 upload/binding/PENDING/CLEAN/REJECTED/submit/download tests for Report files.
   Adapter unit tests pass; real scanner/provider integration has not been verified here.
5. Lead/DEV07 must settle the Report read projection for review text/reviewer. Existing wire
   contract is preserved; adding fields requires an explicit versioned contract decision.
6. Review and integrate the worktree changes, complete packaging/CI and QA handoff. Keep the
   week3 gate OFF until schema, permission provisioning and remaining runtime checks are ready.

The migration hold follows the supplied implementation prompt; it is not resolved merely by
starting the database. Earlier preparation-only status in DEV06_TASK_PROGRESS.md is historical;
this document records the current runtime work and remaining limits.
