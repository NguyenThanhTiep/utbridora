# Package9: verification and operational preparation

This package adds executable DEV tools for R29/R30/R31 and DB/private-file recovery. It does not change frozen SRS/Contract, the user's Plan, running Docker, live data or acceptance status. The user selected preparation of the load recipe because a dedicated4vCPU/8GB SSD VM is not yet available. Fixed-hardware load and staging recovery therefore remain **NOT_RUN**.

## Requirement traceability

`docs/verification/requirements.json` is a read-only export of402 qualified requirement rows from the user's five team Plan sheets, with the exact Plan hash and approved SRS/Contract hashes. Team qualification preserves repeated NFR IDs. It includes Should/Could/policy rows as well as Must; `mandatory` is conservative for technical rules. This is an inventory, not402 verified requirements. `traces.json` contains reviewed mappings only; unreviewed requirements remain visible as SPECIFIED/NOT_RUN. The original workbook and frozen documents are unchanged.

```sh
python scripts/ops/rtm.py --output /private/evidence/rtm.json
python scripts/ops/rtm.py --evidence /private/evidence/VERIFIED_CI-RUN.json --output /private/evidence/rtm-with-runs.json
```

The second command needs actual `ci-RUN/*.zip` beside the verification JSON. It checks ZIP hashes and exact non-skipped integration testcase names before emitting HISTORICAL_PASS. Changed relevant source invalidates the binding. Positive/negative/boundary/race gaps and limits stay explicit. `--require-complete` fails while any mandatory mapping/evidence remains missing; a green regression run cannot hide those gaps. QA must complete per-requirement review and signoff; neither the exporter nor this task signs for QA/Lead/school. `CANONICAL_ORACLES.md` records superseded material without modifying original snapshots.

## Exclusive synthetic operational scope

Install the pinned Python dependency in a dedicated virtual environment:

```sh
python -m venv /private/pkg9-venv
/private/pkg9-venv/bin/pip install -r scripts/ops/requirements.txt
```

Commands run on the isolated host, using loopback MySQL. Never point them at the shared developer daemon, port8088 or current mixed `utbridora` schema. Use a separately provisioned/migrated `utbridora_pkg9_<16-to32randomhex>` schema and dedicated storage/backup/export roots. The DB user must be able to inspect all incoming/outgoing foreign keys on that isolated server. No fallback supports a shared/default schema.

Create a private JSON config with these fields (placeholders deliberately require operator input):

```json
{
  "version": 1,
  "exclusiveSynthetic": true,
  "writersStopped": true,
  "allCopiesRegistered": true,
  "schema": "utbridora_pkg9_REPLACE_WITH_RANDOM_HEX",
  "scopeId": "REPLACE_WITH_SCOPE_UUID",
  "serverUuid": "REPLACE_WITH_SELECT_SERVER_UUID",
  "host": "127.0.0.1", "port": 3306, "user": "REPLACE_WITH_ISOLATED_OPS_USER",
  "acceptedAt": "REPLACE_WITH_ACTUAL_ACCEPTANCE_UTC",
  "approvalRef": "REPLACE_WITH_OWNER_RETENTION_APPROVAL",
  "resources": [
    {"kind":"private-files", "path":"/private/demo/files"},
    {"kind":"backup", "path":"/private/demo/backups"},
    {"kind":"export", "path":"/private/demo/exports"}
  ]
}
```

Each root must already contain `.utbridora-demo-scope.json` exactly `{"scopeId":"same UUID","schema":"same schema"}`. Register every local/off-host backup/export copy, including dumps, snapshots, mail archives, browser/QA exports and transferred copies; bring copies under controlled registered roots or obtain separate destruction evidence before attesting `allCopiesRegistered`. Unknown copies prevent a complete retention claim. Use owner-only directory/file permissions. Keep configs, dumps, credentials and operational outputs outside Git. Supply the password only via `UTB_OPS_DB_PASSWORD`; it is absent from argv/reports.

Stop application/worker/backup writers before inventory, backup, restore or retirement. `writersStopped` is an operator attestation, not a distributed write fence; file hashes, server identity, FK checks and preview fingerprints detect changes but cannot replace stopping all writers. Restore/retention commands are offline maintenance, not background application workers. A changed database/resource requires a new preview.

