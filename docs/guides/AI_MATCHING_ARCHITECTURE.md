# AI Matching — architecture and repository survey

## Scope and approved baseline

Lượt 1 implements an optional **shadow semantic enhancement** on the existing school matching flow. Base: `c58a8ee896381e3d50f237b3c4e08ec715a2d836`. SRS v1.2.4 and Contract Pack v1.1 are approved at the hashes recorded in workspace AGENTS.md. Historical Candidate/PROPOSED labels do not revoke that decision. SRS §25.8 and BC11/12/31/32 remain authoritative.

The product direction updated on 21/11/2026 adds a separate **school AI advisory ranking**, described in the [roadmap](AI_MATCHING_ROADMAP.md) and [implementation prompt](AI_MATCHING_PROMPT.md). It may reorder eligible candidates in the new advisory view so school staff can use AI when choosing nominations. The table and lifecycle below describe the implemented lượt 1 baseline, not that future view.

Stage 5 produced the concrete [advisory extension specification](../contracts/extensions/ai-advisory-v1/README.md): owner ports and exact source versions, three REST operations, school capabilities/audit, ranking/paging, durable processing/retention and evaluation gates. Its frozen package records the specification handoff, before the subsequent runtime implementation.

Subsequent implementation authorization is recorded separately in [the stage 6 decision](../contracts/extensions/AI_ADVISORY_IMPLEMENTATION_DECISION.md). Lượt 3 now adds that advisory runtime in source; see [current implementation/evidence](AI_MATCHING_ADVISORY.md). The table below remains the original shadow architecture for traceability. Advisory uses separate owner snapshots, durable jobs and school-only endpoints, not the shadow memory store.

| Concern | Existing owner / implementation | Decision for this change |
|---|---|---|
| Strict classification / preferred scoring | T4 `StrictClassifier` | No changes |
| Canonical school evaluation / idempotency | T4 `MatchingService` / `OperationLedger` | Attach optional callback only inside a newly executed school mutation |
| Immutable student matching data | T2 `StudentProfileSnapshotPort` | Use the snapshots already captured by canonical evaluation; no later live reads |
| Demand criteria | T5 `DemandCriteriaPort` | Use the authoritative criteria already validated by evaluation |
| Enterprise release | T4 frozen dataset / privacy ledger | Entire path unchanged; never invoke semantic service |
| Nomination | T4 immutable refs → T5 | No semantic input or changes |
| Semantic inference | New internal FastAPI service | CPU E5, offline local model, bounded requests, no DB credentials |
| Semantic results | New internal T4 coordinator | Ephemeral bounded memory; no public API, canonical DTO field or DB migration |

## Data contract

The current ProfileSnapshot contains major, canonical skills/levels/years/source and availability; it does **not** contain projects, CV text, custom skill notes or identity fields. DemandCriteriaSnapshot contains structured criteria, not full JD text. The initial `canonical-v1` text template therefore describes only the structured snapshot. It does not claim to analyse full CV/JD content. Codes remain codes: no implicit aliases, verification, inferred skills or labels from a different taxonomy version.

Internal requests carry only a random batch requestId, template version, query text and ordinal passage keys. No actor, student UUID, school UUID, phone, email, name, file URL or CV bytes are sent. Semantic metadata binds results to the immutable run/dataset/profile/criteria versions internally. Strict relevantHash is not treated as a hash of future free-text sources.

## Transaction and lifecycle

The canonical school evaluation stays synchronous and transactional. When the feature is enabled it builds an immutable bounded shadow work item and registers a transaction synchronization. **Only after commit** may a single bounded worker call the AI HTTP service. Rollback, idempotent replay, enterprise evaluation and disabled mode do not infer. Registration, admission rejection and optional AI failures must not change a successfully computed canonical response.

No authentication object or live owner repository is passed to the background worker. No decision or UI currently consumes these shadow results. Results expire and disappear on restart. This is not a durable job queue, public semantic API or nomination signal. The revised lượt 2 specifies the data/permission/ranking extension; lượt 3 implements its backend and UI after the required decisions are resolved.

## Target advisory architecture

Strict Matching first determines the immutable population and `overallStatus`. Only `MATCH` candidates enter a separate advisory ranking snapshot. AI can change order in that snapshot; it cannot change canonical ordering, strict status or `fitScore`. The new school API/UI must rank the whole eligible snapshot before paging, enforce current school/term/demand authority, and identify model/template/policy and input versions. Missing text or failed inference is an explicit state, never a synthetic zero score.

T2/T5 owner ports must supply versioned, permitted project/experience/skill descriptions and JD/task text for meaningful semantic comparison. Canonical-v1 contains structured codes only. No cross-module live joins or CV extraction are implied. Any persistent jobs, cache, schema migration, audit and new endpoints belong to the separate extension defined in stage 5.

Staff review the advisory order and choose candidates through existing nomination guards. Approval, consent and sharing still govern what an enterprise may see. The current enterprise matching flow remains aggregate-only; the advisory view does not grant enterprise access to student lists. Evidence-based explanations must preserve self-declared source labels and must not infer verified skills from cosine similarity.

## Invariants

- Preserve strict candidate population, PASS/FAIL/UNKNOWN, SELF_DECLARED handling, overallStatus, preferred HALF_UP scoring, ordering, counters and pagination.
- Preserve enterprise frozen release fingerprint/ledger, bands and replay; model changes cannot produce another release.
- No inference in the canonical transaction, no automatic nomination or quota mutation.
- Flag defaults OFF; default application startup and readiness do not depend on AI.
- No raw content, embeddings, scores or PII in logs/errors/telemetry.
- No HTTP controller exposes the current internal result store; a future advisory API requires its own contract and permission enforcement. No authority or consent bypass.

## Runtime and evidence limits

Read-only resource inspection on 21/11/2026 observed only ~947 MiB free on the current 8 GB host. PyTorch/model inference and container tests must run elsewhere. Lightweight unit/protocol checks may use bounded-memory local processes. A stub-based test is protocol/behaviour evidence, never real-model/offline-model evidence.

No shared Docker/DB operation, commit, push, merge or deploy is authorized. Lượt 1 excludes Docker packaging, production persistence, UI expansion and full regression/benchmark from later lượt.

## References

- [E5 model card](https://huggingface.co/intfloat/multilingual-e5-small): 384 dimensions, query/passage prefixes and 512-token limit.
- [Offline configuration](https://huggingface.co/docs/huggingface_hub/en/package_reference/environment_variables): offline and telemetry switches; blocked-network inference still needs separate proof.
- [FastAPI process memory](https://fastapi.tiangolo.com/deployment/concepts/#memory-per-process): one worker by default to avoid model replication.
