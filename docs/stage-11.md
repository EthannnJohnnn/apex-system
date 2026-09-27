# Stage 11 — organization settings and academic terms

Apex stays laptop-only. Stage 10 phone access is omitted. This stage adds settings
and terms without changing your login or member records.

## Run (Git Bash)

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

Open the local address Vite prints, sign in, and scroll below Members to
**Organization & terms**. The V4 migration runs automatically; do not run it by hand.
Your existing Windows `APEX_DB_PASSWORD` must be available to the backend terminal.

## What to try

1. Save the organization name and point defaults; reload and verify they persist.
2. Create a fictional term with a name, start date, and end date.
3. Activate it. Create another draft: it cannot be activated while the first is active.
4. Close the first term, then activate the second. The first remains in the list.
5. Choose **Correct closed term**, update its name or dates, and provide a reason.
6. Open **Term history**: the original details, transitions, correction, actor, and time remain visible.
7. Restart Apex and verify the saved settings and both terms remain.

For disposable practice instead of adding fictional terms to your normal database,
use the Stage 9 preview command documented in `stage-9.md`. It also includes Stage 11;
all preview data disappears when the preview backend stops.

## Rules

- Terms start as drafts; only one may be active. Activation is manual, not date-driven.
- Dates describe the academic period; overlapping dates are allowed for preparation.
- Names must be unique ignoring case and surrounding spaces.
- Close the active term before activating the next. No deletion or reopening.
- Closed-term metadata can only change through the correction action with a reason.
- History stores immutable snapshots, not just the latest name and dates.
- Concurrent edits use a version number; stale writes return a reload message.
- Defaults start at meeting present/late 2/1 and event present/late 3/2.
  Values are whole numbers 0–1000; late cannot exceed present. Excused/absent stay 0.
- Settings are defaults for future activities, not changes to previously awarded points.

## Boundaries for the next stages

There is no ledger or attendance module yet. No fake totals or attendance records are
created by this stage. Stage 12 must attach each point transaction to its term, sum by
that term, and show a new term at zero while preserving earlier results.

The `TermClosureCheck` interface is the attendance integration point: Stage 13 must
provide a bean that checks for unfinished drafts in the selected term. Until that module
exists, there are no attendance drafts to block closure. Tests supply both unfinished
and finished/cancelled outcomes and verify that closure is blocked or allowed.

Future ordinary ledger/activity writes must call `requireActiveForWork(termId)` inside
their transaction. It locks the term until the write finishes, so closing cannot race
with creating an attendance draft. Future closed-term attendance/points corrections
need separate, reason-required audited workflows; the metadata correction here does
not edit scores, reopen a term, or change member eligibility.

## API and important syntax

All endpoints require the president session; writes require CSRF protection.

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/settings` | Read organization/default points |
| `PUT /api/v1/settings` | Save settings with `version` |
| `GET /api/v1/terms` | List current and historical terms |
| `POST /api/v1/terms` | Create a draft |
| `PUT /api/v1/terms/{id}` | Edit an open term with `version` |
| `POST /api/v1/terms/{id}/activate` | Activate with `version` |
| `POST /api/v1/terms/{id}/close` | Close with `version` |
| `POST /api/v1/terms/{id}/corrections` | Correct closed metadata with `version` and `reason` |
| `GET /api/v1/terms/{id}/history` | Read preserved snapshots |

Settings fields: `organizationName`, `meetingPresent`, `meetingLate`, `eventPresent`,
`eventLate`, `version`. Term detail fields: `name`, `startDate`, `endDate`, `version`,
and (for corrections) `reason`. Dates use `YYYY-MM-DD`.

- `@Transactional`: save a change and its history together, or neither.
- `SELECT ... FOR UPDATE`: hold a database row lock while a transaction works.
- `version`: reject an edit based on an outdated screen.
- `active_slot UNIQUE`: database-level protection against two active terms.
- Flyway V4: add new tables without replacing the existing member/account tables.

## Verification

```bash
cd /d/apex_system/backend
./mvnw.cmd clean test
cd ../frontend
npm run build
npm run lint
```

The opt-in PostgreSQL test also checks the V3-to-V4 upgrade, real HTTP settings and
term operations, correction history, and persistence after restarting the application.
It uses a randomly named temporary schema and removes only that schema afterward.
It never changes the real account or application records.

```bash
cd /d/apex_system/backend
APEX_PG_TEST_PASSWORD="$APEX_DB_PASSWORD" ./mvnw.cmd test
```

Suggested commit: `feat(settings): add organization settings and academic terms`

Implementation verification: all 21 backend tests passed with the PostgreSQL test
enabled; frontend build and lint passed. Disposable browser checks covered saving
settings, creating/activating/closing terms, creating a second term, correction
history, reload persistence, and readable light/dark layouts. No real records were
used for browser testing.
