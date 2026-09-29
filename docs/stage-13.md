# Stage 13 — Meetings, events and attendance

## What is ready

Use **Activities & attendance** in the sidebar. Choose a term and create a meeting or event with a title, schedule, expected attendees, and optional location/description. Only active members can be added; an existing attendee can remain after deactivation to preserve their record. Members still have no accounts.

1. Create an activity in the active term. Schedule uses **Asia/Manila** and must fall within the term dates.
2. Check the expected-attendee list. Draft details and the roster can be edited; type and term are fixed. Cancel and recreate if those are wrong.
3. Mark **Present, Late, Excused, or Absent** individually, or select several people and apply a bulk status.
4. **Save draft**. Unsaved marks are not finalized. A draft survives application restart but awards no points.
5. Mark every attendee and select **Finalize attendance**, then confirm. Finalization is not allowed before the activity starts.
6. To fix finalized attendance, select **Correct**, choose the intended status and give a reason. A closed term has an explicit **Correct closed term** action.

Save or cancel before switching sidebar pages; navigating away discards unsaved forms. Reload activities deliberately discards unsaved changes and fetches server records. After a network error, retry the retained request or reload and inspect history before creating a new action.

## Rules

| Status | Meeting default | Event default |
|---|---:|---:|
| Present | 2 | 3 |
| Late | 1 | 2 |
| Excused | 0 | 0 |
| Absent | 0 | 0 |

- Each activity copies Settings' point values when created. Later settings changes do not rewrite it.
- The checklist displays the **15-minute grace deadline**. The president confirms the status; Apex never automatically marks a very late person absent.
- Eligibility comes from the member's eligibility history **at the scheduled time**, then is frozen at finalization. A member with no eligibility history yet at that time cannot earn points. The president's ineligible history prevents points. Later eligibility changes cannot retroactively score older activities.
- Historical corrections retain that eligibility snapshot and the activity's saved point values. They can correct past attendance even after a member is deactivated or their eligibility changes.
- A draft may be cancelled with a reason; it remains visible and earns no points. Finish or cancel all drafts before closing a term. Finalized records are corrected, not deleted or reopened.
- Saving attendance and its ledger entries is one database transaction: either all succeed or none do.
- Request IDs make retries safe; record versions reject stale edits. A second finalization cannot award again, even with a different request ID.
- Nonzero initial attendance awards appear in the existing point ledger, linked to the activity. Corrections reverse and replace the previous entry. Initial zero-point outcomes remain in attendance history without unnecessary ledger entries.
- Attendance-linked ledger entries cannot be independently corrected through the manual points endpoint; use attendance corrections so both records agree.
- The dashboard shows the latest six finalized activities in the active term, with Present + Late divided by all expected attendees. Excused attendees remain in that denominator. The next three scheduled drafts are shown as upcoming activities. These are real records, not generated sample results.

## Main code

- `V7__activities_and_attendance.sql`: activities, rosters, audit history, retry receipts and activity links in the ledger.
- `V8__protect_attendance_history.java`: PostgreSQL prevents direct updates/deletes/truncation of audit history and retry receipts (not a defense against a database administrator).
- `ActivityService`: validation, scoring, transactions, history and summaries.
- `ActivityController`: `/api/v1/activities`; per-activity `/attendance`, `/finalize`, `/cancel`, `/corrections`, `/closed-corrections`.
- `AttendanceClosureCheck`: blocks term closure when drafts remain.
- `Activities.tsx`: forms and manual checklist. `Dashboard.tsx`: attendance chart and upcoming activities.
- `PointService` / `Points.tsx`: show the activity link and direct corrections back to attendance.

Important syntax: `@Transactional` makes related writes succeed together; `FOR UPDATE` locks shared records while writing; `UUID requestId` identifies a retry; `version` catches an edit based on an old screen. Flyway applies the new numbered migrations on normal startup—do not edit old migrations.

## Run normally (Git Bash, separate terminals)

```bash
cd /d/apex_system/backend
./mvnw.cmd spring-boot:run
```

```bash
cd /d/apex_system/frontend
npm run dev
```

Open the laptop's local frontend address and sign in normally. No firewall, certificates, phone setup or online hosting is needed.

## Optional fictional practice

Stop the normal backend first. This command runs an **in-memory test database**, never the real PostgreSQL records:

```bash
cd /d/apex_system/backend
./mvnw.cmd test-compile spring-boot:test-run -Dspring-boot.run.main-class=ph.edu.slsu.psim.apex.activity.Stage13Preview
```

Sign in as `preview_president` with `fictional-preview-password`. Four fictional members and an active practice term are supplied. Use a schedule within the last two days for eligible practice attendance. Create a meeting, bulk mark Present, save and finalize, then correct an ordinary member to Late. Their total should change from 2 to 1; the executive and president stay at 0. Check the ledger history and dashboard. Practice data disappears when this process stops; restart the normal backend to return to your real records. The preview class is excluded from the release JAR.

## Verification

Automated tests cover defaults/snapshots, bulk draft persistence, missing statuses, concurrent/repeated finalization, historical eligibility, zero outcomes, correction chains, active/closed-term rules, roster validation, stale edits, transaction rollback, authentication and CSRF. The opt-in PostgreSQL test uses a fresh random schema, upgrades from Stage 12, restarts the application around draft/finalization, verifies linked corrections and append-only triggers, then removes only that test schema.

Stage 14 warnings and role-no-show deductions are not included here. Full leaderboard/dashboard work remains Stage 15.
