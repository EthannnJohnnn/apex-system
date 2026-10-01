# Stage 16 — Complete-system verification

Apex is **laptop-only**. The original two-device checks are now two laptop sessions/tabs; the disconnected-phone check is a stopped-backend check. No phone, certificate, firewall, hosting or network setup is needed.

## What was added

- `backend/src/test/java/ph/edu/slsu/psim/apex/system/SystemWorkflowTests.java`: one connected workflow through a real HTTP server, independent cookie sessions and real CSRF tokens. Only the initial fictional president is provisioned directly; all workflow actions use the API.
- `frontend/tests/api.test.mjs`: five tests for failed connections, uncertain write outcomes, conflict messages, expired sessions/proxy errors, and successful authenticated writes. Uses Node's built-in test runner; no new dependency.
- `npm test` in the frontend runs these request-layer tests. These are not component/browser tests; browser checks are recorded separately below.

## Fictional workflow and expected results

1. Sign in in two sessions, set organization defaults, create/activate a term, and add a member, officer, executive and president.
2. Both sessions load the same member version. Save in one; the stale second save returns **409** with a reload message.
3. Manual points for the president and ineligible executive are rejected.
4. Create a meeting, then opt the executive into future points. Mark everyone Present and finalize twice using the same request ID. Only the member/officer get **2 each**; the executive and president get **0**. Only two ledger entries exist.
5. Correct the member to Late: their total becomes **1**, with reversal/replacement history.
6. Add a warning with a three-point deduction: their total becomes **−2**. Resolve it: still **−2**. Cancel and retry cancellation: **1**, with exactly one reversal.
7. Compare the ledger, leaderboard and dashboard. Net total is **3**; private incident details are absent from summaries.
8. Close the term. Ordinary awards/corrections fail. An explicit closed-term attendance correction restores the member to **2**. Historical ranks are **1, 1, 3**, including the zero-point eligible executive; the president stays excluded.
9. Log out. Every protected feature rejects the logged-out session with **401**; the other session remains valid.

## Coverage of the original checklist

| Rule | Evidence |
| --- | --- |
| Logout blocks access | `AuthTests` and `SystemWorkflowTests`, including all protected feature reads |
| President/ineligible executives get no points | `ActivityTests`, `PointTests`, and the complete HTTP workflow |
| New eligibility does not score earlier activities | `ActivityTests` historical/new-member cases and HTTP opt-in-after-schedule case |
| Finalizing twice awards once | `ActivityTests` concurrent finalization, HTTP retry, PostgreSQL restart test |
| Corrections produce the right net amount | `PointTests`, `ActivityTests`, complete HTTP ledger-entry/count assertions |
| Warning cancellation reverses once | `WarningTests` concurrent/retry cases, HTTP workflow, PostgreSQL restart test |
| Closed terms reject ordinary edits | `OrganizationTests`, `ActivityTests`, `WarningTests`, HTTP workflow |
| Conflicting edits receive a clear error | HTTP two-session 409 and browser two-tab check |
| Connection failure never reports an unconfirmed save | Frontend request tests and browser stopped-backend check |

## Data safety

The new full workflow creates a randomly named **separate in-memory H2 test database**, binds its server to `127.0.0.1` on a random port, and closes it after the test. Explicit datasource settings prevent fallback to the organization database. Unit/integration test resources also use H2.

The pre-existing opt-in PostgreSQL regression tests additionally exercise native migrations, audit triggers and restarts in randomly generated **isolated schemas** in local `apex_db`. They never insert test records into `public` and remove only their generated schemas afterward. They require `APEX_PG_TEST_PASSWORD`; without it, those three tests are skipped. H2 success alone must not be described as a PostgreSQL verification. Never commit credentials or run test fixtures against client tables.

Browser checks use the existing `Stage14Preview`, which has disposable fictional data, not PostgreSQL. Stopping it discards only that practice data.

## Verification results — 1 October 2026

- Backend package: **56 tests passed, zero failures/errors/skips**, including all three PostgreSQL tests.
- Frontend request tests: **5 passed**.
- Browser: signed in, created a fictional member, saved one edit, rejected the second tab's stale edit with a clear reload message. Stopped the fictional backend, then submitted a new member form: error remained visible, typed fields stayed present, no success notice appeared.
- Frontend production build and lint: **passed**. Vite still reports the existing non-blocking bundle-size advisory (about 605 kB before gzip).

## Run checks in Git Bash

```bash
cd /d/apex_system/backend
./mvnw.cmd test
```

For a release/package check use `./mvnw.cmd package`. To include the optional PostgreSQL checks, supply `APEX_PG_TEST_PASSWORD` securely in the current shell; do not type the literal password into a command that will be saved in shell history. An unset password results in three skips, not proof of PostgreSQL success.

```bash
cd /d/apex_system/frontend
npm test
npm run build
npm run lint
```

`npm test` needs the project's Node 24 runtime (native TypeScript loading). Normal development still uses the backend and frontend terminals described in [Stage 15](stage-15.md).

Next: [Stage 17 — backups and recovery](stage-17.md). Backups/restore and client packaging are not claimed complete by Stage 16.
