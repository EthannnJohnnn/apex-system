package ph.edu.slsu.psim.apex.member;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
public class MemberService {
    public enum Category { MEMBER, OFFICER, EXECUTIVE, PRESIDENT }
    public record Member(UUID id, String memberCode, String name, String position, Category category,
                         boolean eligible, boolean active, String notes, long version) {}
    public record Details(String memberCode, String name, String position, Category category,
                          Boolean eligible, String notes, String reason, Long version) {}
    public record Eligibility(Boolean eligible, String reason, Long version) {}
    public record Status(Boolean active, Long version) {}
    public record History(UUID id, boolean eligible, String reason, String changedBy,
                          OffsetDateTime effectiveAt, long memberVersion) {}
    private final JdbcTemplate jdbc;
    private static final RowMapper<Member> MAPPER = (rs, row) -> new Member(
        rs.getObject("id", UUID.class), rs.getString("member_code"), rs.getString("name"),
        rs.getString("position"), Category.valueOf(rs.getString("category")), rs.getBoolean("eligible"),
        rs.getBoolean("active"), rs.getString("notes"), rs.getLong("version"));

    public MemberService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Member> list() {
        return jdbc.query("SELECT * FROM member ORDER BY LOWER(name), member_code", MAPPER);
    }

    public Member get(UUID id) {
        return jdbc.query("SELECT * FROM member WHERE id = ?", MAPPER, id).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found."));
    }

    @Transactional
    public Member create(Details input, String actor) {
        Category category = category(input.category());
        boolean defaultEligible = category == Category.MEMBER || category == Category.OFFICER;
        boolean eligible = input.eligible() == null ? defaultEligible : input.eligible();
        validateEligibility(category, eligible);
        String reason = eligible == defaultEligible ? "Initial eligibility based on position category."
            : required(input.reason(), 500, "Eligibility reason");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO member (id, member_code, name, position, category, eligible, notes) VALUES (?, ?, ?, ?, ?, ?, ?)",
            id, code(input.memberCode()), required(input.name(), 150, "Name"),
            required(input.position(), 100, "Position"), category.name(), eligible, notes(input.notes()));
        history(id, eligible, reason, actor, 0);
        return get(id);
    }

    @Transactional
    public Member edit(UUID id, Details input, String actor) {
        Member current = get(id);
        checkVersion(current, input.version());
        Category category = category(input.category());
        if (input.eligible() == null) throw bad("Select point eligibility.");
        boolean eligible = input.eligible();
        validateEligibility(category, eligible);
        String reason = eligible != current.eligible() ? required(input.reason(), 500, "Eligibility reason") : null;
        int updated = jdbc.update("UPDATE member SET member_code = ?, name = ?, position = ?, category = ?, eligible = ?, notes = ?, version = version + 1 WHERE id = ? AND version = ?",
            code(input.memberCode()), required(input.name(), 150, "Name"), required(input.position(), 100, "Position"),
            category.name(), eligible, notes(input.notes()), id, input.version());
        checkUpdated(updated);
        if (reason != null) history(id, eligible, reason, actor, current.version() + 1);
        return get(id);
    }

    @Transactional
    public Member eligibility(UUID id, Eligibility input, String actor) {
        Member current = get(id);
        checkVersion(current, input.version());
        if (input.eligible() == null) throw bad("Select point eligibility.");
        validateEligibility(current.category(), input.eligible());
        String reason = required(input.reason(), 500, "Eligibility reason");
        if (input.eligible() == current.eligible()) throw bad("Eligibility has not changed.");
        checkUpdated(jdbc.update("UPDATE member SET eligible = ?, version = version + 1 WHERE id = ? AND version = ?",
            input.eligible(), id, input.version()));
        history(id, input.eligible(), reason, actor, current.version() + 1);
        return get(id);
    }

    @Transactional
    public Member status(UUID id, Status input) {
        Member current = get(id);
        checkVersion(current, input.version());
        if (input.active() == null) throw bad("Select active status.");
        checkUpdated(jdbc.update("UPDATE member SET active = ?, version = version + 1 WHERE id = ? AND version = ?",
            input.active(), id, input.version()));
        return get(id);
    }

    public List<History> history(UUID id) {
        get(id);
        return jdbc.query("SELECT * FROM member_eligibility_history WHERE member_id = ? ORDER BY member_version DESC",
            (rs, row) -> new History(rs.getObject("id", UUID.class), rs.getBoolean("eligible"), rs.getString("reason"),
                rs.getString("changed_by"), rs.getObject("effective_at", OffsetDateTime.class), rs.getLong("member_version")), id);
    }

    private void history(UUID id, boolean eligible, String reason, String actor, long version) {
        jdbc.update("INSERT INTO member_eligibility_history (id, member_id, eligible, reason, changed_by, member_version, effective_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), id, eligible, reason, actor, version, OffsetDateTime.now(ZoneOffset.UTC));
    }
    private static Category category(Category value) {
        if (value == null) throw bad("Select a position category.");
        return value;
    }
    private static void validateEligibility(Category category, boolean eligible) {
        if (category == Category.PRESIDENT && eligible) throw bad("The president cannot earn points.");
    }
    private static String code(String value) {
        return required(value, 64, "Student / Member ID").toUpperCase(Locale.ROOT);
    }
    private static String notes(String value) {
        String result = value == null ? "" : value.strip();
        if (result.length() > 2000) throw bad("Notes must be at most 2000 characters.");
        return result;
    }
    private static String required(String value, int max, String field) {
        if (value == null || value.isBlank() || value.strip().length() > max)
            throw bad(field + " is required and must be at most " + max + " characters.");
        return value.strip();
    }
    private static void checkVersion(Member member, Long version) {
        if (version == null) throw bad("Record version is required. Reload members.");
        if (member.version() != version) checkUpdated(0);
    }
    private static void checkUpdated(int count) {
        if (count != 1) throw new ResponseStatusException(HttpStatus.CONFLICT,
            "This member changed in another request. Reload members before editing again.");
    }
    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
