# QA03 — T3-12 Rubric, Publication, and Student Results

**Generated:** 2026-11-21
**Branch:** `qa03/week4-t312-tests`
**Base:** `origin/main`
**Scope:** Automated regression coverage only; no production code changed.

## Summary

| Validation | Status | Exit code | Result |
|---|---:|---:|---|
| Frontend build and type-check | PASS | 0 | `npm run build` |
| Java test compilation | PASS | 0 | `.\mvnw.cmd -B -DskipTests test-compile` |
| QA03 Playwright discovery | PASS | 0 | One QA03 scenario discovered |
| Maven verification | PASS | 0 | 1,372 unit tests: 0 failures, 1 skipped; QA03 integration: 1 passed |

## Environment and startup

- Docker: available — Docker Engine 29.9.2 (`desktop-linux` context).
- Node.js: available — v24.19.0.
- Browser E2E: Playwright Chromium, headless.
- Database: MySQL 8.4.13 via Testcontainers.
- Startup: PASS — Spring Boot test context and HTTP server started on a random port; Testcontainers migrations completed.

## QA03 scenario

`web-demo/tests/t3-12-qa03.spec.ts` exercises a full flow with isolated test accounts:

1. Select the 40/60 rubric and enter scores 5 and 3; verify the calculated total is 76.00.
2. Save and submit the evaluation; verify the Student cannot view the result before publication.
3. Configure the HR and school visibility policies; publish and verify the Student can view 76.00.
4. Create a revision, change the scores to 1 and 1, and verify the Student continues to see the previously published 76.00 while the revision is a draft/submission.
5. Publish the revision and verify the Student now sees 20.00.
6. Open version 1 and verify the historical 76.00 is read-only.
7. Verify the Student browser does not call mentor evaluation/rubric/visibility APIs, and an outsider receives 404 without grade data.

The Java integration harness also verifies persisted history in MySQL: version 1 remains 76.00, the published version is 20.00, and the published pointer matches the current version. It allows intermediate immutable draft snapshots rather than assuming a fixed snapshot count.

## Evidence

Command:

```powershell
.\mvnw.cmd -B "-Dit.test=T312EvaluationBrowserIT#qa03VerifiesRubricScorePublicationGateAndImmutableHistory" verify
```

Final result: `BUILD SUCCESS`, exit code 0; QA03 integration test 1/1 passed in 81.69 seconds. Test output: `target/QA03_T312_verify.log`; Playwright report and screenshots are generated under `target/` and are not committed.

## Verdict

**PASS** — the QA03 browser journey and its database assertions pass against the application backed by MySQL Testcontainers. No fallback tiers were used.
