package ph.edu.slsu.psim.apex.activity;

import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ph.edu.slsu.psim.apex.organization.OrganizationService;

@Service
public class ActivityService {
    public enum Kind { MEETING, EVENT }
    public enum Status { PRESENT, LATE, EXCUSED, ABSENT }
    public record Details(UUID requestId, UUID termId, String title, Kind kind, OffsetDateTime scheduledAt,
                          String location, String description, List<UUID> memberIds, Long version) {}
    public record Mark(UUID memberId, Status status) {}
    public record Draft(UUID requestId, Long version, List<Mark> marks) {}
    public record Change(UUID requestId, Long version, String reason) {}
    public record Correction(UUID requestId, Long version, UUID memberId, Status status, String reason) {}
    public record Activity(UUID id, UUID termId, String title, Kind kind, OffsetDateTime scheduledAt,
                           String location, String description, int presentPoints, int latePoints, String status, long version) {}
    public record Attendee(UUID memberId, String name, String memberCode, Status status, Boolean eligibleSnapshot, int points, UUID entryId) {}
    public record History(UUID id, UUID memberId, String action, String oldStatus, String newStatus, Integer oldPoints,
                          Integer newPoints, String reason, String actor, OffsetDateTime recordedAt, long activityVersion) {}
    public record View(Activity activity, String termStatus, List<Attendee> attendees, List<History> history) {}
    public record Trend(Activity activity, long present, long late, long excused, long absent) {}
    public record Summary(List<Trend> recent, List<Activity> upcoming) {}
    private final JdbcTemplate jdbc;
    private final OrganizationService terms;
    private static final ZoneId ZONE = ZoneId.of("Asia/Manila");
    private static final RowMapper<Activity> ACTIVITY = (r,n) -> new Activity(r.getObject("id",UUID.class),r.getObject("term_id",UUID.class),
        r.getString("title"),Kind.valueOf(r.getString("kind")),r.getObject("scheduled_at",OffsetDateTime.class),r.getString("location"),
        r.getString("description"),r.getInt("present_points"),r.getInt("late_points"),r.getString("status"),r.getLong("version"));
    public ActivityService(JdbcTemplate jdbc, OrganizationService terms) { this.jdbc=jdbc; this.terms=terms; }

