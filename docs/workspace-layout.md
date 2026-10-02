# Management workspace layout

This UI step established the layout before Stage 13. Attendance is documented in [Stage 13](stage-13.md); term rankings and the summary dashboard are documented in [Stage 15](stage-15.md).

- Login opens Dashboard; the sidebar separates Members, Activities & attendance, Point ledger, Leaderboard, Warnings and Settings, with a highlighted active page.
- Dashboard reads its dedicated summary API, not sample data. Four cards distinguish **current membership** (active and eligible members) from **selected-term results** (net points and open warnings). The academic term and its status remain prominent.
- The attendance chart shows Present, Late, Excused and Absent as proportional segments and explicit text counts. Its rate is (Present + Late) / all expected attendees; the denominator includes Excused and Absent. Zero expected attendees have no percentage, rather than a misleading 0%.
- Upcoming activities link directly to their attendance sheet. Attendance and ledger links preserve the selected term; the full leaderboard keeps term selection and includes tied ranks. No mutation happens when opening these links.
- Leading members and recent point changes share a lower row on wide laptop windows. Empty states use real absence of data, not placeholder figures. Private incident reasons remain outside the summary.
- Net points sum every ledger entry in the selected term, including reversals and deductions. The separate leaderboard ranks only eligible members.
- Activities provides the attendance workflow. Dashboard attendance trends and upcoming activities use actual records. Warnings now provides Stage 14 incidents, optional deductions and resolution/cancellation history; see [Stage 14](stage-14.md).
- Organization/term controls are in Settings. The header contains Light/Dark/System controls and an Account menu for account/password controls and sign out. The account dialog retains the existing password validation and session handling.
- A small footer indicator reports the last connection check; Recheck connection refreshes it and exposes technical details in a tooltip. A failed check shows an explicit retry message. It is not continuous monitoring.
- Narrow laptop windows use a navigation drawer. This does not enable phone/network access.
- Hash routes (for example `/#/members`) support page refresh without additional backend routes. Existing session and CSRF protections remain unchanged.
- Navigating away closes unsaved forms; save or cancel before switching pages. Dashboard reloads when revisited; Refresh overview reloads it in place.

Run the backend and frontend using the existing commands. Verify login, sidebar navigation, page refresh, light/dark themes, the ledger and logout. Build with `npm run build`; check lint with `npm run lint` from `frontend`.

## Disposable design preview

With port 8080 free, run from `backend` in Git Bash:

```bash
./mvnw.cmd test-compile spring-boot:test-run -Dspring-boot.run.main-class=ph.edu.slsu.psim.apex.system.DashboardPreview
```

Run `npm run dev` from `frontend` separately. Sign in with `preview_president` / `fictional-preview-password`. This test-only entry point uses loopback and an in-memory H2 database, with four fictional members, mixed attendance, a closed empty term and an upcoming activity. It never connects to the production PostgreSQL database and is excluded from the release JAR. Stop both preview processes after checking. Production startup and credentials remain unchanged.

Check the four cards, labeled chart, direct attendance shortcut, selected-term ledger and leaderboard links, historical empty states, account menu/password form, keyboard focus, light/dark themes and footer recheck. Unit checks: `npm test` in `frontend`. The portfolio narrative is separate in [Apex case study](apex-case-study.md); it makes no measured client-outcome claims.

## UI refinement verification (October 2026)

- Production frontend build, ESLint and eight Node tests passed. Tests cover the four-status attendance denominator, empty totals, term/activity links and existing API failure/CSRF behavior.
- The running H2 preview verified a real dashboard response, 2/4 attendance = 50%, the upcoming-activity link opening its specific draft, historical empty states, and the ledger retaining the selected closed term. The account menu opened its account/password dialog.
- Browser inspection covered light and dark laptop layouts. An isolated temporary UI fixture additionally verified a labeled 8/10 = 80% chart, paired lower panels, the compact footer and failed-connection retry recovery. This fixture used synthetic responses, not a real network outage, and was removed after verification.
- No production database records, account credentials, firewall rules or network exposure were changed. No client usability outcomes have been measured.
- The build still emits Vite's non-blocking bundle-size advisory; performance optimization is not claimed by this layout refinement.
