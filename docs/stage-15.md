# Stage 15 — Leaderboard and dashboard

Apex remains laptop-only and president-only. These pages summarize saved records; they do not create a second point balance or change the ledger.

## How to use

1. Start the backend and frontend normally, then sign in.
2. Dashboard opens first. Select the current active term or a historical term. Member counts/chart describe **current membership**; net points, open-warning count, attendance, upcoming activities, leading members and recent point changes belong to the selected term.
3. Open **Leaderboard** from the sidebar. Choose a term, then **Top ten, including ties** or **Full list**.
4. Use Refresh overview / Refresh rankings after recording changes elsewhere. Opening the full leaderboard from Dashboard keeps the selected term.
5. Compare a member's total with Point ledger. Awards, deductions, replacements and reversals all contribute to the total.

## Ranking rules

- Higher total first; zero-point and negative-total members are included.
- Equal totals share a competition rank: **1, 1, 3**. Names and member IDs provide stable ordering within ties, without breaking the tie.
- Top ten includes every member whose rank is at most 10. A boundary tie can show more than ten people; an all-zero eligible group shares rank 1.
- Active/draft terms use currently active, eligible members.
- Closed terms use the latest eligibility record at the original closure time. Later eligibility changes or member creation do not rewrite that roster. Inactive former members remain represented. The current president category is always excluded.
- Closed-term corrections still change ledger totals and therefore rankings. Rankings are calculated, not stored snapshots.
- The executive category is ineligible by default; an explicitly eligible executive follows the same eligibility rules as other members.
- The president is excluded from rankings. Private warning reasons, incident references, assigned roles and severity are not returned in leaderboard/dashboard summaries. Dashboard shows only the aggregate open-warning count and neutral point transactions; detailed records remain in Warnings/Point ledger.
- Dashboard net points include the whole selected-term ledger, so they need not equal the sum of only the eligible leaderboard members.

## Important files and syntax

- `backend/src/main/java/ph/edu/slsu/psim/apex/report/ReportService.java`: read-only ranking and dashboard queries.
- `ReportController.java`: `GET /api/v1/leaderboard?termId=...&view=top10` (or `all`) and `GET /api/v1/dashboard?termId=...`. Without a dashboard term, the active term is used. Both APIs require the president session.
- `frontend/src/components/Leaderboard.tsx`: term/view controls and accessible ranking table.
- `Dashboard.tsx`: summary API, term selector, charts, counts and leading members. `Workspace.tsx`: sidebar route.
- `ReportTests.java`: ties, boundaries, zero/negative totals, eligibility, historical terms, corrections, privacy, authentication, attendance and upcoming activities.
- PostgreSQL integration tests also check live/historical ranking queries and dashboard totals through application restarts and HTTP.

`LEFT JOIN` keeps members with no transactions. `COALESCE(SUM(amount), 0)` gives them zero rather than a missing total. A read-only repeatable-read transaction keeps each response internally consistent while reading several tables. No migration, extra package, hosting or phone setup is needed.

## Run and verify (Git Bash)

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

For safe fictional practice, stop the normal backend and use the Stage 14 preview command in [Stage 14](stage-14.md). Its fictional member/officer begin tied at zero; the president and default-ineligible executive stay out of the leaderboard. Record a fictional deduction or award, then refresh to see the ranking change. Practice data disappears when that backend stops.

Automated checks: `./mvnw.cmd test` in backend, `npm run build` and `npm run lint` in frontend. The isolated PostgreSQL checks additionally require `APEX_PG_TEST_PASSWORD`; never commit passwords. Tests use isolated test databases/schemas, not organization records.

Next: Stage 16 tests the complete end-to-end system.
