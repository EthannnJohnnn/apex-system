package ph.edu.slsu.psim.apex.activity;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ph.edu.slsu.psim.apex.organization.TermClosureCheck;

@Component
public class AttendanceClosureCheck implements TermClosureCheck {
    private final JdbcTemplate jdbc;
    public AttendanceClosureCheck(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean hasUnfinishedDrafts(UUID termId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM activity WHERE term_id=? AND status='DRAFT'", Integer.class, termId) > 0;
    }
}
