# Must packages 1–5

Implementation follows the user-approved SRS v1.2.4 and Contract Pack v1.1. Frozen specifications and the user's Plan are not modified. DEV evidence does not replace QA or acceptance.

## Package 1

- Skill catalog searches include exact normalized aliases, so the existing Student and Demand pickers receive the canonical skill code. Unknown aliases do not create a skill; deprecated skills remain excluded by default. Canonical-name/code partial search remains available.
- Demand `PATCH /enterprise/demands/{id}/reopen` and `/restore` use the approved `{expectedVersion,reasonCode}` request, current owner authorization, shared idempotency ledger and immutable revision snapshots. Restore clears the soft-delete marker and returns a draft. The criteria hash/version and IDs remain intact; the demand version increases.
- Submitted/approved cooperation or effective downstream proposals/placements block lifecycle changes and draft edits. Legacy cancelled cooperation remains read-only under the approved policy. Demand is acquired before Cooperation for transactional writer reads, matching the quota/lifecycle fence.
- The demand screen can include deleted records and sends versioned lifecycle commands. Server guards determine whether the operation is currently allowed.
- Tests cover alias search/normalization/deprecation, lifecycle history/replay/scope/CSRF/schema/active-cooperation guards, and adding a different student after a decline into the same proposal batch without reusing an old student.

No migration is needed for package 1. Existing revision operations remain CREATE/UPDATE; `DEMAND_CHANGED` audit metadata records REOPEN/RESTORE. No existing migration or history checksum is edited.

Verification checkpoints live outside Git under `.work/must-packages-20261119`. Local checks may run unit, compilation and UI-only tests; integration/container tests must run on isolated CI resources, never the shared developer Docker daemon. Deploying or changing the demo database is a separate operation.

## Packages 2–5

T1 owns staging/jobs, identity/claims/token consumption and encrypted mail transport. T2 owns roster/term metadata, profile/eligibility materialization and the activation inbox. T5 owns the school import UI. The owner ports join the same transaction for provisioning and acceptance; an event reconciles an already bound identity instead of creating it later.

- Canonical multipart preview: `POST /api/v1/school/student-imports/preview` with `file`, `termId`, CSRF and UUID `Idempotency-Key`. School scope comes from the authoritative term. Current SCHOOL_STAFF membership and exact `schoolRosterImport:manage` delegation are required. A role/permission ceiling does not grant a person that delegation.
- CSV UTF-8/BOM or a single XLSX worksheet, <=5 MiB and <=5,000 parsed data records. Required headers: `studentCode,email,fullName,majorCode,termCode`; optional `cohortCode,classCode,schoolOrgId`. Leading zeros and case are preserved; Excel numeric codes are invalid, formulas never execute. Duplicate code/email rows are invalid; foreign school content fails the whole request. Existing identity/metadata conflicts require review rather than automatic merging.
- Real ClamD must return CLEAN before a selectable preview is committed. Scan timeout60s, initial+3 retries5/30/120s; no DB lock is held during scan/network I/O. A proxy serving this synchronous endpoint must allow its bounded scan/retry duration (up to395s plus validation/storage); latency for 5,000 rows is not promised without measurement. Private staging uses the existing FileStorage integrity/security rules.
- Preview expires after30min. Confirm stores only selected VALID row IDs, checks version/file/catalog/term/row snapshots and starts durable provisioning. The worker uses a120s lease, epoch fencing and per-row transactions, retries1/5/30s, preserves successful effects across jobs, and rechecks the initiating account plus captured delegation before every row. Resume only retries unfinished rows. Counters separate source validation from selected provisioning outcomes.
- A new pending student has roster/term + claim/current token/encrypted mail outbox, without a user/profile/eligibility. Acceptance atomically creates/activates the identity, STUDENT membership, binds the roster, creates missing profile/term eligibility with `schoolApproved=false`, consumes the token and writes audit/event. Existing linked ACTIVE accounts keep their password, profile and approvals.
- New activation clients opt in with `X-UTB-Contract-Version: 1.1`, UUID key and `{token,newPassword,displayName?}`. Legacy `{token,displayName,password}` retains its existing status behavior. Token precedence distinguishes used/replaced/expired tokens without returning identity information.
- Resend is separate from provisioning: claim version guards, live-send guard,5min cooldown and3 manual issuances/rolling24h across jobs/actors. The claim is a common identity mutex for roster/legacy/Mentor issuance. SMTP transport is at-least-once using a stable Message-ID/link per retry; terminal messages purge encrypted payloads. A SENT message means the SMTP server accepted it, not that a student activated.
- School UI supports scope/term selection, real preview, valid-only selection across pages, confirmation, progress, filters, resume, tracking by job ID and resend. Scope/session changes clear staged PII; no roster data or activation credentials are placed in browser storage.

### Deployment configuration (not enabled by this source change)

`STUDENT_IMPORT_WORKER_ENABLED`, `ACTIVATION_MAIL_WORKER_ENABLED` and `STUDENT_ACTIVATION_EVENTS_ENABLED` default to `false`. Enable only on the instance designated to process the reviewed environment. Multiple instances still share database fencing/budget; do not assume changing a Compose project isolates resources.

Supply `ACTIVATION_MAIL_KEY_ID` and `ACTIVATION_MAIL_KEYS` through a secret manager/environment. The latter is a JSON object mapping key IDs to base64-encoded32-byte AES keys. Keep the prior decrypt keys during rotation. No key is generated or embedded by application code; missing configuration fails issuance closed. Never commit this value, raw tokens, passwords, private outbox payloads or SMTP contents. `MAIL_DISPATCH_PER_MINUTE` defaults60 and accepts5..300; there are two database-backed dispatcher slots. Configure the existing SMTP host/port/credentials and finite connection/read/write timeouts for the target environment; local demo mail remains separate.

V41/V42 are forward migrations. Back up and test a clone of the actual lineage before any live upgrade. Existing migration SQL/history, approved SRS/contract snapshots and the user's Plan are preserved. A source commit does not deploy these migrations or enable the workers.

### Verification

Unit/boundary tests cover the non-executing parser, duplicate/row validation, scan retry/fail-closed behavior and AES-GCM key/AAD integrity. UI fixture tests verify the wire/selection/session behavior. `StudentImportIT` uses dedicated MySQL8.4, real ClamAV and Mailpit on CI, with pending-only provisioning, cross-job reuse, atomic activation, resend, expiry/reference guards, authority/crash recovery for1,000rows and a5,000row counterexample with200invalid/4,800selected. These tests are source coverage until the exact commit's CI reports pass. No integration container is run on the shared local Docker daemon.