    public List<Activity> list(UUID termId) {
        terms.get(termId);
        return jdbc.query("SELECT * FROM activity WHERE term_id=? ORDER BY scheduled_at DESC,title",ACTIVITY,termId);
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Summary summary(UUID termId) {
        var rows=list(termId);
        var recent=rows.stream().filter(a -> a.status().equals("FINALIZED")).limit(6).map(a -> {
            var people=attendees(a.id());
            return new Trend(a,people.stream().filter(m -> m.status()==Status.PRESENT).count(),people.stream().filter(m -> m.status()==Status.LATE).count(),
                people.stream().filter(m -> m.status()==Status.EXCUSED).count(),people.stream().filter(m -> m.status()==Status.ABSENT).count());
        }).toList();
        var upcoming=rows.stream().filter(a -> a.status().equals("DRAFT") && a.scheduledAt().isAfter(OffsetDateTime.now()))
            .sorted(Comparator.comparing(Activity::scheduledAt)).limit(3).toList();
        return new Summary(recent,upcoming);
    }
    private Activity get(UUID id) {
        return jdbc.query("SELECT * FROM activity WHERE id=?",ACTIVITY,id).stream().findFirst().orElseThrow(() -> missing("Activity not found."));
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public View view(UUID id) {
        Activity a=get(id);
        return new View(a,terms.get(a.termId()).status(),attendees(id),jdbc.query("SELECT * FROM attendance_history WHERE activity_id=? ORDER BY activity_version DESC,recorded_at DESC,member_id",(r,n) ->
            new History(r.getObject("id",UUID.class),r.getObject("member_id",UUID.class),r.getString("action"),r.getString("old_status"),r.getString("new_status"),
                r.getObject("old_points",Integer.class),r.getObject("new_points",Integer.class),r.getString("reason"),r.getString("actor"),r.getObject("recorded_at",OffsetDateTime.class),r.getLong("activity_version")),id));
    }
    private List<Attendee> attendees(UUID id) {
        return jdbc.query("SELECT a.*,m.name,m.member_code FROM attendance a JOIN member m ON m.id=a.member_id WHERE a.activity_id=? ORDER BY LOWER(m.name),m.member_code",(r,n) ->
            new Attendee(r.getObject("member_id",UUID.class),r.getString("name"),r.getString("member_code"),r.getString("status")==null ? null : Status.valueOf(r.getString("status")),
                r.getObject("eligible_snapshot",Boolean.class),r.getInt("points"),r.getObject("entry_id",UUID.class)),id);
    }
    @Transactional
    public View create(Details input, String actor) {
        lock();
        UUID id=requiredId(input.requestId());
        if (replay(id,id,"CREATE:"+input,actor)) return view(id);
        validate(input);
        var term=terms.requireActiveForWork(input.termId());
        schedule(term,input.scheduledAt());
        var settings=terms.settings();
        jdbc.update("INSERT INTO activity (id,term_id,title,kind,scheduled_at,location,description,present_points,late_points) VALUES (?,?,?,?,?,?,?,?,?)",
            id,input.termId(),input.title().strip(),input.kind().name(),input.scheduledAt(),optional(input.location(),200),optional(input.description(),2000),
            input.kind()==Kind.MEETING ? settings.meetingPresent() : settings.eventPresent(),input.kind()==Kind.MEETING ? settings.meetingLate() : settings.eventLate());
        roster(id,input.memberIds());
        remember(id,id,"CREATE:"+input,actor);
        audit(get(id),null,"CREATED",null,null,null,null,"Activity created; point values saved from settings.",actor);
        return view(id);
    }
    @Transactional
    public View edit(UUID id, Details input, String actor) {
        lock();
        if (replay(input.requestId(),id,"EDIT:"+input,actor)) return view(id);
        validate(input);
        Activity a=draft(id,input.version());
        if (!a.termId().equals(input.termId()) || a.kind()!=input.kind()) throw bad("Term and activity type cannot change. Cancel and create a new draft instead.");
        schedule(terms.get(a.termId()),input.scheduledAt());
        roster(id,input.memberIds());
        jdbc.update("UPDATE activity SET title=?,scheduled_at=?,location=?,description=?,version=version+1 WHERE id=?",input.title().strip(),input.scheduledAt(),optional(input.location(),200),optional(input.description(),2000),id);
        remember(input.requestId(),id,"EDIT:"+input,actor);
        audit(get(id),null,"EDITED",null,null,null,null,"Draft details or expected attendees updated; saved point values retained.",actor);
        return view(id);
    }
    @Transactional
    public View saveDraft(UUID id, Draft input, String actor) {
        lock();
        if (replay(input.requestId(),id,"DRAFT:"+input,actor)) return view(id);
        Activity a=draft(id,input.version());
        if (input.marks()==null || input.marks().isEmpty()) throw bad("Choose attendance to save.");
        Set<UUID> seen=new HashSet<>();
        var roster=attendees(id);
        for (Mark mark:input.marks()) {
            if (mark==null || mark.memberId()==null || !seen.add(mark.memberId())) throw bad("Each attendee may appear only once.");
            var old=roster.stream().filter(m -> m.memberId().equals(mark.memberId())).findFirst().orElseThrow(() -> bad("Member is not an expected attendee."));
            jdbc.update("UPDATE attendance SET status=? WHERE activity_id=? AND member_id=?",name(mark.status()),id,mark.memberId());
            if (old.status()!=mark.status()) auditNext(a,mark.memberId(),"DRAFT_SAVED",name(old.status()),name(mark.status()),0,0,"Attendance draft saved.",actor);
        }
        bump(id); remember(input.requestId(),id,"DRAFT:"+input,actor);
        return view(id);
    }
    @Transactional
    public View finalizeAttendance(UUID id, Change input, String actor) {
        lock();
        if (replay(input.requestId(),id,"FINALIZE:"+input,actor)) return view(id);
        Activity a=draft(id,input.version());
        if (a.scheduledAt().isAfter(OffsetDateTime.now())) throw bad("Attendance cannot be finalized before the activity starts.");
        schedule(terms.get(a.termId()),a.scheduledAt());
        var roster=attendees(id);
        if (roster.isEmpty() || roster.stream().anyMatch(m -> m.status()==null)) throw bad("Give every expected attendee a status before finalizing.");
        for (var member:roster) {
            boolean eligible=eligibleAt(member.memberId(),a.scheduledAt());
            int amount=score(a,member.status(),eligible);
            UUID entry=ledger(a,member,amount,"Attendance finalized: "+a.title(),actor);
            jdbc.update("UPDATE attendance SET eligible_snapshot=?,points=?,entry_id=? WHERE activity_id=? AND member_id=?",eligible,amount,entry,id,member.memberId());
            auditNext(a,member.memberId(),"FINALIZED",null,name(member.status()),0,amount,"Attendance finalized.",actor);
        }
        jdbc.update("UPDATE activity SET status='FINALIZED',version=version+1 WHERE id=?",id);
        remember(input.requestId(),id,"FINALIZE:"+input,actor);
        return view(id);
    }
    @Transactional
    public View cancel(UUID id, Change input, String actor) {
        lock();
        if (replay(input.requestId(),id,"CANCEL:"+input,actor)) return view(id);
        Activity a=draft(id,input.version());
        String reason=text(input.reason(),500,"Cancellation reason");
        jdbc.update("UPDATE activity SET status='CANCELLED',version=version+1 WHERE id=?",id);
        auditNext(a,null,"CANCELLED",null,null,null,null,reason,actor);
        remember(input.requestId(),id,"CANCEL:"+input,actor);
        return view(id);
    }
    @Transactional
    public View correct(UUID id, Correction input, String actor, boolean closed) {
        lock();
        String payload=(closed ? "CLOSED_CORRECTION:" : "CORRECTION:")+input;
        if (replay(input.requestId(),id,payload,actor)) return view(id);
        Activity a=get(id); version(a,input.version());
        if (!a.status().equals("FINALIZED")) throw conflict("Only finalized attendance can be corrected.");
        if (closed) {
            if (!terms.get(a.termId()).status().equals("CLOSED")) throw conflict("Use the active-term correction action.");
        } else terms.requireActiveForWork(a.termId());
        String reason=text(input.reason(),500,"Correction reason");
        if (input.status()==null) throw bad("Choose a status.");
        var member=attendees(id).stream().filter(m -> m.memberId().equals(input.memberId())).findFirst().orElseThrow(() -> bad("Member is not an expected attendee."));
        if (member.status()==input.status()) throw bad("Choose a different attendance status.");
          if (jdbc.queryForObject("SELECT COUNT(*) FROM warning_record WHERE activity_id=? AND member_id=? AND status<>'CANCELLED'",Integer.class,id,input.memberId())>0)
              throw conflict("Cancel the linked role no-show record in Warnings before correcting this attendance.");
        int amount=score(a,input.status(),Boolean.TRUE.equals(member.eligibleSnapshot()));
        UUID entry=ledger(a,member,amount,reason,actor);
        jdbc.update("UPDATE attendance SET status=?,points=?,entry_id=? WHERE activity_id=? AND member_id=?",input.status().name(),amount,entry,id,member.memberId());
        auditNext(a,member.memberId(),closed ? "CLOSED_CORRECTION" : "CORRECTION",name(member.status()),name(input.status()),member.points(),amount,reason,actor);
        bump(id); remember(input.requestId(),id,payload,actor);
        return view(id);
    }
    // Same singleton lock/order as ledger writes and term closure. The entire workflow is one transaction.
    private void lock() { jdbc.queryForObject("SELECT id FROM organization_settings WHERE id=1 FOR UPDATE",Integer.class); }
    private Activity draft(UUID id, Long version) {
        Activity a=get(id); terms.requireActiveForWork(a.termId()); version(a,version);
        if (!a.status().equals("DRAFT")) throw conflict("This activity is no longer a draft. Reload it.");
        return a;
    }
    private void roster(UUID id,List<UUID> ids) {
        Set<UUID> existing=new HashSet<>(jdbc.queryForList("SELECT member_id FROM attendance WHERE activity_id=?",UUID.class,id));
        for (UUID member:ids) if (!existing.contains(member)) {
            var active=jdbc.query("SELECT active FROM member WHERE id=? FOR UPDATE",(r,n) -> r.getBoolean(1),member);
            if (active.isEmpty() || !active.getFirst()) throw bad("Only existing active members can be added to the roster.");
            jdbc.update("INSERT INTO attendance (activity_id,member_id) VALUES (?,?)",id,member);
        }
        for (UUID member:existing) if (!ids.contains(member)) jdbc.update("DELETE FROM attendance WHERE activity_id=? AND member_id=?",id,member);
    }
    private boolean eligibleAt(UUID member,OffsetDateTime time) {
        // Serialize against a member edit before reading committed eligibility history.
        jdbc.queryForObject("SELECT id FROM member WHERE id=? FOR UPDATE",UUID.class,member);
        var values=jdbc.query("SELECT eligible FROM member_eligibility_history WHERE member_id=? AND effective_at<=? ORDER BY effective_at DESC,member_version DESC",(r,n) -> r.getBoolean(1),member,time);
        return !values.isEmpty() && values.getFirst();
    }
    private static int score(Activity a,Status status,boolean eligible) {
        if (!eligible) return 0;
        return switch(status) { case PRESENT -> a.presentPoints(); case LATE -> a.latePoints(); default -> 0; };
    }
    private UUID ledger(Activity a,Attendee member,int amount,String reason,String actor) {
        if (member.entryId()==null && amount==0) return null;
        UUID request=UUID.randomUUID();
        jdbc.update("INSERT INTO point_request (id,operation,term_id,member_id,source_id,amount,reason,actor) VALUES (?,?,?,?,?,?,?,?)",request,"ATTENDANCE",a.termId(),member.memberId(),a.id(),amount,reason,actor);
        if (member.entryId()!=null) insertEntry(a,member,request,-member.points(),"REVERSAL",member.entryId(),null,reason,actor);
        return insertEntry(a,member,request,amount,member.entryId()==null ? "MANUAL" : "REPLACEMENT",null,member.entryId(),reason,actor);
    }
    private UUID insertEntry(Activity a,Attendee m,UUID request,int amount,String kind,UUID reverses,UUID replaces,String reason,String actor) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO point_entry (id,request_id,term_id,member_id,amount,kind,reverses_id,replaces_id,reason,actor,activity_id) VALUES (?,?,?,?,?,?,?,?,?,?,?)",id,request,a.termId(),m.memberId(),amount,kind,reverses,replaces,reason,actor,a.id());
        return id;
    }
    private boolean replay(UUID request,UUID activity,String payload,String actor) {
        requiredId(request);
        var rows=jdbc.query("SELECT * FROM activity_action WHERE request_id=?",(r,n) -> r.getObject("activity_id",UUID.class).equals(activity) && r.getString("payload").equals(payload) && r.getString("actor").equals(actor),request);
        if (rows.isEmpty()) return false;
        if (!rows.getFirst()) throw conflict("Request ID already used for different details. Reload before starting another action.");
        return true;
    }
    private void remember(UUID request,UUID activity,String payload,String actor) { jdbc.update("INSERT INTO activity_action (request_id,activity_id,payload,actor) VALUES (?,?,?,?)",request,activity,payload,actor); }
    private void bump(UUID id) { jdbc.update("UPDATE activity SET version=version+1 WHERE id=?",id); }
    private void auditNext(Activity a,UUID member,String action,String oldStatus,String newStatus,Integer oldPoints,Integer newPoints,String reason,String actor) {
        audit(new Activity(a.id(),a.termId(),a.title(),a.kind(),a.scheduledAt(),a.location(),a.description(),a.presentPoints(),a.latePoints(),a.status(),a.version()+1),member,action,oldStatus,newStatus,oldPoints,newPoints,reason,actor);
    }
    private void audit(Activity a,UUID member,String action,String oldStatus,String newStatus,Integer oldPoints,Integer newPoints,String reason,String actor) {
        jdbc.update("INSERT INTO attendance_history (id,activity_id,member_id,action,old_status,new_status,old_points,new_points,reason,actor,activity_version) VALUES (?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),a.id(),member,action,oldStatus,newStatus,oldPoints,newPoints,reason,actor,a.version());
    }
    private static void validate(Details d) {
        text(d.title(),150,"Title"); optional(d.location(),200); optional(d.description(),2000);
        if (d.termId()==null || d.kind()==null || d.scheduledAt()==null) throw bad("Choose a term, type and schedule.");
        if (d.memberIds()==null || d.memberIds().isEmpty() || d.memberIds().stream().anyMatch(Objects::isNull) || new HashSet<>(d.memberIds()).size()!=d.memberIds().size()) throw bad("Choose unique expected attendees (at least one).");
    }
    private static void schedule(OrganizationService.Term term,OffsetDateTime at) {
        LocalDate date=at.atZoneSameInstant(ZONE).toLocalDate();
        if (date.isBefore(term.startDate()) || date.isAfter(term.endDate())) throw bad("The activity must fall within the term dates (Asia/Manila).");
    }
    private static void version(Activity a,Long v) { if (v==null || v!=a.version()) throw conflict("This activity changed. Reload it before trying again."); }
    private static UUID requiredId(UUID id) { if (id==null) throw bad("A request ID is required for safe retries."); return id; }
    private static String name(Status status) { return status==null ? null : status.name(); }
    private static String optional(String text,int max) { String value=text==null ? "" : text.strip(); if (value.length()>max) throw bad("Text is too long (maximum "+max+" characters)."); return value; }
    private static String text(String value,int max,String field) { if (value==null || value.isBlank()) throw bad(field+" is required."); return optional(value,max); }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,m); }
    private static ResponseStatusException conflict(String m) { return new ResponseStatusException(HttpStatus.CONFLICT,m); }
    private static ResponseStatusException missing(String m) { return new ResponseStatusException(HttpStatus.NOT_FOUND,m); }
}
