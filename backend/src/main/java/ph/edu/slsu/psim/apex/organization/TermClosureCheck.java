package ph.edu.slsu.psim.apex.organization;

import java.util.UUID;

/** Attendance (Stage 13) supplies a bean that reports unfinished drafts.
 * Draft writers must lock the term via requireActiveForWork in the same transaction. */
public interface TermClosureCheck {
    boolean hasUnfinishedDrafts(UUID termId);
}
