package ph.edu.slsu.psim.apex.warning;

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
public class WarningService {
    public enum Severity { MINOR, MAJOR }
    public record Create(UUID requestId,UUID termId,UUID memberId,String incident,Severity severity,
        UUID activityId,String assignedRole,Integer deduction,String reason) {}
    public record Change(UUID requestId,Long version,String reason) {}
    public record Warning(UUID id,UUID termId,UUID memberId,String memberName,String incident,Severity severity,
        UUID activityId,String assignedRole,int deduction,String reason,String status,long version,OffsetDateTime createdAt) {}
    public record History(String action,String reason,String actor,OffsetDateTime recordedAt,long version) {}
    public record View(Warning warning,List<History> history) {}
    private final JdbcTemplate jdbc;
    private final OrganizationService terms;
    private static final RowMapper<Warning> ROW=(r,n) -> new Warning(r.getObject("id",UUID.class),r.getObject("term_id",UUID.class),
        r.getObject("member_id",UUID.class),r.getString("member_name"),r.getString("incident"),r.getString("severity")==null ? null : Severity.valueOf(r.getString("severity")),
        r.getObject("activity_id",UUID.class),r.getString("assigned_role"),r.getInt("deduction"),r.getString("reason"),r.getString("status"),r.getLong("version"),r.getObject("created_at",OffsetDateTime.class));
    private static final String SELECT="SELECT w.*,m.name AS member_name FROM warning_record w JOIN member m ON m.id=w.member_id";
    public WarningService(JdbcTemplate jdbc,OrganizationService terms) { this.jdbc=jdbc; this.terms=terms; }
    @Transactional(readOnly=true)
    public List<Warning> list(UUID termId) {
        terms.get(termId);
        return jdbc.query(SELECT+" WHERE w.term_id=? ORDER BY w.created_at DESC,w.id",ROW,termId);
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public View view(UUID id) {
        var w=get(id);
        return new View(w,jdbc.query("SELECT * FROM warning_history WHERE warning_id=? ORDER BY version",(r,n) ->
            new History(r.getString("action"),r.getString("reason"),r.getString("actor"),r.getObject("recorded_at",OffsetDateTime.class),r.getLong("version")),id));
    }
    @Transactional
    public View create(Create input,String actor) {
        lock();
        String payload="CREATE:"+input;
        UUID replay=replay(input.requestId(),payload,actor);
        if (replay!=null) return view(replay);
        if (input.termId()==null || input.memberId()==null) throw bad("Choose a term and member.");
        terms.requireActiveForWork(input.termId());
        String incident=text(input.incident(),180,"Incident reference");
        String reason=text(input.reason(),500,"Reason");
        if (input.deduction()==null || input.deduction()<0 || input.deduction()>1000) throw bad("Deduction must be a whole number from 0 to 1000.");
        int amount=input.deduction();
        if (input.severity()==null && amount==0) throw bad("Choose Minor/Major or include a deduction.");
        var members=jdbc.query("SELECT active,eligible,category FROM member WHERE id=? FOR UPDATE",(r,n) -> new boolean[]{r.getBoolean(1),r.getBoolean(2),r.getString(3).equals("PRESIDENT")},input.memberId());
        if (members.isEmpty() || !members.getFirst()[0]) throw bad("Choose an active member.");
        var member=members.getFirst();
        String role="";
        String key="REFERENCE:"+incident.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
        if (input.activityId()!=null) {
            role=text(input.assignedRole(),150,"Assigned role");
            var activities=jdbc.query("SELECT a.kind,a.term_id,a.status AS activity_status,t.status,t.eligible_snapshot FROM activity a JOIN attendance t ON t.activity_id=a.id WHERE a.id=? AND t.member_id=?",
                (r,n) -> new Object[]{r.getString("kind"),r.getObject("term_id",UUID.class),r.getString("activity_status"),r.getString("status"),r.getBoolean("eligible_snapshot")},input.activityId(),input.memberId());
            if (activities.isEmpty()) throw bad("Choose an activity where this member was an expected attendee.");
            var a=activities.getFirst();
            if (!input.termId().equals(a[1]) || !"FINALIZED".equals(a[2]) || !"ABSENT".equals(a[3])) throw bad("A role no-show requires finalized ABSENT attendance in this term.");
            int fixed="MEETING".equals(a[0]) ? 3 : 5;
            if (amount!=0 && amount!=fixed) throw bad("Role no-show deductions are 3 for a meeting and 5 for an event.");
            if (amount>0 && (!(Boolean)a[4] || member[2])) throw bad("This member was not point-eligible for that activity.");
            key="ROLE_NO_SHOW:"+input.activityId();
        } else if (amount>0 && (!member[1] || member[2])) throw bad("Only point-eligible members may receive a deduction.");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM warning_record WHERE term_id=? AND member_id=? AND incident_key=?",Integer.class,input.termId(),input.memberId(),key)>0)
            throw conflict("This member already has a record for this incident, including cancelled records. Open its history instead of applying it again.");
        UUID id=input.requestId();
        jdbc.update("INSERT INTO warning_record(id,term_id,member_id,incident_key,incident,severity,activity_id,assigned_role,deduction,reason,status) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            id,input.termId(),input.memberId(),key,incident,input.severity()==null ? null : input.severity().name(),input.activityId(),role,amount,reason,"OPEN");
        if (amount>0) ledger(get(id),input.requestId(),-amount,null,reason,actor);
        audit(id,"CREATED",reason,actor,0);
        remember(input.requestId(),id,payload,actor);
        return view(id);
    }
    @Transactional
    public View change(UUID id,Change input,String actor,boolean cancel,boolean closed) {
        lock();
        String operation=closed ? "CLOSED_CANCEL" : cancel ? "CANCELLED" : "RESOLVED";
        String payload=operation+":"+id+":"+input;
        UUID replay=replay(input.requestId(),payload,actor);
        if (replay!=null) return view(replay);
        var w=get(id);
        if (input.version()==null || input.version()!=w.version()) throw conflict("This record changed. Reload before continuing.");
        String reason=text(input.reason(),500,"Reason");
        if (closed) {
            if (!cancel || !terms.get(w.termId()).status().equals("CLOSED")) throw conflict("Use the ordinary action for an active term.");
        } else terms.requireActiveForWork(w.termId());
        if (w.status().equals("CANCELLED") || (!cancel && (!w.status().equals("OPEN") || w.severity()==null))) throw conflict("This action is not available for this record.");
        if (cancel && w.deduction()>0) {
            UUID source=jdbc.queryForObject("SELECT id FROM point_entry WHERE warning_id=? AND kind='MANUAL'",UUID.class,id);
            ledger(w,input.requestId(),w.deduction(),source,reason,actor);
        }
        jdbc.update("UPDATE warning_record SET status=?,version=version+1 WHERE id=?",cancel ? "CANCELLED" : "RESOLVED",id);
        audit(id,operation,reason,actor,w.version()+1);
        remember(input.requestId(),id,payload,actor);
        return view(id);
    }
    private void ledger(Warning w,UUID request,int amount,UUID source,String reason,String actor) {
        jdbc.update("INSERT INTO point_request(id,operation,term_id,member_id,source_id,amount,reason,actor) VALUES (?,?,?,?,?,?,?,?)",request,"WARNING",w.termId(),w.memberId(),w.id(),amount,reason,actor);
        jdbc.update("INSERT INTO point_entry(id,request_id,term_id,member_id,amount,kind,reverses_id,reason,actor,warning_id) VALUES (?,?,?,?,?,?,?,?,?,?)",
            UUID.randomUUID(),request,w.termId(),w.memberId(),amount,source==null ? "MANUAL" : "REVERSAL",source,reason,actor,w.id());
    }
    private Warning get(UUID id) { return jdbc.query(SELECT+" WHERE w.id=?",ROW,id).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Warning/incident not found.")); }
    private void lock() { jdbc.queryForObject("SELECT id FROM organization_settings WHERE id=1 FOR UPDATE",Integer.class); }
    private UUID replay(UUID request,String payload,String actor) {
        if (request==null) throw bad("A request ID is required for safe retries.");
        return jdbc.query("SELECT * FROM warning_action WHERE request_id=?",(r,n) -> {
            if (!payload.equals(r.getString("payload")) || !actor.equals(r.getString("actor"))) throw conflict("Request ID already used for different details.");
            return r.getObject("warning_id",UUID.class);
        },request).stream().findFirst().orElse(null);
    }
    private void remember(UUID request,UUID id,String payload,String actor) { jdbc.update("INSERT INTO warning_action VALUES (?,?,?,?)",request,id,payload,actor); }
    private void audit(UUID id,String action,String reason,String actor,long version) { jdbc.update("INSERT INTO warning_history(id,warning_id,action,reason,actor,version) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),id,action,reason,actor,version); }
    private static String text(String value,int max,String label) { if(value==null || value.isBlank() || value.strip().length()>max) throw bad(label+" is required (maximum "+max+" characters)."); return value.strip(); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
