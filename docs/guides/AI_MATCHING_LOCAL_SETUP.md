# AI Matching — safe local setup for lượt 1

## Current workspace

Implementation is in the separate `feature/team4-ai-matching-selfhosted` worktree. No commit/push/merge/deploy is performed. Keep the original main checkout, private Plan/config changes and stash intact.

These instructions exercise the implemented shadow foundation. The [revised roadmap](AI_MATCHING_ROADMAP.md) adds a separate school advisory ranking in later stages; no new ranking endpoint or UI is available from this setup. Future setup instructions must follow the stage 5 extension and cannot expose the current internal store directly.

Do not run model inference, Docker/container tests, database migrations/seeds or Compose build/up against the current shared workstation/demo. Read-only inspection observed ~947 MiB free RAM on an 8 GB host. New code remains default OFF.

## Lightweight development

Python baseline tested: CPython 3.12.14. Create a private venv and follow [AI service checks](../../ai-service/README.md#lightweight-checks). No PyTorch import/weights are needed for those tests. Keep test XML/logs and dependency freeze with the source manifest.

Java baseline: JDK 21, repository Maven wrapper/version. `mvn test` runs unit tests; `mvn verify` activates Failsafe and must not be run on the shared Docker daemon. Inspect the selected tests before running them. The lượt 1 focused suite uses no database or containers:

```sh
./mvnw -DforkCount=0 -Dtest=SemanticWorkTest,SemanticConfigurationTest,SchoolSemanticCoordinatorTest,SemanticHttpClientTest,MatchingServiceSemanticTest,StrictClassifierTest,ProtectedAggregatePolicyTest test
```

Use `.\mvnw.cmd` on Windows and an explicitly bounded Maven heap appropriate to available RAM. Cross-language loopback protocol probing uses the test-only `protocol_server.py` plus `PythonSemanticProtocolProbe`; it is not a model test or shared-stack integration test.

## Isolated real-model/backend environment

1. Allocate a separate CPU environment with measured RAM/CPU headroom.
2. Follow [model setup](../../ai-service/README.md#real-model-setup--isolated-host-only), preserving the manifest and full dependency freeze.
3. Configure the private service token, model path, reviewed revision and offline settings. Start one internal AI worker.
4. Run the explicit real-model offline smoke test and preserve its report.
5. Prepare a separately scoped synthetic backend database/files environment under the existing test/deployment rules. Do not use the default shared DB URL/port or shared volumes.
6. Configure Java `AI_MATCHING_ENABLED=true`, `AI_MATCHING_BASE_URL` and the same private token. Keep model revision aligned with the lock.
7. Execute an authorized school matching run. Canonical results must remain identical with AI ON/OFF. Enterprise responses and releases must not change.
8. Exercise timeout, overload, model absence, rollback, replay and permission failures. Record actual results.

There is no public semantic result endpoint in lượt 1; observe via the internal test harness/approved diagnostics only. Do not expose the package-private result store or add an endpoint to bypass this scope.

## Return to default behaviour

Set `AI_MATCHING_ENABLED=false` in the isolated environment and stop the separate AI process. No database migration or score rollback is required for lượt 1. No restart or configuration change to the shared demo is part of this task.
