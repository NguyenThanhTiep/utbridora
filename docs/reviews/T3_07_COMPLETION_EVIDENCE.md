# T3-07 - DEV completion evidence: URL versions and Mentor Review

2026-11-12 correction: this checkpoint did not include a GET endpoint for persisted
review feedback after reload. See the [review-read follow-up and new verification](T3_07_REVIEW_READ_HANDOFF.md)
for the added endpoint and current evidence. Results below remain the historical 2026-11-11 run.

Updated 2026-11-11. **DEV URL scope verified; QA03/Lead acceptance not recorded.**
This supersedes the 10/11 PARTIAL/BLOCKED report. Current authorization resolved
V29, T1-06 integration and the exact Review/Revision audit subset; those historical
approval questions are no longer blockers.

## Result and scope

The backend URL flow is implemented and verified over real HTTP, Spring Security,
JDBC/JPA transactions and MySQL 8.4:

IN_PROGRESS -> submit v1 -> REQUEST_REVISION -> existing Start -> submit v2 -> ACCEPT.
Assignment ends COMPLETED; v1 URL/note/time and its Review remain unchanged. Reading
old history works after completion/cancellation when current read permission allows it.
Reviewing v1 when v2 is current returns 409 STALE_SUBMISSION.

DEV05 owns FR-15/FR-16/R21 (Plan CAP NHAT CHUNG row 45; T3 rows 17/18/37).
ReviewInput.comment is part of FR-16. Task Comment FR-17 and Weekly Report runtime
remain DEV06; UI is DEV07/T3-09 and T2. No Plan/SRS approval or progress cells changed.
Approved CP-WS2/C.0/D/E v1.0 is unchanged; v1.1 remains Candidate.

## Git and integration baseline

- Working branch: feature/t3-week3; HEAD 3e6a573a303a7d1801324901b6783dfbb110851b.
- Full T1-06 dependency: 065c5be15f19df2fe8100c42248c5bd6d9c4a956.
- Common merge base: f1ef2176041c5b0892d5bb107f01d601616755c2.
- origin/main at fetch: c3d0dbc67b66ba21af84a7d4095def5d32324696.
  Its separate demo-import change is not a deletion by T3; it was not integrated.
- Tests ran in `.work/t3-07-integration`, created from the T3 HEAD above, with a
  full `git merge --no-commit --no-ff origin/feature/t1-06-private-files`.
  Only generated manifest conflicted; the official builder regenerated it and
  its conflict was resolved. No merge commit was created.
- Existing uncommitted task work was copied and retained. The tested snapshot is
  synchronized back to the main working checkout as uncommitted file changes.
  This does not add T1 ancestry to HEAD; the integration worktree retains the
  pending merge for later authorized Git review. No main merge, commit or push.
- Original files before synchronization are backed up under `.work/t3-07-before-sync`;
  per-file SHA-256 guards reject unexpected concurrent edits. Personal package-lock,
  PROMPT_SETUP_AI and unrelated files are not included.

## Retained work and changes

Retained: DTO/parser/policy, SubmissionService/ports, controller, URL v2/Review JDBC
and authorization work already present. Reuses WorkspaceAuthority/handover,
CaseRuntime/current T5 source locks, Core identity/permission, OperationLedger and
AuditService.workflow. No parallel persistence, auth, ledger or Placement producer.

Completed in this session:

- Full T1-06 runtime/V28 baseline, not a copied SQL-only substitute.
- `V29__t3_submission_review_comment.sql`: immutable Submission/Review storage,
  version uniqueness, current-pointer ownership and reviewed-version composite FKs,
  artifact-reference schema and Comment schema for DEV06. V1-V28 unchanged.
- Canonical SUBMISSION_REVIEWED enum and registry; SUBMISSION_REVISED registry.
  SUBMISSION_CREATED and FILE_* preserved. Free-text URL/note/comment remain forbidden.
- Microsecond UTC submission Clock injection for deterministic boundary tests.
- Real HTTP/MySQL tests, populated V27 upgrade/constraints, audit/ledger failure
  rollback, twenty concurrent calls, current scope/revoke and actual handover endpoints.
- Updated stale Preparation/schema documentation and official generated source hashes.

See [schema details](T3_07_V29_SCHEMA_PROPOSAL.md) and
[API handoff for DEV07/T2](T3_07_M2B_RUNTIME_PREPARATION.md).

## Final verification

One final run, after fixes, completed **BUILD SUCCESS**:

