# AI Matching — lượt 1 implementation

The product target now includes a school-facing AI advisory ranking, not only shadow scores. See the [updated prompt](AI_MATCHING_PROMPT.md) and [eight-stage roadmap](AI_MATCHING_ROADMAP.md). This document records the implemented lượt 1 foundation; no advisory API, UI or ranking consumer has been implemented by the documentation update.

## Implemented scope

- Python FastAPI service with strict internal schemas, shared-token authentication, bounded bytes/batches/chunks, CPU/offline E5 loader, checksum manifest verification, embedding/similarity and health/readiness.
- Model lock at revision `614241f622f53c4eeff9890bdc4f31cfecc418b3`; runtime does not follow `main` or accept a different configured revision.
- Java typed protocol/client with correlation, schema/template/model/revision/dimension/aggregation checks, finite scores, key mapping, bounded response bytes, no redirects and an overall HTTP deadline including streamed response bodies.
- School-only optional observer attached inside a newly executed canonical school evaluation. The observer freezes structured texts and refs before commit; only a bounded after-commit worker calls AI.
- One Java worker and one queued work item by default; maximum 5,000 profiles. Overflow rejects the entire shadow work, not a sample. An AI failure in a later batch discards all partial scores.
- At most eight result envelopes with a five-minute access/insertion expiry by default. Results are package-private diagnostics, ephemeral, cleared on close and lost on restart; not a durable job queue or public read API.
- Disabled observer by default. It creates no AI HTTP client/worker and requires no endpoint, token or model to start.

No change to strict classifier, canonical DTO/controller, nomination, enterprise evaluation/release, audit registry, Flyway, schema, frontend or existing Compose/launcher files.

## Snapshot provenance

The internal work envelope binds run/school/term/dataset/profile-set/criteria/catalog versions. Every score retains the immutable profile snapshot ref and an independent SHA-256 of the exact role/template/text input. Neither this ref nor domain IDs are sent to Python. The initial `canonical-v1` template uses only existing canonical snapshot fields and codes; it does not access live profiles, CVs, projects, names or descriptions from another module's tables.

No result consumer is implemented. Future persistence/UI must add current authority, stale/source/purpose checks and an approved extension contract; the package-private store must not be exposed directly.

## Configuration

| Environment variable | Default | Meaning |
|---|---|---|
| AI_MATCHING_ENABLED | false | Java school-only shadow switch |
| AI_MATCHING_BASE_URL | empty | Server-controlled internal AI origin, no credentials/query/path/fragment |
| AI_INTERNAL_TOKEN | empty | Private token shared by Java/Python; required only when enabled |
| AI_MODEL_REVISION | reviewed SHA | Must match model lock |
| AI_MATCHING_TIMEOUT_MS | 12000 | Overall HTTP deadline |
| AI_MATCHING_WORK_TIMEOUT_MS | 60000 | Entire shadow work budget |
| AI_MATCHING_QUEUE_CAPACITY | 1 | Waiting Java work items, bounded 1–8 |
| AI_MATCHING_RETAINED_RUNS | 8 | Ephemeral result count, bounded 1–32 |
| AI_MATCHING_MAX_PROFILES | 5000 | Whole-work admission limit |
| AI_MATCHING_RESULT_TTL_SECONDS | 300 | Ephemeral result expiry |

Python settings and conservative limits are in `ai-service/.env.example`. Long work may be rejected or time out; this never changes the canonical response. No retry is enabled in lượt 1.

## Runtime limits and remaining work

The current host lacked safe RAM headroom for a real model. Consequently model inference, blocked-network real-model proof and real MySQL-backed runtime integration are not claimed. Unit/HTTP protocol fixtures use synthetic encoders or owner ports and are labelled separately in the test report.

The revised plan has four lượt: lượt 1 is the existing stages 1–4 foundation; lượt 2 specifies stage 5 data/permission/advisory-ranking contracts; lượt 3 implements stage 6 durable processing, ranking API/UI and isolated Docker recipes; lượt 4 performs stages 7–8 real-model/full-regression/quality evaluation and an authorized pilot. Existing SRS/Contract approval is not reopened. New data sources and advisory behaviour are specified in a separate extension without modifying frozen baseline bytes.

The advisory feature must allow an AI-specific order among `MATCH` candidates while retaining canonical order in existing endpoints. It must rank a consistent full eligible snapshot before paging, handle missing data explicitly, enforce current authority and stale checks, and route staff choices through the existing nomination workflow. The current ephemeral diagnostic store does not satisfy these product requirements.

Lượt 2/stage 5 produced the [extension specification and handoff](../contracts/extensions/ai-advisory-v1/README.md), including machine-readable schemas and synthetic examples. That historical lượt changed specification/checker/guide files only. The later user-authorized lượt 3 has now implemented the [advisory backend/API/UI and isolated Docker recipe](AI_MATCHING_ADVISORY.md), with V44 prepared but not applied here. New capabilities require explicit delegation, flags remain OFF and real MySQL/model/full-stack validation remains separate. Historical lượt 1/2 reports retain their original source provenance.
