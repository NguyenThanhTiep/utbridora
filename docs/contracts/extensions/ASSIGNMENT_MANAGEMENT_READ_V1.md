# Assignment management read extension v1 — lượt 1, 25/11/2026

Additive implementation under the user's request to implement phases 1–2. Frozen SRS v1.2.4 and Contract Pack v1.1 remain byte-identical. This file documents DEV behavior; it does not declare QA/Lead/school acceptance or modify the four global roles.

## Existing commands

The UI reuses CP-AUTHZ1/CP-WS1 commands: `POST /t3/coordinators/confirm`, `POST /t3/hr-assignments`, `POST /t3/cases/{caseId}/mentor-handover`, `DELETE /t3/context-assignments/{id}`. Prefix every path with `/api/v1`. CSRF, `Idempotency-Key`, optimistic versions, authoritative parent checks, security invalidation and audit remain mandatory. No browser callback activates Mentor assignments. Mentor invitation acceptance remains mandatory.

## T3 read projections

Every list uses C.0 `Page<T> = {content,page,size,totalElements,totalPages}`. `page` is 0–10000; `size` is 1–100, default 25. Stable ordering includes resource ID. Reads have `Cache-Control: private, no-store`. Input filters select a scope; they never authorize it.

Base: `/api/v1/t3/assignment-management`.

| GET path | Parameters | Projection / guard |
|---|---|---|
| `/scopes` | page,size | Own current school confirmations, Coordinator or broad HR scopes. School requires current `t3:coordinator:confirm` delegation; enterprise requires effective assignment plus `t3:hr:manage` or `t3:mentors:manage`. Active terms only. No business authority from global ADMIN. |
| `/enterprises` | schoolOrgId,termId,page,size | School confirmer only. T5 supplies latest APPROVED school verification references for VERIFIED enterprises. Also includes enterprises where this actor already granted Coordinator so lapsed verification cannot hide revocation. Returns enterpriseOrgId/name/canConfirm; false means read/revoke only. |
| `/candidates` | schoolOrgId,enterpriseOrgId,termId,search?,page,size | School confirmer chooses Coordinator, Coordinator chooses HR. Excludes self, inactive accounts and inactive/foreign memberships. Only userId/displayName; no email/profile/password/security metadata. |
| `/assignments` | schoolOrgId,enterpriseOrgId,termId,includeInactive=false,page,size | Only records granted by the actor: SCHOOL→COORDINATOR, COORDINATOR→HR, HR→MENTOR. Includes current command DTO, displayName, effective, canRevoke. Current authority is checked before query/count. |
| `/assignments/{id}` | schoolOrgId,enterpriseOrgId,termId | Same guard and grantor restriction; missing/out-of-scope row returns 404. Includes version/decisionRef for explicit revocation. |
| `/groups` | schoolOrgId,enterpriseOrgId,termId,page,size | Coordinator/HR in selected scope; minimal groupId/code/cohortCode for constrained HR assignments. |
| `/cases` | schoolOrgId,enterpriseOrgId,termId,groupId?,page,size | Coordinator/HR; only grouped nonterminal cases in selected scope. Minimal caseId/groupId/groupCode/state/version; no student profile or contact data. |
| `/cases/{caseId}/mentors` | schoolOrgId,enterpriseOrgId,termId,page,size | HR only. Current active Mentor authority in the case's current group, matching school/enterprise/term and optional case restriction. Only userId/displayName. Does not grant or extend authority. |

Enterprise reads require `X-Context-Assignment-Id` belonging to the current actor. An invalid/missing business authority yields 403/404 under existing T3 error mapping. A supplied header is never silently ignored in favor of another role. School reads use current delegation; new confirmation candidates additionally require T5 approval. Existing grantor-owned records remain readable/revocable after verification lapses. School reads have no enterprise context header.

T1 owns member name lookup through internal `EnterpriseMemberReferencePort`; T3 validates scope before calling it. T5 owns verified enterprise references through its existing verification port. No new canonical identity/verification/preference tables or cross-owner writes.

## T2 preference reads prepared for lượt 2

| GET path | Behavior |
|---|---|
| `/api/v1/school/preferences/scopes` | Page of own active school/term references under `student:preference:school:review`. |
| `/api/v1/school/preferences` | `X-Organization-Id`, required termId, optional status SUBMITTED/WITHDRAWN/REVIEWED, page,size. Both membership and exact current delegated scope are required before query/count. Returns existing PreferenceView only. |
| `/api/v1/school/preferences/{id}` | Guard derives school/term from canonical row and requires review authority; wrong owner/scope or absent row yields 404. |

These reads do not grant student-profile access. REVIEWED means the intent was reviewed, not admission, nomination or Placement creation. The existing POST review command remains unchanged. UI for this workflow is lượt 2.

## T1 Mentor invitation reads prepared for lượt 2

`GET /api/v1/t3/mentor-invitations/sent` and `/sent/{id}` require enterpriseOrgId/termId plus the current broad HR context with `t3:mentor:invite`. T3 validates the context through the internal MentorScopePort. T1 queries only invitations issued by that same HR in exactly the current school/enterprise/term. The list is paginated; the detail is a 404 for another issuer/scope. A renewed HR context in the same scope can read its own earlier invitations; invitation activation still validates its original parent separately.

The read DTO is `{invitation: MentorInviteDto, email, recipientUserId}`. Email is the address the issuer supplied; no raw token, token ID/hash, password or onboarding credential is exposed. The receiver-only `/mentor-invitations/me` contract is unchanged. Reads do not use `FOR UPDATE`, issue mail, reissue, activate or revoke anything. HR invitation-management UI is lượt 2.

## UI and verification

Entry: **Phân công thực tập**. Backend discovery supplies current scopes. School selects an approved enterprise and candidate; Coordinator grants broad or group/Case-limited HR; HR selects case and eligible Mentor for handover. The grantor can read details/version and revoke with reason and explicit confirmation. The UI preserves exact payload/key for uncertain retries and locks scope switching while a command is pending. Definite version conflicts require loading current data before a new command. Unmount/logout removes scope data.

The real-MySQL `AssignmentManagementIT` covers boundaries, pagination/privacy, existing grant/revoke commands, descendant invalidation, handover/version/CSRF, school preference reads and issuer-only invitation reads. Its Chromium harness drives the new school→Coordinator→HR→handover→root revoke flow, reload and mobile layout. Run through existing isolated CI; do not run container tests on the shared local Docker daemon. No deploy/migration/runtime flags are required for this source change.
