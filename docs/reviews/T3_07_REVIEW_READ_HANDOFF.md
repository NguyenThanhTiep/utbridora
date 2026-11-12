# T3-07: read persisted submission reviews

Updated 2026-11-12. DEV05 follow-up for DEV07/T3-09 on `feature/t3-week3`,
based on `59ef6d2` (includes DEV06/T3-08). The user requested this endpoint,
regression tests and a build before publishing the fix for UI integration.
This supplements the 2026-11-11 evidence; the previous report did not verify
reading review feedback after a reload.

## API and UI integration

1. Fetch `GET /api/v1/t3/assignments/{assignmentId}/submissions?page=0&size=25`.
   The existing `Page<SubmissionDto>` shape and ordering are unchanged.
2. For the selected history row, fetch
   `GET /api/v1/t3/submissions/{submissionId}/reviews`.
3. Handle `204` before parsing JSON: that submission exists and is visible but
   has no review yet. Clear any feedback from a previously selected version.
4. On `200`, render `comment` and `verdict` from the returned `ReviewDto`.
   Fetch again after reload, a successful review or selection of another version.
   Do not carry v1 feedback over to an unreviewed v2.

The endpoint returns one object, not an array: V29 permits one immutable review
per submission. It reads the persisted review, including older versions after
resubmission/completion, rather than reconstructing it from current assignment state.
The read uses the same existing scope/ownership checks as submission history.
An inactive Placement does not by itself hide authorized historical reads.

```json
{
  "reviewId": "00000000-0000-0000-0000-000000000090",
  "submissionId": "00000000-0000-0000-0000-000000000010",
  "reviewedVersionNo": 1,
  "verdict": "REQUEST_REVISION",
  "comment": "Please include the missing test results.",
  "reviewerUserId": "00000000-0000-0000-0000-000000000080",
  "createdAt": "2026-11-12T03:00:00Z",
  "assignmentVersion": 4
}
```

The example is synthetic. Verdict is `ACCEPT` or `REQUEST_REVISION`.
`assignmentVersion` is the version recorded when this review was created;
read the current task/assignment before a subsequent mutation.

Use the existing session cookie (`credentials: 'include'` for a cross-origin
frontend) and current `X-Context-Assignment-Id` where required for staff/Mentor.
GET requires neither an Idempotency-Key nor a CSRF mutation header. Existing
POST review requirements still apply. Error responses retain the Core envelope:

| Status | Meaning |
|---|---|
| 200 | Persisted `ReviewDto`, including comment/verdict |
| 204 | Visible submission not yet reviewed; empty body |
| 400 | Malformed submission UUID (`SCHEMA_VALIDATION`) |
| 401 | Missing/expired session |
| 404 | Submission absent or outside current read scope (`RESOURCE_NOT_FOUND`) |
| 503 | Runtime dependency unavailable or inconsistent stored projection |

When switching versions, discard an older in-flight response if its
`submissionId` no longer matches the selected row. Treat 401/404 as loss of
access and clear previously displayed private feedback.

This is an additive runtime endpoint using the existing `ReviewDto`. It does
not revise or approve the frozen v1.0 contract or the v1.1 Candidate. The generated
manifest is refreshed only to track changed production-source hashes.

## Dependencies checked against Plan and source

Workbook consulted: `.work/t3-07-integration/docs/plans/UTBridora_Plan.xlsx`,
`CAP NHAT CHUNG` row 45 and `T3 - THUC TAP` rows 17/18/37. Workbook status cells
are planning snapshots, not evidence of the current branch's test results.

