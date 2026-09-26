# Stage 9: member management

## What you can do

After signing in, add, search, edit, deactivate and reactivate members. Search matches
name, Student / Member ID, or position. The Show filter includes active, inactive or all records.
Members are records, never login accounts.

Each record stores name, unique Student / Member ID, position/title, position category,
point eligibility, active status and optional notes. IDs are trimmed and saved in uppercase;
inactive records still reserve their IDs.

## Rules

- Member and Officer categories are eligible by default.
- Executive Committee is ineligible by default, but may opt in with a reason.
- President is always ineligible, enforced by both backend and database.
- Select the correct category: the free-text position/title is descriptive, not a security role.
- Initial exceptions and every eligibility change require a reason.
- History records who changed eligibility, when it took effect and the reason.
  Earlier history is never rewritten. Later attendance/points stages must use eligibility
  effective at the activity time, not apply today's status to old activities.
- Deactivate instead of deleting. Reactivation preserves the existing eligibility choice.
- Inactive and eligible are different fields: future point/activity logic must check both.
- A record version prevents stale edits from overwriting newer changes. On a conflict,
  close the form, reload the list, open the record again and reapply your changes.

## Run it yourself (Git Bash)

Stop any old backend with Ctrl+C, then run:

```bash
cd /d/apex_system/backend
./mvnw.cmd spring-boot:run
```

The saved `APEX_DB_PASSWORD` Windows environment variable must be available to this
terminal. If you recently set it, restart VS Code. Do not paste passwords into source files.
The new V3 migration runs automatically on startup; do not execute its SQL manually.

In another Git Bash terminal:

```bash
cd /d/apex_system/frontend
npm run dev
```

Open the address Vite prints and sign in with your existing president account.
Do not run an old JAR: it lacks Stage 9. If you prefer the JAR, rebuild it first with
`./mvnw.cmd package`, then run `java -jar target/apex-0.0.1-SNAPSHOT.jar`.

## Your learning checkpoint

1. Add `Demo Member` with ID `DEMO-001`, Member category and Member position.
2. Confirm it appears and can be found by name or ID.
3. Edit the position and optional notes; reopen the form to verify them.
4. Change category to Executive Committee. Supply a reason for becoming ineligible.
5. Open History and verify both the initial eligibility and the new change.
6. Try adding the same ID again: Apex must reject it, including when the original is inactive.
7. Deactivate the record, select Inactive, then reactivate it.
8. Stop and restart the backend; refresh, sign in again and confirm the member remains.
9. Sign out and confirm member records disappear from the screen.

Use fictional records while learning. Never commit real member data or database backups.

## Files and flow

- `V3__create_members.sql`: database tables and constraints.
- `MemberController.java`: HTTP requests and readable error responses.
- `MemberService.java`: validation, defaults, transactional SQL and history.
- `Members.tsx`: list, search, forms, confirmations and eligibility history.
- `auth.ts`: shared request helper with session cookies and CSRF tokens.
- `PresidentAccess.tsx` / `App.tsx`: authenticated member screen and wider layout.

Flow: React form → API controller → service rules → PostgreSQL → saved record in React.

Important syntax:

- `@Transactional`: member and history changes succeed together or roll back together.
- SQL `?`: parameters keep values separate from SQL commands.
- `WHERE id = ? AND version = ?`: only update the version that was actually viewed.
- `@PostMapping` creates/changes records; `@GetMapping` reads; `@PutMapping` edits details.

## API

All routes require the president session; writes also require CSRF protection.

| Method | Route | Purpose |
|---|---|---|
| GET | `/api/v1/members` | List records (search/filter runs locally in React) |
| POST | `/api/v1/members` | Create |
| PUT | `/api/v1/members/{id}` | Edit details and eligibility |
| POST | `/api/v1/members/{id}/eligibility` | Change eligibility with reason |
| POST | `/api/v1/members/{id}/status` | Deactivate/reactivate |
| GET | `/api/v1/members/{id}/eligibility-history` | Read eligibility history |

There is no delete endpoint. No attendance, scoring, member login or LAN exposure is added.

## Automated verification

```bash
cd /d/apex_system/backend
./mvnw.cmd test
cd ../frontend
npm run build
npm run lint
```

The normal suite uses isolated H2 databases. The optional PostgreSQL test creates a
random `apex_stage9_test_...` schema inside local `apex_db`, tests a real HTTP login,
saves a member, restarts the application and checks persistence, then drops only that
test schema. It never changes the normal application's tables or president account.

To run it in Git Bash when `APEX_DB_PASSWORD` is inherited:

```bash
cd /d/apex_system/backend
APEX_PG_TEST_PASSWORD="$APEX_DB_PASSWORD" ./mvnw.cmd -Dtest=PostgresMemberPersistenceTests test
```

For disposable browser testing only, stop the normal backend and run:

```bash
./mvnw.cmd test-compile spring-boot:test-run -Dspring-boot.run.main-class=ph.edu.slsu.psim.apex.member.Stage9Preview
```

This uses an in-memory database and fictional login `preview_president` /
`fictional-preview-password`. Its records disappear when stopped. This test fixture is
excluded from the production JAR. Start the frontend normally; stop this fixture before
returning to your real local database.

Verification during implementation: backend rule/security tests, actual PostgreSQL restart
test, frontend build/lint, and browser checks for create/edit/search/history, executive opt-in,
duplicate-ID rejection and deactivation passed. Dark mode and phone-width layout were checked.

After your own checkpoint, the suggested commit is:
`feat(members): add member management and eligibility history`
