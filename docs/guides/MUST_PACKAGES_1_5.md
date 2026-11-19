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

Roster provisioning, bulk preview/confirm/job recovery, durable activation mail and the school import UI are the next implementation stages. They are not claimed complete by the package-1 checks.
