package ph.edu.slsu.psim.apex.points;

import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ph.edu.slsu.psim.apex.organization.OrganizationService;

@Service
public class PointService {
    public record Command(UUID requestId, UUID termId, UUID memberId, Integer amount, String reason) {}
    public record Correction(UUID requestId, Integer amount, String reason) {}
    public record Entry(UUID id, long sequence, UUID requestId, UUID termId, UUID memberId, int amount,
        String kind, UUID reversesId, UUID replacesId, String reason, String actor, OffsetDateTime recordedAt, UUID activityId) {}
    public record Total(UUID memberId, String memberCode, String name, boolean active, boolean eligible, long total) {}
    public record Ledger(OrganizationService.Term term, List<Total> totals, List<Entry> entries) {}
    private record Request(UUID id, String operation, UUID term, UUID member, UUID source, int amount, String reason, String actor) {}
    private final JdbcTemplate jdbc;
    private final OrganizationService terms;
    private static final RowMapper<Entry> ENTRY = (rs,row) -> new Entry(rs.getObject("id",UUID.class), rs.getLong("sequence_no"),
        rs.getObject("request_id",UUID.class), rs.getObject("term_id",UUID.class), rs.getObject("member_id",UUID.class),
        rs.getInt("amount"), rs.getString("kind"), rs.getObject("reverses_id",UUID.class), rs.getObject("replaces_id",UUID.class),
        rs.getString("reason"), rs.getString("actor"), rs.getObject("recorded_at",OffsetDateTime.class), rs.getObject("activity_id",UUID.class));
    public PointService(JdbcTemplate jdbc, OrganizationService terms) { this.jdbc=jdbc; this.terms=terms; }

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Ledger ledger(UUID termId) {
        var term = terms.get(termId);
        var entries = jdbc.query("SELECT * FROM point_entry WHERE term_id=? ORDER BY sequence_no DESC", ENTRY, termId);
        Map<UUID,Long> sums = new HashMap<>();
        entries.forEach(e -> sums.merge(e.memberId(), (long)e.amount(), Long::sum));
        var totals = jdbc.query("SELECT * FROM member ORDER BY LOWER(name),member_code", (rs,row) -> {
            var id = rs.getObject("id",UUID.class);
            return new Total(id,rs.getString("member_code"),rs.getString("name"),rs.getBoolean("active"),rs.getBoolean("eligible"),sums.getOrDefault(id,0L));
        });
        return new Ledger(term, totals, entries);
    }
    @Transactional
    public List<Entry> award(Command command, String actor) {
        if (command.termId()==null || command.memberId()==null) throw bad("Choose a term and member.");
        Request request = request(command.requestId(), "MANUAL", command.termId(), command.memberId(), null, command.amount(), command.reason(), actor);
        if (request.amount()==0) throw bad("An award or deduction cannot be zero.");
        serialize();
        if (replay(request)) return response(request.id());
        terms.requireActiveForWork(request.term());
        eligible(request.member());
        save(request);
        insert(request, request.amount(), "MANUAL", null, null);
        return response(request.id());
    }
    @Transactional
    public List<Entry> correct(UUID id, Correction input, String actor, boolean closed) {
        serialize();
        Entry source = jdbc.query("SELECT * FROM point_entry WHERE id=?", ENTRY, id).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Point entry not found."));
        Request request = request(input.requestId(), closed ? "CLOSED_CORRECTION" : "CORRECTION", source.termId(),source.memberId(),id,input.amount(),input.reason(),actor);
        if (source.activityId()!=null) throw conflict("Correct attendance in Activities so attendance and points stay consistent.");
        if (replay(request)) return response(request.id());
        var term = terms.get(source.termId());
        if (closed) {
            if (!term.status().equals("CLOSED")) throw conflict("Use the ordinary correction action for an active term.");
        } else terms.requireActiveForWork(source.termId());
        if (source.kind().equals("REVERSAL") || jdbc.queryForObject("SELECT COUNT(*) FROM point_entry WHERE reverses_id=?",Integer.class,id)>0)
            throw conflict("This entry is already reversed. Correct its latest replacement instead.");
        // Cancelling to zero must remain possible even after a member becomes inactive/ineligible.
        // Nonzero replacement points must still satisfy current eligibility.
        if (request.amount()!=0) eligible(source.memberId());
        save(request);
        insert(request,-source.amount(),"REVERSAL",source.id(),null);
        insert(request,request.amount(),"REPLACEMENT",null,source.id());
        return response(request.id());
    }
    private void serialize() {
        // Same lock order as term activation/closure; serializes retries and corrections.
        jdbc.queryForObject("SELECT id FROM organization_settings WHERE id=1 FOR UPDATE",Integer.class);
    }
    private void eligible(UUID id) {
        var rows = jdbc.query("SELECT active,eligible,category FROM member WHERE id=? FOR UPDATE", (rs,row) ->
            rs.getBoolean("active") && rs.getBoolean("eligible") && !rs.getString("category").equals("PRESIDENT"),id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Member not found.");
        if (!rows.getFirst()) throw bad("Only active, point-eligible members can receive awards or deductions. The president cannot earn points.");
    }
    private static Request request(UUID id,String operation,UUID term,UUID member,UUID source,Integer amount,String reason,String actor) {
        if (id==null) throw bad("A request ID is required for safe retries.");
        if (amount==null || amount < -1000 || amount > 1000) throw bad("Points must be whole numbers from -1000 to 1000.");
        if (reason==null || reason.isBlank() || reason.strip().length()>500) throw bad("A reason is required (maximum 500 characters).");
        return new Request(id,operation,term,member,source,amount,reason.strip(),actor);
    }
    private boolean replay(Request request) {
        var rows = jdbc.query("SELECT * FROM point_request WHERE id=?", (rs,row) -> new Request(rs.getObject("id",UUID.class),
            rs.getString("operation"),rs.getObject("term_id",UUID.class),rs.getObject("member_id",UUID.class),
            rs.getObject("source_id",UUID.class),rs.getInt("amount"),rs.getString("reason"),rs.getString("actor")),request.id());
        if (rows.isEmpty()) return false;
        if (!rows.getFirst().equals(request)) throw conflict("This request ID was already used for different details. Reload and start a new action.");
        return true;
    }
    private void save(Request r) {
        jdbc.update("INSERT INTO point_request (id,operation,term_id,member_id,source_id,amount,reason,actor) VALUES (?,?,?,?,?,?,?,?)",
            r.id(),r.operation(),r.term(),r.member(),r.source(),r.amount(),r.reason(),r.actor());
    }
    private void insert(Request r,int amount,String kind,UUID reverses,UUID replaces) {
        jdbc.update("INSERT INTO point_entry (id,request_id,term_id,member_id,amount,kind,reverses_id,replaces_id,reason,actor) VALUES (?,?,?,?,?,?,?,?,?,?)",
            UUID.randomUUID(),r.id(),r.term(),r.member(),amount,kind,reverses,replaces,r.reason(),r.actor());
    }
    private List<Entry> response(UUID requestId) { return jdbc.query("SELECT * FROM point_entry WHERE request_id=? ORDER BY sequence_no",ENTRY,requestId); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
