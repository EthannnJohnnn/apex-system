package ph.edu.slsu.psim.apex.organization;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrganizationService {
    public record Settings(String organizationName, Integer meetingPresent, Integer meetingLate,
                           Integer eventPresent, Integer eventLate, Long version) {}
    public record Term(UUID id, String name, LocalDate startDate, LocalDate endDate, String status, long version) {}
    public record Details(String name, LocalDate startDate, LocalDate endDate, Long version, String reason) {}
    public record Change(Long version) {}
    public record History(UUID id, String action, String name, LocalDate startDate, LocalDate endDate,
                          String status, String reason, String changedBy, OffsetDateTime changedAt, long termVersion) {}
    private final JdbcTemplate jdbc;
    private final List<TermClosureCheck> closureChecks;
    private static final RowMapper<Term> TERM = (rs, row) -> new Term(rs.getObject("id", UUID.class),
        rs.getString("name"), rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
        rs.getString("status"), rs.getLong("version"));

    public OrganizationService(JdbcTemplate jdbc, List<TermClosureCheck> closureChecks) {
        this.jdbc = jdbc;
        this.closureChecks = closureChecks;
    }
    public Settings settings() {
        return jdbc.queryForObject("SELECT * FROM organization_settings WHERE id = 1", (rs, row) ->
            new Settings(rs.getString("organization_name"), rs.getInt("meeting_present"), rs.getInt("meeting_late"),
                rs.getInt("event_present"), rs.getInt("event_late"), rs.getLong("version")));
    }
    @Transactional
    public Settings saveSettings(Settings input) {
        String name = text(input.organizationName(), 150, "Organization name");
        for (Integer value : new Integer[]{input.meetingPresent(), input.meetingLate(), input.eventPresent(), input.eventLate()})
            if (value == null || value < 0 || value > 1000) throw bad("Points must be whole numbers from 0 to 1000.");
        if (input.meetingLate() > input.meetingPresent() || input.eventLate() > input.eventPresent())
            throw bad("Late points cannot exceed present points.");
        version(input.version());
        updated(jdbc.update("UPDATE organization_settings SET organization_name=?, meeting_present=?, meeting_late=?, event_present=?, event_late=?, version=version+1 WHERE id=1 AND version=?",
            name, input.meetingPresent(), input.meetingLate(), input.eventPresent(), input.eventLate(), input.version()));
        return settings();
    }
    public List<Term> terms() {
        return jdbc.query("SELECT * FROM academic_term ORDER BY start_date DESC, name", TERM);
    }
    public Term get(UUID id) {
        return jdbc.query("SELECT * FROM academic_term WHERE id=?", TERM, id).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Term not found."));
    }
    private Term lock(UUID id) {
        return jdbc.query("SELECT * FROM academic_term WHERE id=? FOR UPDATE", TERM, id).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Term not found."));
    }
    /** Future points/attendance writes call this within their transaction to serialize with closure. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Term requireActiveForWork(UUID id) {
        Term term = lock(id);
        if (!term.status().equals("ACTIVE")) throw conflict("Choose an active term. Closed terms require a separate correction workflow.");
        return term;
    }
    @Transactional
    public Term create(Details input, String actor) {
        validate(input);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO academic_term (id,name,name_key,start_date,end_date) VALUES (?,?,?,?,?)",
            id, input.name().strip(), input.name().strip().toLowerCase(Locale.ROOT), input.startDate(), input.endDate());
        audit(get(id), "CREATED", "Term created.", actor);
        return get(id);
    }
    @Transactional
    public Term edit(UUID id, Details input, String actor, boolean correction) {
        validate(input);
        Term term = lock(id);
        checkVersion(term, input.version());
        if (correction != term.status().equals("CLOSED"))
            throw conflict(correction ? "Corrections are for closed terms only." : "This term is closed. Use Correct closed term with a reason.");
        String reason = correction ? text(input.reason(), 500, "Correction reason") : "Term details updated.";
        jdbc.update("UPDATE academic_term SET name=?, name_key=?, start_date=?, end_date=?, version=version+1 WHERE id=?",
            input.name().strip(), input.name().strip().toLowerCase(Locale.ROOT), input.startDate(), input.endDate(), id);
        Term saved = get(id);
        audit(saved, correction ? "CORRECTED" : "EDITED", reason, actor);
        return saved;
    }
    @Transactional
    public Term transition(UUID id, Change input, String actor, boolean activate) {
        // A singleton lock serializes activation requests, including requests for different terms.
        jdbc.queryForObject("SELECT id FROM organization_settings WHERE id=1 FOR UPDATE", Integer.class);
        Term term = lock(id);
        checkVersion(term, input.version());
        if (activate) {
            if (!term.status().equals("DRAFT")) throw conflict("Only a draft term can be activated.");
            if (jdbc.queryForObject("SELECT COUNT(*) FROM academic_term WHERE status='ACTIVE'", Integer.class) != 0)
                throw conflict("Close the current active term first.");
        } else {
            if (!term.status().equals("ACTIVE")) throw conflict("Only the active term can be closed.");
            if (closureChecks.stream().anyMatch(check -> check.hasUnfinishedDrafts(id)))
                throw conflict("Complete or cancel every attendance draft before closing this term.");
        }
        jdbc.update("UPDATE academic_term SET status=?, active_slot=?, version=version+1 WHERE id=?",
            activate ? "ACTIVE" : "CLOSED", activate ? 1 : null, id);
        Term saved = get(id);
        audit(saved, activate ? "ACTIVATED" : "CLOSED", activate ? "Term activated." : "Term closed.", actor);
        return saved;
    }
    public List<History> history(UUID id) {
        get(id);
        return jdbc.query("SELECT * FROM term_history WHERE term_id=? ORDER BY term_version DESC", (rs,row) ->
            new History(rs.getObject("id", UUID.class), rs.getString("action"), rs.getString("name"),
                rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
                rs.getString("status"), rs.getString("reason"), rs.getString("changed_by"),
                rs.getObject("changed_at", OffsetDateTime.class), rs.getLong("term_version")), id);
    }
    private void audit(Term term, String action, String reason, String actor) {
        jdbc.update("INSERT INTO term_history (id,term_id,action,name,start_date,end_date,status,reason,changed_by,term_version) VALUES (?,?,?,?,?,?,?,?,?,?)",
            UUID.randomUUID(), term.id(), action, term.name(), term.startDate(), term.endDate(), term.status(), reason, actor, term.version());
    }
    private static void validate(Details input) {
        text(input.name(), 100, "Term name");
        if (input.startDate() == null || input.endDate() == null || input.endDate().isBefore(input.startDate()))
            throw bad("Choose valid start and end dates; the end cannot precede the start.");
    }
    private static String text(String value, int max, String field) {
        if (value == null || value.isBlank() || value.strip().length() > max) throw bad(field + " is required (maximum " + max + " characters).");
        return value.strip();
    }
    private static void version(Long value) { if (value == null) throw bad("Record version is required. Reload first."); }
    private static void checkVersion(Term term, Long value) {
        version(value);
        if (term.version() != value) updated(0);
    }
    private static void updated(int count) { if (count != 1) throw conflict("This record changed. Reload before trying again."); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