```powershell
.\mvnw.cmd -B '-Dit.test=T307SubmissionRuntimeIT,T307SchemaUpgradeIT,MigrationUpgradeIT,InternshipRuntimeIT' verify
python scripts/contracts/build_contract_bundle.py --check
python scripts/contracts/verify_contracts.py --output target/t3-07-contract-verification.json
python scripts/contracts/test_audit_registry.py
python scripts/contracts/test_bundle_portability.py
```

| Check | Actual result |
|---|---|
| Unit, 43 suites | 957 cases: 956 PASS, 1 SKIPPED, 0 failures/errors |
| T307SubmissionRuntimeIT | 19 PASS, no skipped |
| T307SchemaUpgradeIT | 1 PASS, no skipped |
| MigrationUpgradeIT | 3 PASS, no skipped |
| InternshipRuntimeIT regression | 11 PASS, 1 SKIPPED |
| Integration total in final run | 35 cases: 34 PASS, 1 SKIPPED, 0 failures/errors |
| Contract builder/checker | 868/868 PASS |
| Audit registry / portability | 3/3 and 1/1 PASS |

Unit skip: Windows could not create symlinks for PrivateFileStorageTest.
Integration skip: legacy `realHrAndMentorBrowserFlow` requires `frontend.e2e=true`,
not enabled for this backend task. All T3-07 MySQL/HTTP tests executed.
No claim of browser E2E, human QA or GitHub CI for these uncommitted changes.

Environment: Java runtime 24.0.1, compiler `--release 21`, MySQL 8.4 Testcontainers.
The project Java target/framework was not upgraded. Java 21 runtime validation
remains for the standard CI environment; it was not rerun locally.

Evidence: [machine-readable receipt](../testing/evidence/T3_07_DEV_VERIFICATION.json),
`.work/t3-07-integration/t3-07-final-verify.log`, and that worktree's
`target/surefire-reports`, `target/failsafe-reports`, `target/t3-07-contract-verification.json`.
Raw logs/reports remain local and are not staged.

LF-normalized source/test/resource/pom fingerprint:
`9f53cf9a718eec19f46b95201dfab1201ee7ca078128154846b8eb476c0babe2`.
The receipt includes per-file hashes and report hashes, tying evidence to the exact snapshot.

Earlier failed runs are not hidden: stale unit assertions from the old authorization
scope, container lifecycle ordering, a CANCELLED fixture missing cancelled_at,
a URL schema-error expectation, and test-container trigger privileges were corrected.
Fault injection enables trigger creation only in the disposable MySQL test container.
No migration-history workaround or production guard was disabled.

## Acceptance trace

| Requirement | Real evidence |
|---|---|
| FR-15 / R21 immutable v1/v2 | realRevisionCycleKeepsV1V2AndRejectsOldReview; persisted paginated history after terminal source state |
| FR-16 Mentor current scope | wrongMentorAndRevokedParentCannotReviewOrReplayPrivateResults; studentAndHrCannotUseTheMentorReviewEndpoint |
| Handover/session invalidation | explicitHandoverControlsReviewOfHistoricalTasksAndIsLostOnAnotherMove, actual move/handover HTTP routes |
| Placement/current guard | realSourceGuardRejectsPlacementThatIsNoLongerActive, effectiveAtCannotBeBypassedByAStaleInProgressCase |
| Deadline | deadlineBoundaryUsesServerClockAndStoredPolicy: exactly dueAt, allowed late, forbidden late |
| Retry/concurrency | 20 same-key requests -> one artifact/audit; 20 distinct keys with same version -> one winner |
| Atomicity | CREATED/REVISED/REVIEWED audit fault and ledger DONE fault -> rollback; retry same key succeeds |
| Security | real session/CSRF chain, anonymous, cross-school owner, HR/Student/wrong Mentor, revoked parent, stale session |
| Database | populated V27 -> V28 -> V29; fresh migrations; FK/unique/checks, validate and no-op reapplication |

## Limits and next handoff

- File-mode still rejects safely with 503 DEPENDENCY_UNAVAILABLE; URL mode is complete.
  T1 scanner/full binding and Mentor file access are not replaced with synthetic CLEAN.
- Fixture users/grants/ACTIVE Placement are synthetic prerequisites. Tests use actual
  current T5 read adapter/security/persistence, but do not certify T5 approval workflow.
- DEV07/T2 can integrate the unchanged CP-WS2 routes described in the API handoff.
- DEV06 receives schema details for Comment; this work does not implement its runtime.
- T1 reviews the small audit subset, shared V29 and dependency integration during review.
- QA03/Lead signoff: NOT_RECORDED. CI for this snapshot: NOT_RUN. Shared deployment: NOT_RUN.
- No shared database seed/import/migrate/restart; no commit/push or main merge.
