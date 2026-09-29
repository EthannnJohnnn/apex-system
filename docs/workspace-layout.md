# Management workspace layout

This UI step established the layout before Stage 13. Attendance is now implemented; see [Stage 13](stage-13.md). The full Stage 15 dashboard/leaderboard remains later work.

- Login opens Dashboard; the sidebar separates Members, Point ledger, Settings, and future feature pages.
- Dashboard reads existing member, term and ledger APIs. Counts, the membership bar chart and recent transactions use database records, not sample data.
- Net points sum every ledger entry in the active term, including reversals and deductions. This is not an eligible-member leaderboard.
- Activities now provides the attendance workflow. Dashboard attendance trends and upcoming activities use actual records; Warnings remains marked as planned. No attendance data is invented.
- Organization/term controls and password changes are in Settings. Sign out and Light/Dark/System controls are available in the header.
- Narrow laptop windows use a navigation drawer. This does not enable phone/network access.
- Hash routes (for example `/#/members`) support page refresh without additional backend routes. Existing session and CSRF protections remain unchanged.
- Navigating away closes unsaved forms; save or cancel before switching pages. Dashboard reloads when revisited; Refresh overview reloads it in place.

Run the backend and frontend using the existing commands. Verify login, sidebar navigation, page refresh, light/dark themes, the ledger and logout. Build with `npm run build`; check lint with `npm run lint` from `frontend`.
