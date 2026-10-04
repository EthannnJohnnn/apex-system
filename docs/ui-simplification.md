# UI simplification

## Design references

Reviewed the caption transcripts for the owner's two Kole Jain videos:

- [UI/UX concepts](https://www.youtube.com/watch?v=EcbgbKtOELY), via [captions](https://youtube2text.diguardia.org/v/every-ui-ux-concept-explained-in-under-10-minutes-EcbgbKtOELY): visual cues, hierarchy, spacing, typography, purposeful color and feedback.
- [Dashboard design](https://www.youtube.com/watch?v=B7k5rOgmOGY), via [captions](https://prepublish.ai/youtube-transcript/B7k5rOgmOGY): short navigation, separated settings, compact panels, useful charts and focused interactions.

These principles were adapted to Apex. No new integrations, decoration-only charts or optimistic point writes were added.

## Changes

- Removed the pictured sidebar slogan, laptop badge, repeated captions and duplicate Members / Point ledger headings.
- Kept labeled navigation and active states; moved Settings to the bottom.
- Smaller page headings and sentence-case buttons retain the existing font, red brand and light/dark palettes.
- Compact dashboard term selector and action row replace the large introduction card. Closed terms use “View attendance.”
- Reduced metric copy while distinguishing current membership from selected-term totals. Four attendance outcomes, counts, rates and term-preserving links remain.
- Optional scoring, ranking, warning and point explanations use keyboard-accessible disclosures. Important confirmations, unsaved changes and uncertain-save recovery stay visible when needed.
- Short attendance field labels retain attendee-specific accessible names. Draft saves still apply no points; finalization applies scoring.

## Verification

The browser preview mounted actual components with fictional in-memory responses, without production accounts or database writes.

- Inspected populated light/dark dashboards and lower list panels.
- At a 1280px laptop viewport, content and viewport widths matched (1265px excluding the scrollbar): no horizontal overflow.
- Exercised dashboard/scoring disclosures by keyboard and opened ranking, warning and point help.
- Followed the upcoming-activity link to its matching sheet.
- Checked finalization disabled for unmarked/unsaved attendance; a fictional draft save reported no points applied and enabled finalization.
- Selected a closed term and verified empty states, preserved links and the View attendance action.
- Simulated a failed dashboard request: error feedback and Refresh remained available.
- Verified attendee-specific accessible names after shortening visible field labels.

These are UI checks with synthetic responses, not a new backend integration test or measured client usability study. Scoring, security, persistence and laptop-only deployment remain unchanged.

Final verification: all 11 frontend tests, ESLint and the production build passed. The existing non-blocking bundle-size advisory remains. Temporary preview files were deleted and no preview listener remained on port 5174. The screenshot in `tmp/dashboard-simplified-dark.jpg` uses fictional data and is not a release asset.
