# Stage 14 — Warnings and deductions

Apex remains **laptop-only**, with one president account. This stage adds the Warnings page inside the existing management workspace.

## What to do

1. Start the backend and frontend with the usual commands below; database migrations run automatically.
2. Open **Warnings**, select the active term, and choose **Record incident**.
3. Choose a member and enter a consistent incident reference plus a reason.
4. Choose **Minor**, **Major**, or **None — deduction only**. A warning can have no deduction.
5. For an assigned-role no-show, select its finalized activity and enter the assigned role. Confirm that the member really had that assignment. Their recorded attendance must be **Absent**.
6. Include a deduction if appropriate. Role no-show amounts are fixed: **−3 meeting / −5 event**. Other incident deductions accept positive whole numbers from 1 to 1000; Apex subtracts them.
7. Use **History** to inspect the record. **Resolve** means the warning was handled; it keeps any deduction. **Cancel incident** preserves the record and reverses its deduction once.
8. Visit **Point ledger** to see the original deduction, any reversal, and the resulting total.

## Rules

- New incidents and ordinary resolutions/cancellations require an active term. Closed terms offer an explicit reasoned cancellation for an incorrect incident.
- Member + term + normalized incident reference identifies a general incident. Reusing it is blocked, including after cancellation. Use the same reference for the same event; Apex cannot infer that two differently worded references describe the same event.
- A role no-show is identified by member + activity, irrespective of the typed reference, severity, role text or retry ID. It cannot be penalized twice.
- Reference matching ignores letter case and repeated whitespace. References identify incidents, not login accounts.
- Role deductions use eligibility frozen by attendance at the activity date. Other deductions use current eligibility. The president never receives points; warnings without deductions remain possible for ineligible members.
- An assigned role is explicitly confirmed and recorded by the president here; Stage 14 does not add an advance role-assignment scheduler or infer assignments from a member's organization position.
- Recording an incident, its deduction, history and retry receipt is one transaction: all succeed together or none do.
- Repeating the same request does not repeat the deduction or cancellation. A different request for the same incident is rejected. Stale record versions require reloading.
- A linked deduction cannot be independently edited in Point ledger. Cancel its incident from Warnings. Cancel an existing role no-show record before correcting the corresponding attendance away from Absent.
- No record deletion is offered. Original details and chronological creation/resolution/cancellation history stay visible. PostgreSQL blocks update/delete/truncate of the audit history and retry receipts.
- Save or close a form before navigating away. After a connection interruption, retry the held request or reload and inspect history before starting again.

## Run (two Git Bash terminals)

```bash
cd /d/apex_system/backend
./mvnw.cmd spring-boot:run
```

```bash
cd /d/apex_system/frontend
npm run dev
```

Open the frontend address displayed in the terminal. No certificates, firewall changes or network setup are needed.

## Optional safe practice

Stop the normal backend first. This command uses only an in-memory fictional database:

```bash
cd /d/apex_system/backend
./mvnw.cmd test-compile spring-boot:test-run -Dspring-boot.run.main-class=ph.edu.slsu.psim.apex.warning.Stage14Preview
```

Sign in as `preview_president` with `fictional-preview-password`. It supplies four fictional members, an active term, and finalized meeting/event attendance marked Absent. Record a meeting role no-show for Fictional MEMBER: total becomes −3. Resolve its warning: total stays −3. Cancel it: total becomes 0, with both ledger entries preserved. Trying to record that same no-show again is blocked. All practice data disappears when you stop this process; restart the normal backend to return to PostgreSQL. The preview class is not packaged in the release JAR.

## Files and concepts

- `WarningService.java`: incident validation, duplicate protection, transactions, history, resolution and cancellation.
- `WarningController.java`: president-only `/api/v1/warnings` API; whole-number validation.
- V9/V10 migrations: incident/history/retry tables, ledger links and PostgreSQL audit protection. Earlier migrations are unchanged.
- `Warnings.tsx`: forms, term/status/search filters and history; Workspace and Points now link the workflow into existing pages.
- `WarningTests.java`: business rules, duplicate requests, concurrency, eligibility, closed terms and API security.
- `PostgresMemberPersistenceTests.java`: Stage 13 schema upgrade, actual HTTP login/creation/cancellation, application restarts, and immutable-history checks in an isolated random schema. Production tables are never used for test records.

`@Transactional` means the related database writes succeed together. A request ID makes a retry safe; an incident key prevents a second penalty under a new request. A reversal is a new positive ledger entry that cancels a deduction without deleting it.

## Verification

Automated coverage includes warnings without deductions, resolve-then-cancel history, fixed meeting/event penalties, duplicate incident requests, concurrent creation/cancellation, historical eligibility, stale versions, closed-term cancellation, authentication, CSRF and whole-number validation. The PostgreSQL check upgrades V8 to V10, restarts between creation/cancellation/retry, verifies the net total and tests append-only audit protection.

Frontend checks: `npm run build` and `npm run lint`. Backend checks: `./mvnw.cmd test`; the isolated PostgreSQL integration checks additionally require the opt-in `APEX_PG_TEST_PASSWORD` environment variable. Keep passwords out of committed files. Browser checks use the fictional preview to exercise create, resolve, cancel, ledger reversal and the assigned-role form.

Full leaderboard/dashboard work remains Stage 15.
