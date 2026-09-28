# Stage 12 — point ledger

The ledger is a permanent list of point changes. A member's term total is the sum
of those changes, not a number that can be overwritten.

## Run normally (Git Bash)

Backend terminal:

```bash
cd /d/apex_system/backend
./mvnw.cmd spring-boot:run
```

Frontend terminal:

```bash
cd /d/apex_system/frontend
npm run dev
```

Sign in and scroll to **Point ledger** below Organization & terms. Migrations V5
and V6 run automatically. No manual SQL, phone setup, or new password is needed.

## Safe practice without changing real records

Stop the normal backend first (Ctrl+C), then start this disposable preview instead:

```bash
cd /d/apex_system/backend
./mvnw.cmd test-compile spring-boot:test-run -Dspring-boot.run.main-class=ph.edu.slsu.psim.apex.points.Stage12Preview
```

Keep the frontend running normally. Sign in as `preview_president` with
`fictional-preview-password`. It includes a fictional eligible member, an ineligible
executive, and an active practice term. This test-only fixture is excluded from the
release JAR; its database disappears when stopped.

1. Award the fictional member 5 points with a reason: total 5.
2. Deduct 2 with a reason: total 3.
3. Correct the original +5 award to +3 with a reason: total 1.
   History now contains +5, -2, -5 (reversal), +3 (replacement).
4. Refresh: the same total and entries remain.
5. Try the ineligible executive: the award button is disabled and the backend also rejects it.
6. Close the practice term using Organization & terms; reload the ledger. Ordinary
   awards are blocked. Closed-term corrections are explicit and require a reason.
7. Create and activate another term, then reload the ledger and select it: total 0.
   Select the old term to see its original history and total again.

After practice, stop the preview and start the normal backend. Never use fictional
awards on real member records just to test the application.

## Rules and behavior

- Only the president can read or change points; all writes require CSRF protection.
- Manual awards/deductions require an active term and an active, eligible member.
  The president and ineligible executives cannot receive new points.
- Amounts are whole numbers. Manual entries range from -1000 to 1000 excluding 0.
  Deductions may make a total negative; totals are not silently clamped to zero.
- Every entry requires a nonblank reason of at most 500 characters.
- Manual entries are recorded now, not backdated. Changing eligibility does not
  rewrite older points or award points for earlier activities.
- Corrections append an equal-and-opposite reversal and an intended replacement
  in one transaction. Enter the replacement amount, not the difference. Use 0 to cancel.
- The original remains visible. Each entry can be reversed only once; correct the
  latest replacement if another correction is needed. Reversals cannot be corrected directly.
- Nonzero replacements still require current active/eligible status. Cancellation
  to zero is permitted after deactivation/ineligibility so a mistake can be removed
  without granting a new award. Historical legitimate points are otherwise preserved.
- A closed term blocks ordinary writes. A separate closed-term correction endpoint
  and clearly labelled UI action permit a reason-required correction without reopening it.
- Each action has a UUID request ID. Repeating the same ID and details returns the
  saved result, including after a term closes. Reusing it for different details returns 409.
- On an uncertain network result, the form retains the original request for retry.
  If you cancel or refresh, reload history before starting a new action; a new action
  has a new ID and is not considered a retry of the earlier one.
- PostgreSQL triggers reject direct UPDATE, DELETE, and TRUNCATE on ledger entries
  and request records. This protects against accidents, not a database administrator
  who deliberately disables protections. Backups are still necessary.

## API and important syntax

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/points?termId=UUID` | Selected term, all member totals (including zero), and history |
| `POST /api/v1/points` | Manual award/deduction |
| `POST /api/v1/points/{id}/corrections` | Correct an entry in the active term |
| `POST /api/v1/points/{id}/closed-corrections` | Explicit closed-term correction |

Manual body: `requestId`, `termId`, `memberId`, signed `amount`, `reason`.
Correction body: `requestId`, intended replacement `amount`, `reason`.
Write responses contain the saved entries. There are no PUT/DELETE ledger endpoints.

- `@Transactional`: request, reversal, and replacement commit together or all roll back.
- `FOR UPDATE`: serializes writes with term closure and eligibility changes.
- `requestId`: makes retries safe (often called idempotency).
- `reversesId` / `replacesId`: link a correction to its original entry.
- A consistent read transaction calculates totals from the same entries shown in history.

## Verification and future integration

```bash
cd /d/apex_system/backend
./mvnw.cmd test
APEX_PG_TEST_PASSWORD="$APEX_DB_PASSWORD" ./mvnw.cmd test
cd ../frontend
npm run build
npm run lint
```

Normal tests use isolated H2 data. The optional PostgreSQL test uses a temporary
random schema, verifies migrations, HTTP writes, retries, correction history,
database immutability, and persistence after restart, then drops only that schema.
It never touches normal application records. H2 does not implement the PostgreSQL triggers.

Stage 13 adds attendance-linked points; Stage 14 adds incident/warning-linked
deductions. They must keep ledger entries append-only, use stable source/request
identifiers, and evaluate eligibility effective at the activity time. Do not
substitute this manual-entry endpoint for automatic historical attendance scoring.

Suggested commit: `feat(points): add term-based point ledger and audited corrections`

Implementation verification: 28 backend tests passed with PostgreSQL enabled; the
backend release JAR, frontend build, and lint passed. Browser checks used disposable
records and verified +5, -2, correction to +3 (net total 1), preserved reversal links,
reload persistence, an ineligible member's disabled award button, and light/dark layouts.
Vite reports a non-blocking bundle-size advisory; packaging optimization can follow later.