## Paired DB/file backup and restore

1. Stop writers; capture the exact application commit, Java/MySQL/image digests, migration locations and private storage ownership. Dump the exact schema using MySQL8.4 `mysqldump --single-transaction --hex-blob --no-tablespaces --set-gtid-purged=OFF --routines --events --triggers --result-file=... SCHEMA`. Keep the dump under a registered private backup root. Supply MySQL credentials through the client environment or a private option file, never argv/logs.
2. Pair that dump with private-file bytes and the live database's per-table row/DDL digests:

```sh
python scripts/ops/handoff.py --config /private/scope.json --output /private/evidence/backup.json backup --dump /private/demo/backups/database.sql --bundle /private/demo/backups/paired-001
```

3. Provision a new isolated schema and an empty dedicated private root; use a different schema/scope marker/config. Restore SQL into the new schema with the MySQL client. Do not restore over the source. The `backup` command reports BACKUP_CREATED_NOT_RESTORE_VERIFIED until a real restore is checked.
4. Attach file storage only after comparing every restored table/DDL digest against the paired manifest:

```sh
python scripts/ops/handoff.py --config /private/clone.json --output /private/evidence/restore.json restore-files --bundle /private/demo/backups/paired-001
```

The tool rejects changed dump/blob hashes, mismatched metadata, symlinks and a nonempty file destination. It verifies bytes for every live committed file, preserving real scan states rather than setting CLEAN. A crash during a partial copy requires a new empty destination or a separately reviewed reconciliation; it does not overwrite an existing root. Run Flyway validate and no-op migration with the clone's actual lineage, then real owner/foreign/anonymous download checks, scanner/EICAR, range and revoked-access checks before reopening service. Existing FileScanningIT/PrivateFileServiceIT cover those guards; Package9OperationsIT adds paired recovery with a real scanner and the storage adapter.

RPO/RTO targets require PM/Ops decisions. Measure incident time, last recoverable transaction, start/end of restore and full authorization/file verification; record observed loss/downtime separately from targets. A successful synthetic restore and its duration do not certify production recovery, offsite schedules, encryption/key recovery or RPO/RTO.

## Retention preview, hold and apply

R30 retires the **entire exclusive synthetic dataset** after30 elapsed days from actual acceptance. It deletes child/business/session/token/mail/PII records in FK order, removes registered file/backup/export copies, and keeps non-login UUID tombstones for immutable audit references. Audit rows expire90 elapsed days after their own creation, unless an approved hold prevents the run. This mode must not be used for selective retirement in a mixed database. No scheduler or live deletion is enabled by this package.

```sh
python scripts/ops/handoff.py --config /private/scope.json --output /private/evidence/plan.json plan-retention --now 2027-01-31T00:00:00Z
python scripts/ops/handoff.py --config /private/scope.json --output /private/evidence/result.json apply-retention --plan /private/evidence/plan.json --now 2027-01-31T00:00:00Z --confirm-scope THE_EXACT_SCOPE_UUID
```

Use the real UTC clock in operations; the explicit clock is for deterministic day31/day91 rehearsal. A plan expires after one hour. To hold, add `"hold":{"approvalRef":"...","reason":"...","until":"...UTC..."}`; omit `until` for an indefinite approved hold. A changed hold/config invalidates an old plan. No automatic acceptance date is invented.

An audit receipt records the plan hash in the same transaction as DB retirement, without deleting in-window audit or disabling FKs. File deletion follows committed removal of bindings/credentials/sessions; a file failure remains a failed command and can resume using the same still-valid plan/receipt. A replay refuses changed/new resource bytes. If the plan expires during recovery, preserve the evidence and investigate/replan; never force a stale file list. Keep operational receipts outside the retiring roots and under the audit retention/hold policy. Day91 is a new preview/run, not an old plan replay. Never use retention to conceal an incident or erase held evidence.

## Fixed-hardware load preparation

