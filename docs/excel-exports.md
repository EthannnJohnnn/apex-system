# Excel exports

Sign in as president. On **Members** or **Attendance**, select **Export Excel**, choose a term, then **Download Excel**.

- Members workbook: all current member details (including inactive members), effective points earned, deductions and net points for the selected term. The Point history sheet includes every transaction, date, reason, actor, event and correction reference.
- Events workbook: all meetings and events in the term, including drafts and cancelled activities. The Attendance sheet lists each expected member, saved attendance status, eligibility at finalization and awarded points.

Search filters do not restrict exports. Save attendance edits before exporting. Drafts award no points; unmarked attendance is not treated as absent. Dates use Asia/Manila. Headers stay visible when scrolling, columns have filters, and IDs stay text to preserve leading zeros.

Member summary totals exclude reversed entries and their reversals. The complete signed point history still reconciles to net points. Current member names, positions and eligibility are not historical snapshots. Event scoring and finalized attendance eligibility use the saved snapshots.

Exports are read-only, available only to the president, and not cached by the API. Downloaded files contain member details and point reasons; share them only with authorized recipients. These reports are not database backups and cannot restore the system. Import is not included.

Implementation uses Apache POI 5.5.1 in the Java backend. No Excel installation is needed to generate files. Run `./mvnw.cmd test` in backend and `npm test`, `npm run lint`, `npm run build` in frontend.
