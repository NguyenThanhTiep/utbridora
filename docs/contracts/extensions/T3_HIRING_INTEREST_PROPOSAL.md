# FR-27 hiring-interest extension — proposed for user review

This is a separate extension, not a modification of the approved SRS/Contract snapshots. FR-27 requires an interest marker separate from an employment decision, but CP-WS4/5 does not define its DTO, endpoint, permission or audit action. SRS §25.8.3 G27 requires a typed contract before implementing the corresponding public interface.

## Proposed wire and scope

`HiringInterestInput {expectedVersion:int[0..9007199254740990],expectedCaseVersion:V,interested:boolean,note:Text(0..2000)|null}`

`HiringInterestDto {caseId:U,interested:boolean,note:Text(0..2000)|null,version:int[0..9007199254740991],updatedAt:I|null}`

- GET `/api/v1/t3/cases/{caseId}/hiring-interest`:200 HiringInterestDto. Current assigned HR or delegated school with `t3:workspace:read`; no student or unrelated Mentor access. Missing record returns interested=false,note=null,version=0,updatedAt=null.
- PUT same path:200 HiringInterestDto. CSRF and UUID Idempotency-Key; current HR for the case plus new exact capability `t3:hiring_interest:manage`. A role ceiling alone does not grant a case assignment. `expectedVersion=0` creates the first record; every update checks both versions, appends immutable history and audits the mutation atomically.
- Writes are allowed only while Case is WAITING_START or IN_PROGRESS and the authoritative Placement remains APPROVED/ACTIVE. After Case closure the interest marker is read-only, consistent with the SRS terminal-write lock. HR records interest before school closes the Case.
- Current HR assignment, ancestry, enterprise/school/term and group/case restrictions are revalidated on every command/replay. Foreign/missing case404; revoked assignment403/404; stale version409 VERSION_CONFLICT; terminal/source conflict409 STATE_CONFLICT; schema400 SCHEMA_VALIDATION.
- New audit code `HIRING_INTEREST_CHANGED`, resource `hiring_interest`, permitted metadata only caseId,enterpriseOrgId,schoolOrgId,termId,version,interested. The free-text note is never audit metadata.
- The marker is an intention only. It neither changes grades, Placement, quota, consent nor makes an employment/credit decision. A later separate process would be required to record a real hiring decision.

## Required DEV verification

HR/school scope and revoked parent chain; denied student/Mentor/foreign school; version0 create/update/history/idempotent replay and changed-payload conflict; competing writes; terminal/source guards; no effect on grades/Placement; UI clearing on session/scope change.

Status: awaiting explicit decision on this extension only. Existing approved SRS/Contract remain approved and unchanged.
