# T3 shared V29 schema - implementation record

Updated 2026-11-11. The filename is retained for existing links; this is now an
implementation record, superseding the 10/11 proposal/blocked checkpoint.
The current user request allocates V29, authorizes its completion, and authorizes
full local integration of T1-06/V28. This is not human QA or deployment approval.

## Ordered baseline

- T1-06 commit 065c5be15f19df2fe8100c42248c5bd6d9c4a956 is integrated in full.
- V28__private_file_service.sql remains byte-for-byte the T1 migration (LF-normalized Git content).
- New V29__t3_submission_review_comment.sql follows V28. No V1-V28 migration edit,
  history manipulation, clean, repair, out-of-order setting or shared database change.
- Existing V21 Task, Assignment, Case, cohort and group tables are reused.

## Physical schema

| Table | Columns / constraints |
|---|---|
| internship_submissions | id PK, assignment_id FK, version_no INT >=1, url nullable TEXT, note nullable TEXT <=2000 characters, submitted_at DATETIME(6) UTC, late_flag BOOLEAN; UNIQUE(assignment_id,version_no) |
| internship_submission_reviews | id PK, submission_id UNIQUE, reviewed_version_no, verdict ACCEPT/REQUEST_REVISION, nonblank comment <=2000 characters, reviewer_user_id FK users, created_at UTC, assignment_version in safe integer range |
| internship_submission_files | submission_id/file_id composite PK and FKs, ordinal 1..20, UNIQUE(submission_id,ordinal); reserved for future real CLEAN/binding integration |
| internship_task_comments | id PK, assignment_id FK, author_user_id FK users, nonblank text <=2000 characters, created_at UTC; schema for DEV06, no runtime here |

Assignment.current_submission_id is reused. A composite FK
(current_submission_id,id) -> submissions(id,assignment_id) prevents attaching
another assignment's artifact. Review has a composite FK
(submission_id,reviewed_version_no) -> submissions(id,version_no), so the version
cannot differ from its referenced artifact. MySQL 8.4 uses explicit UNIQUE parent
keys for these references. No cascade deletes erase Submission/Review history.

History index: (assignment_id,version_no DESC,id ASC).
Comment history index: (assignment_id,created_at,id).
All user text tables use utf8mb4. The application validates the closed contract,
HTTPS/no userinfo, URL/file XOR, unicode length and distinct file IDs before SQL.
The database supplies referential/unique/check constraints; it does not replace
current user permissions, the Placement/Case guard or file scanning.

DEV05 SQL only INSERTs artifacts and reviews. It updates Assignment state/version/
current pointer atomically using expected version, state and old pointer predicates.
The service verifies REQUEST_REVISION and the existing Start endpoint before v2.
Review comment belongs to FR-16. Task Comment FR-17 runtime remains DEV06, with no
TASK_COMMENTED registry entry or t3:comments:write grant added by this task.

## Audit subset for T1 review

- SUBMISSION_CREATED: existing enum/entry, resource submission;
  assignmentId,versionNo,fileId,fileIds,lateFlag.
- SUBMISSION_REVISED: existing enum, new registry entry, resource submission;
  assignmentId,versionNo,previousSubmissionId,fileId,fileIds,lateFlag.
- SUBMISSION_REVIEWED: new enum/registry entry, resource submission;
  assignmentId,reviewedVersion,verdict,assignmentVersion.

All FILE_* and existing entries remain intact. No URL, note or free-text comment
is logged. AuditService.workflow/OperationLedger/transaction machinery is reused.
Mandatory audit failure rolls back the same domain transaction.

## Verification

T307SchemaUpgradeIT creates a populated V27 database in its own MySQL 8.4 container,
applies V28 then V29 separately, validates checksums/reapplication, retains the old
assignment state/version, and rejects foreign current pointers, duplicate versions,
wrong-version/duplicate/blank reviews, deletion of referenced artifacts and blank comments.
T307SubmissionRuntimeIT covers fresh migrations, real HTTP/session/CSRF, state cycles,
authority, concurrency, deadlines and injected transaction failures. MigrationUpgradeIT
keeps the existing identity/file upgrade regression and accounts for the additional V29.

See T3_07_COMPLETION_EVIDENCE.md for actual runs, source fingerprint and limitations.
File-mode remains fail-closed. Creating its reference table does not claim that the
scanner, artifact binding, Mentor file reader or DEV06 Comment API is implemented.
