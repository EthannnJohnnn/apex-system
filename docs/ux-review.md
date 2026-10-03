# Apex UX review and interview

## Confirmed decision

On October 2, 2026, the project owner chose **dashboard first** after sign-in. Keep the next activity accessible through an attendance shortcut, without automatically redirecting to it. The owner has not yet supplied a rationale; do not invent one.

This is a design decision, not evidence from client usability research.

The project owner also confirmed that **points are applied only after Finalize attendance**. Save draft stores attendance without awarding points. This retains the existing scoring behavior.

The owner subsequently delegated the remaining design choices with the constraint to keep things simple. The defaults below are implementation decisions, not invented interview answers. No further interview is required to ship this refinement.

## Current design critique

Source review confirms that the previous dashboard refinement addresses the six original concerns:

- Wide laptop layouts pair attendance with upcoming activities, and leading members with recent point changes.
- Four cards replace the overlapping five-card summary.
- Attendance has labeled Present, Late, Excused and Absent segments and an explicit denominator.
- Upcoming activities link to their specific attendance sheet.
- Relevant links carry the selected term to attendance, ledger and leaderboard pages.
- Routine connection information is in the footer, with more prominent failure feedback and retry.

Evidence: `frontend/src/components/Dashboard.tsx`, `Workspace.tsx`, `ConnectionStatus.tsx`, `Activities.tsx`, `Points.tsx`, and `Leaderboard.tsx`. Earlier build/browser verification is recorded in [workspace layout](workspace-layout.md).

The sidebar, separate feature pages, preserved history and omission of private incident reasons from dashboard summaries remain strengths.

### Attendance refinements

`Activities.tsx` now distinguishes saved activity drafts, saved attendance drafts, finalization, cancellation and corrections. Draft messages explicitly say no points were applied. A marked-attendee count explains whether attendance is incomplete, unsaved or ready to finalize. Existing finalization guards remain unchanged.

A failed save can have an unknown outcome. The existing pending-request behavior retains the same request for retry and locks other mutations. Normal saving shows a progress message rather than an unconfirmed-result warning. Recovery copy explains that the request might have completed. Reload requires confirmation when there are local attendance edits or an unresolved request, and explains that saved server data replaces local edits. There is no autosave or offline queue.

The attendance sheet repeats the selected term and status. Finalized attendance explains the existing per-member correction action. Corrections retain history and require a reason. Attendance-linked ledger entries now offer **View source attendance**, preserving both term and activity context.

## Interview: questions and recorded answers

Scenario: The president opens Apex shortly before a meeting, finds the correct activity, records attendance and checks whether points have been applied.

1. **Starting screen:** Dashboard first. Rationale not yet provided.
2. **Immediate context:** Delegated default: term name/status, activity title, schedule and draft/finalized state.
3. **Save versus finalize:** Points apply only after Finalize attendance (confirmed October 2, 2026). Save draft does not award points. Exact feedback wording has not yet been evaluated with the participant.
4. **Interrupted save:** Delegated default: keep the same request for retry, explain uncertainty, and confirm before reloading local edits.
5. **Correction:** Delegated default: retain per-member correction with a reason, explain preserved history and link ledger entries to source attendance.

Design feedback: dashboard-first retains the overview; the activity shortcut keeps attendance accessible. Explicit draft/finalized feedback follows the owner's scoring rule without adding another workflow. These defaults have not been validated through client interviews.

## Exercise 1: five-second recall

Use a representative fictional dashboard with a visible term and upcoming activity. Record the actual displayed term and activity as the facilitator's answer key. Let the participant look for five seconds, then have them look away or cover the screen. Do not claim the interface has been hidden automatically.

Ask without hints: "Which term was selected? What needed attention? Where would you go to take attendance?"

Record the participant's words, correct/incorrect recall and any uncertainty. One response identifies a possible issue, not a measured general improvement. Status: awaiting participant response; no results recorded.

## Exercise 2: failure-state drafts

These failure-first scenarios informed the implemented feedback. They are not client research findings:

| Situation | Feedback and action |
| --- | --- |
| Draft saved | "Attendance draft saved. No points have been applied." Keep a DRAFT label. |
| Finalization succeeded | "Attendance finalized. Points recorded for eligible attendees." Keep a FINALIZED label and access to history/corrections. |
| Incomplete attendance | "3 attendees are still unmarked. Complete and save their attendance before finalizing." The number must come from actual state. |
| Unsaved changes | "Save your attendance changes before finalizing." Do not silently discard changes. |
| Save outcome unknown | "We couldn't confirm whether the save completed. Retry the same request, or reload to check the saved record. Reload replaces local edits with saved data." Do not automatically submit a new request. |
| Wrong term or activity | Keep both names and term status prominent before finalization. For a closed term, explain that ordinary edits are unavailable and historical corrections require a reason. |

Optional later usability check: ask what has been saved, whether points changed and which action the participant would take. This is not a prerequisite for the owner-delegated changes.

## Exercise 3: trace a meeting backward

Start in the disposable practice environment, not the live database. A synthetic example is a member whose only activity changes were Present +2, followed by a correction to Late: reversal -2 and replacement +1, net +1. Verify the actual ledger before using these amounts as an answer key; other transactions would change the total.

Ask the participant to explain the total using the ledger, identify the source activity and find the attendance correction, its reason and history. Record each navigation step and any place they lose the connection between records. Source review identified a missing direct attendance link in the ledger; that link is now implemented. The synthetic arithmetic is an exercise example, not a newly observed real meeting.

For a later real-meeting review, use authorized records privately and avoid copying incident narratives into portfolio material. Status: exercise prepared; no participant trace or real-meeting findings recorded.

## Verification and limits

Automated frontend tests cover draft/finalization feedback, incomplete/unsaved/ready states, term/activity links and existing interrupted-request behavior. No backend scoring, database records, network access or laptop-only scope was changed. Five-second recall and backward tracing remain prepared optional human exercises; no participant results, client time savings, accuracy improvements or satisfaction measurements are claimed.

Verification: all 11 frontend tests and the production build passed. The build retains its non-blocking bundle-size warning. This attendance refinement has not received a new interactive browser walkthrough; automated checks do not replace that or a client usability session.
