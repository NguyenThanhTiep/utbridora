# AI Matching — lượt 1 DEV test report (21/11/2026)

The later documentation update adds the [school advisory ranking roadmap](AI_MATCHING_ROADMAP.md) and [updated prompt](AI_MATCHING_PROMPT.md). Results below remain historical evidence for the original lượt 1 source manifest; they do not validate that future ranking, API/UI or semantic quality. Documentation-only changes are audited separately in the working checkpoint at `documentation-update/VERIFICATION.json`; runtime tests were not rerun for this edit.

## Source under test

Branch `feature/team4-ai-matching-selfhosted`, base `c58a8ee896381e3d50f237b3c4e08ec715a2d836`. Changes are **UNCOMMITTED**, not a new commit SHA. The working checkpoint contains `SOURCE_MANIFEST.json`, exact file/diff hashes, `FINAL_VERIFICATION.json`, original-workspace preservation and raw test reports.

Windows host, JDK 21.0.11 and CPython 3.12.14. Maven 3.9.15 used a task-local dependency cache and bounded heap (`-Xmx320m`, metaspace 128m, two active processors, in-process Surefire). This differs from isolated forked Linux CI and is not a replacement for its required checks. Read-only resource inspection observed ~947 MiB free on an 8 GB workstation; no PyTorch/model inference or Docker tests were run.

## Results

| Check | Result | Evidence / limits |
|---|---|---|
| Java production compile | PASS | 364 source files; no application/database startup |
| Full Java `*Test` unit selection | 1,420 PASS, 1 conditional SKIP, 0 failures/errors | 1,421 cases / 88 suites; includes 49 new semantic cases; no Failsafe/container test execution |
| Existing symlink test | SKIP | `PrivateFileStorageTest.rejectsSymlinkObjectAndRoot`; Windows did not permit creating symlinks |
| Focused Java semantic + strict/privacy suite | 55 PASS | Real service/classifier/Spring callbacks with mocked owner ports/JDBC; loopback HTTP responses are synthetic |
| Python service/core tests | 40 PASS | Authentication, closed schema, input limits, prefixes, chunking, finite vectors, errors, streaming deadline, busy-slot lifecycle; fake encoder, not real model |
| Java → actual Python FastAPI HTTP protocol | 1 PASS | Short-lived loopback Uvicorn service, synthetic encoder; validates metadata, score/null states, key mapping and chunk-limit flag |
| Existing canonical contract checker | 878/878 PASS | Generated manifest refreshed and diff reviewed; only source hashes changed/added, no canonical schema/permission/audit changes |
| Internal OpenAPI drift check | PASS | Exported from Pydantic/FastAPI without starting a service or model |
| Python compile/import/dependency checks | PASS | No PyTorch/weights installed; actual light environment freeze retained |
| Real E5 load/inference | NOT RUN | Insufficient safe RAM headroom on current shared host |
| Real-model blocked-network/offline test | NOT RUN | Explicit test/recipe supplied for a separate host; no inference proof claimed |
| Real MySQL-backed AI integration | NOT RUN | Shared Docker/database excluded; separate environment required |
| Browser/full container regression | NOT RUN | No UI change in lượt 1; isolated full regression remains later work |
| Semantic quality / VPS / R31 load | NOT RUN | No labelled relevance acceptance or fixed-hardware measurement |

## Failure history and warnings

The first all-unit attempt produced 12 AccessDenied errors during cleanup of existing private-file temporary directories in the sandbox. The unchanged full suite was rerun with the proper owner context and a task-local temp directory; final results above retain the genuine OS-conditional symlink skip. No tests or assertions were weakened.

The first canonical bundle check detected stale source hashes after implementation. The existing generator refreshed `docs/contracts/v1.1/manifest.json`; review confirmed only two changed source hashes and six added Java source hashes. Frozen SRS/Contract bytes and generated schemas/permissions/audit contents remain unchanged.

Known nonfailing warnings: existing Mockito/JDK dynamic-agent notice, in-process Surefire limitations, and Starlette's deprecation notice for its httpx-based test client. These do not certify compatibility with future versions.

## Behaviour verified at the stated layers

- Flag OFF starts the semantic configuration without AI URL/token/model or a network call.
- The real school MatchingService/classifier writes the same canonical result with AI ON/OFF.
- Inference is deferred until commit, runs on a different transaction-free worker, and does not run on rollback or idempotent replay.
- Optional AI/registration failure preserves canonical success.
- Admission and response sizes are bounded; a later-batch failure never publishes partial scores.
- Immutable snapshot refs/input hashes remain internal; Python receives ordinal keys and structured texts without domain UUIDs.
- Enterprise branch does not use the semantic observer. Existing strict/privacy oracles and contract checks remain green. Actual enterprise/MySQL/browser runtime regression is not claimed.

This is DEV verification of the implemented scope, not QA/Lead/school acceptance. The current shared demo is unchanged and does not have AI enabled.
