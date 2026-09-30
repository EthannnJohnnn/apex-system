# Management workspace layout

This UI step established the layout before Stage 13. Attendance is documented in [Stage 13](stage-13.md); term rankings and the summary dashboard are documented in [Stage 15](stage-15.md).

- Login opens Dashboard; the sidebar separates Members, Point ledger, Settings, and future feature pages.
- Dashboard reads its dedicated summary API. Counts, the membership bar chart and recent transactions use database records, not sample data. The Leaderboard sidebar page provides term selection and top-ten/full-list views.
- Net points sum every ledger entry in the selected term, including reversals and deductions. The separate leaderboard ranks only eligible members.
- Activities provides the attendance workflow. Dashboard attendance trends and upcoming activities use actual records. Warnings now provides Stage 14 incidents, optional deductions and resolution/cancellation history; see [Stage 14](stage-14.md).
- Organization/term controls and password changes are in Settings. Sign out and Light/Dark/System controls are available in the header.
- Narrow laptop windows use a navigation drawer. This does not enable phone/network access.
- Hash routes (for example `/#/members`) support page refresh without additional backend routes. Existing session and CSRF protections remain unchanged.
- Navigating away closes unsaved forms; save or cancel before switching pages. Dashboard reloads when revisited; Refresh overview reloads it in place.

Run the backend and frontend using the existing commands. Verify login, sidebar navigation, page refresh, light/dark themes, the ledger and logout. Build with `npm run build`; check lint with `npm run lint` from `frontend`.