Use a newly migrated empty isolated scope. `load_fixture.py` refuses existing user/org/profile/demand/taxonomy rows. It inserts deterministic5000 synthetic profiles,100enterprises,50000 skill relations, two term candidate sets400/5000, and50 scoped school identities. Students cannot log in, schoolApproved staysfalse; no token, real email, consent, Placement, real school approval or privacy release is created. The50 staff and HR share an operator-supplied BCrypt hash solely for this isolated load fixture. Provision its plaintext separately in private environment variables; never commit it or include it in reports. Stop workers and seed before starting the backend.

```sh
python scripts/ops/load_fixture.py --config /private/load-scope.json --confirm-scope UUID --start-date 2027-01-10 --end-date 2027-04-10 --output /private/evidence/seed.json
```

Supply the hash via `UTB_OPS_LOAD_PASSWORD_HASH`. Start the backend on the isolated DB with local-demo/bootstrap/import/scan/dispatch workers off. Supply `UTB_LOAD_HR_EMAIL=hr@load.invalid`, `UTB_LOAD_EMAIL_0=school0@load.invalid` through `UTB_LOAD_EMAIL_49=school49@load.invalid`, and `UTB_LOAD_PASSWORD` privately. All50 identities are distinct.

```sh
python scripts/ops/prepare_load.py --seed /private/evidence/seed.json --base-url http://127.0.0.1:8080 --commit EXACT40CHAR_SHA --output /private/load-manifest.json
```

This creates two valid Demands via real HR APIs, verifies400×5 and5000×10 matching once through a school session and verifies the5000-row paged details endpoint. It produces PREPARED_NOT_MEASURED. A failed setup cannot become a load PASS.

Record actual4vCPU/8GB SSD, Java/MySQL/container image digests, DB parameters, commit, rule/taxonomy/catalog versions, seed SHA256 and runtime/OS settings in `runtimeManifest`; attach SSD evidence in `ssdEvidence`. Restart/record the process state and attach `coldStartRef` before measuring. These fields are deliberately empty in the prepared file, so load cannot accidentally be certified from setup alone. Keep separate CPU/DB metrics and EXPLAIN ANALYZE artifacts; record the exact script commit and `pip freeze`.

```sh
python scripts/ops/load.py --manifest /private/load-manifest.json --output /private/evidence/load.json --validate-only
python scripts/ops/load.py --manifest /private/load-manifest.json --scenario matching400 --output /private/evidence/load400-measured.json
```

Recipes: matching400×5 and5000×10 each concurrency10/20warmups/100measured; p95≤2000ms/5000ms. Lists:50distinct logged-in sessions/300requests per endpoint, size≤100, p95≤800ms. Add each other common list/detail endpoint as a separate scenario with its expected result total. The initial recipe includes matching details; that endpoint alone does not certify every common API or T3's500-student/cohort requirement.

Measure one selected scenario per run. Repeat with `--scenario matching5000` and `--scenario matching-details5000`, restarting/recording a new `coldStartRef` for each. Cold first request, warmups and measured timings are separate. All measured errors, malformed responses and60-second timeouts remain in raw timings and nearest-rank p95; any error fails the recipe. Memory/CPU mismatch yields ENVIRONMENT_MISMATCH. SSD/runtime/commit/seed identity still needs operator review; the tool does not independently certify attestations. Performance samples include HTTP overhead. This is a reproducible test standard, not a production SLA or QA signature.

## DEV verification and final acceptance

Run unit/contract/build locally if useful. Run full MySQL/ClamAV/browser regression only on isolated CI/VM resources. The existing complete39-suite inventory is preserved; two new integration suites exercise operational recovery/retention and canonical load-fixture setup. CI uploads sanitized `target/package9/` reports without dumps/seed credentials. Python safety checks are part of the fast job; new ITs are automatically included by the complete-inventory sharder.

For final handoff retain exact commit/run IDs, raw ZIP hashes/XML, per-requirement RTM and gaps, full-chain real browser evidence, restore checksums, private-file verification, retention hold/day31/day91 reports and fixed-hardware load results. QA/Lead/school signoff, staging deployment, all-Must trace review, fixed-hardware measurements and operational recovery remain separate gates until actually performed. Plan statuses are not changed automatically.
