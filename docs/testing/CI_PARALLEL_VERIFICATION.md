# Parallel CI verification

The `Core platform verification` workflow runs three integration shards on separate
GitHub-hosted runners. Failsafe still uses one fork at a time and releases each
class's Spring context/Testcontainers at JVM exit. Browser ports and databases are
isolated by runner; no local/shared deployment is involved.

Runner labels are pinned to `ubuntu-24.04`, matching the verified main baseline,
to keep the operating-system migration of `ubuntu-latest` separate from this change.

Jobs start independently:

- `contracts`: existing contract, audit, portability and fixture checks.
- `fast`: sharding guard tests, frontend build/typecheck, Maven unit tests and packaging.
- `integration (1..3)`: compile tests, then run disjoint Failsafe suites with both
  `frontend.e2e=true` and `t5.week3.browser=true` as before.
- `verify`: retains the existing check name and succeeds only when every job above
  succeeds. Failed, cancelled or skipped prerequisites cannot produce a green gate.

`scripts/ci/integration_shards.py` discovers the default Failsafe names (`IT*`,
`*IT`, `*ITCase`). It assigns the longest suites first, using timing hints from
main run `43416579821` at SHA `f731320e300aead220e0d584ecbcd77f704e348e`.
New classes are included automatically with a 30-second estimate; the timing file
is a balancing hint, never a test allowlist. Each shard compares actual JUnit suite
reports against its complete planned class list and fails on missing, duplicate,
extra, empty or failed reports. Existing Maven `verify` remains unchanged for
developers; no POM or application behavior changes are required.

Artifacts are `test-results` (unit reports), `integration-results-1..3` (IT/browser
reports, logs, screenshots and shard manifests), and `contract-results`. Keep all
three integration artifacts when counting regression results. Matrix `fail-fast`
is disabled to retain diagnostic evidence from the other shards after a failure.
JUnit totals and a digest of the underlying XML reports are also emitted as GitHub
Checks notices, with bounded failure annotations. REST clients can inspect these
when downloading artifacts is unavailable; the raw reports remain in the artifacts.

New commits cancel superseded runs for the same DEV branch or pull request and
event type. Main and tag runs have unique concurrency groups so their evidence is
preserved. Push and pull-request events have separate groups. Full checks still
run for all changed paths, including SRS, contracts, migrations and configuration.

To inspect a shard locally without starting databases:

```sh
python -m unittest discover -s scripts/ci -p 'test_*.py'
python scripts/ci/integration_shards.py plan --shard 1 --shards 3
```

Actual CI time depends on runner availability and cache state. Measure wall time,
queue time and all test totals on the first pushed run before claiming a speedup.
The baseline Maven step took about 14 minutes, including about 13 minutes in 27 IT
suites. Parallel jobs may increase total runner minutes while reducing elapsed time.
