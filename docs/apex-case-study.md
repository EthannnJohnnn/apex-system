# Apex: building a focused workspace for an organization president

## Problem and scope

Apex brings member records, attendance, points and warnings into one laptop-based workspace. The design challenge was not simply displaying records—it was helping the president understand how actions affect those records without losing their history.

During development, I clarified the scope around a single president account and member records rather than member accounts. I also reconsidered phone access: because the laptop would still need to remain running, the additional setup did not fit the client's primary workflow. Returning to a laptop-only design kept the product focused.

## My contribution and process

Working with AI-assisted development, I guided the requirements, reviewed the implementation and tested the workflows. The interface evolved from an initial branded screen into a management layout with a sidebar, dashboard and dedicated feature pages.

The dashboard refinement separates current membership from selected-term results. It uses four focused summary cards, labeled attendance outcomes and direct links from upcoming activities to their attendance sheets. Account actions live behind an account menu, and routine connection information stays near the footer. These are design decisions intended to improve clarity—not measured usability outcomes.

Preserving history remains central: attendance finalization, point corrections and warning changes must remain understandable rather than silently replacing earlier records. The dashboard excludes private incident reasons from its summary.

## Current outcome and limits

The implementation has passed automated checks and fictional workflow testing, including conflicting edits and failed connections; see the Stage 16 verification notes. This establishes technical evidence, not proof of client satisfaction or usability. Packaging and client handover remain separate work.

My main insight was that a useful management system needs more than successful data entry: it must make the current state, consequences and recovery options understandable.

## What to evaluate with the client

- Can the president identify the selected term and distinguish historical results from current membership?
- Can they open the intended attendance sheet from the dashboard without searching again?
- Do they understand the difference between saving a draft and finalizing attendance?
- Can they recover from a failed connection without accidentally repeating an action?

Do not claim time savings, improved accuracy or client satisfaction until measured with the client. This narrative records design intent and the development process; it is not a completed user-research study.