| Dependency | Owner | Relevance and current evidence |
|---|---|---|
| T3-05, direct Plan dependency | DEV06 / QA03 | Task/assignment, deadline and transitions. Existing week-2 runtime is reused and included in regression verification. |
| T5-06, direct Plan dependency | DEV11 / QA05 | Plan row 50 names consent/share whitelist and enterprise feedback. This is not the Placement read API; this patch does not call or certify consent sharing. Lead should reconcile this dependency label with the intended integration. |
| T5 Placement, runtime dependency | T5 | Mutations require current ACTIVE/effective Placement and the existing Case checks; authorized history/review reads remain available after cancellation. Tests use synthetic prerequisite Placement records and do not certify T5 approval workflow. |
| Core auth/scope, ledger/audit and V28 baseline | T1; T1-06 DEV01 / QA01 | Existing adapters and schema are already integrated on this branch. This GET reuses read authorization and V29 persistence, without new permissions/migrations. |
| Full file submission/scanning | T1 and T3 integration | Still outside the URL-v1/v2 delivery: file-only submission fails closed with 503. No CLEAN/scanner completion is claimed. |
| UI consumer T3-09 | DEV07 / QA03 | Depends on T3-07 and T3-08 per Plan row 47. This endpoint unblocks loading submission feedback; UI acceptance remains DEV07/QA work. |

T3-08 weekly-report feedback is a separate workflow. It is included in regression
checks because DEV06's latest code shares the branch, not because this read API
needs WeeklyReportService.

## Verification

Completed locally at 2026-11-12 13:41 +07:00, **BUILD SUCCESS**, before commit.

```powershell
.\mvnw.cmd -B '-Dit.test=T307SubmissionRuntimeIT,T307SchemaUpgradeIT,MigrationUpgradeIT,InternshipRuntimeIT,T308CommentRuntimeIT,T308ReportRuntimeIT,T308SchemaUpgradeIT' clean verify
python scripts/contracts/build_contract_bundle.py --check
python scripts/contracts/verify_contracts.py --output .work/t3-07-review-read-contracts.json
python scripts/contracts/test_audit_registry.py
python scripts/contracts/test_bundle_portability.py
```

| Check | Result |
|---|---|
| All unit tests, 65 suites | 1,263 PASS, 1 SKIP; 0 failures/errors |
| Selected integration tests, 7 suites | 51 PASS, 1 SKIP; 0 failures/errors |
| T307SubmissionRuntimeIT | 20 PASS, no skips |
| T307SchemaUpgradeIT | 1 PASS |
| MigrationUpgradeIT | 3 PASS |
| InternshipRuntimeIT | 11 PASS, 1 browser SKIP |
| DEV06 comment/report/schema regression | 7 + 8 + 1 PASS |
| Contract / audit registry / portability | 868 / 3 / 1 PASS |
| Packaged application | `target/utbridora-auth-foundation-0.2.0-SNAPSHOT.jar` |

The unit skip is the existing Windows symlink storage check. The integration
skip is the existing browser test gated by `frontend.e2e=true`; frontend E2E
was not enabled. Runtime was JDK 24.0.1 with compiler release 21, not a local
Java 21 runtime certification. Integration used isolated MySQL 8.4 containers.
This run selected the seven listed integration suites, not every repository IT.

See the [verification receipt](../testing/evidence/T3_07_REVIEW_READ_VERIFICATION.json)
for suite counts, skipped tests and source/report hashes. The complete tested
source fingerprint is `84c544bad135ee55bef5e9a9235efc37c82dbe9d2c1fb679ef8ef3e2d3bdeba4`.
Local log: `.work/t3-07-review-read-verify.log`; XML reports: `target/surefire-reports`
and `target/failsafe-reports`. The receipt preserves evidence without committing logs.

The added tests cover both verdicts, empty review, malformed/unknown IDs,
anonymous/foreign Student/wrong-group Mentor access, revoked parent grant,
historical-task handover, fresh-session reads, v1 feedback after v2, and reads
after Placement cancellation. Successful reads must not add operation-ledger
or audit rows. Service checks reject inconsistent review-version projections.

No shared database or demo data is changed by these tests. QA03 signoff,
GitHub CI and DEV07's browser acceptance are separate from local DEV evidence.
