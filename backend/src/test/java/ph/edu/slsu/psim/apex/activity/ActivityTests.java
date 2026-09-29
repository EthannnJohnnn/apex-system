package ph.edu.slsu.psim.apex.activity;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import ph.edu.slsu.psim.apex.auth.PresidentAccounts;
import ph.edu.slsu.psim.apex.member.MemberService;
import ph.edu.slsu.psim.apex.organization.OrganizationService;
import ph.edu.slsu.psim.apex.points.PointService;
import static ph.edu.slsu.psim.apex.activity.ActivityService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:activities_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ActivityTests {
    @Autowired ActivityService activities;
    @Autowired OrganizationService terms;
    @Autowired MemberService members;
    @Autowired PointService points;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PresidentAccounts accounts;
    UUID term,member;
    OffsetDateTime at;
    MockHttpSession session;
    @BeforeEach void setup() throws Exception {
        for(String table:List.of("attendance_history","activity_action","attendance","point_entry","point_request","activity","term_history","academic_term","member_eligibility_history","member","president_account")) jdbc.update("DELETE FROM "+table);
        jdbc.update("UPDATE organization_settings SET meeting_present=2,meeting_late=1,event_present=3,event_late=2");
        at=OffsetDateTime.now(ZoneOffset.ofHours(8)).minusHours(1);
        var t=terms.create(new OrganizationService.Details("Test term",at.toLocalDate().minusDays(10),at.toLocalDate().plusDays(10),null,null),"president");
        term=t.id(); terms.transition(term,new OrganizationService.Change(0L),"president",true);
        member=newMember("M1",MemberService.Category.MEMBER);
        accounts.create("president","fictional-activity-password");
        session=(MockHttpSession)mvc.perform(post("/api/v1/auth/login").with(csrf()).param("username","president").param("password","fictional-activity-password"))
            .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }
    UUID newMember(String code,MemberService.Category category) {
        UUID id=members.create(new MemberService.Details(code,"Fictional "+code,"Position",category,null,"",null,null),"president").id();
        jdbc.update("UPDATE member_eligibility_history SET effective_at=? WHERE member_id=?",at.minusDays(1),id);
        return id;
    }
    Details details(Kind kind,List<UUID> roster) { return new Details(UUID.randomUUID(),term,"Fictional "+kind,kind,at,"Room 1","Description",roster,null); }
    View create(Kind kind) { return activities.create(details(kind,List.of(member)),"president"); }
    View mark(View v,Status status) { return activities.saveDraft(v.activity().id(),new Draft(UUID.randomUUID(),v.activity().version(),v.attendees().stream().map(m -> new Mark(m.memberId(),status)).toList()),"president"); }
    View finish(View v) { return activities.finalizeAttendance(v.activity().id(),new Change(UUID.randomUUID(),v.activity().version(),""),"president"); }
    long total() { return points.ledger(term).totals().stream().filter(t -> t.memberId().equals(member)).findFirst().orElseThrow().total(); }
    @Test void savedDefaultsBulkDraftAndFinalizationBothTypes() {
        UUID other=newMember("M2",MemberService.Category.MEMBER);
        var meeting=activities.create(details(Kind.MEETING,List.of(member,other)),"president");
        assertEquals(2,meeting.activity().presentPoints()); assertEquals(1,meeting.activity().latePoints());
        jdbc.update("UPDATE organization_settings SET meeting_present=7,meeting_late=4");
        assertThrows(ResponseStatusException.class,() -> finish(meeting));
        var marked=activities.saveDraft(meeting.activity().id(),new Draft(UUID.randomUUID(),0L,List.of(new Mark(member,Status.PRESENT),new Mark(other,Status.LATE))),"president");
        assertEquals(0,total());
        var done=finish(marked);
        assertEquals("FINALIZED",done.activity().status()); assertEquals(2,total());
        assertEquals(3,done.attendees().stream().mapToInt(Attendee::points).sum());
        assertEquals(2,points.ledger(term).entries().size());
        var event=finish(mark(create(Kind.EVENT),Status.LATE));
        assertEquals(3,event.activity().presentPoints()); assertEquals(2,event.attendees().getFirst().points());
        assertEquals(4,total());
        assertEquals(2,activities.summary(term).recent().size());
        assertEquals(1,activities.summary(term).recent().stream().filter(t -> t.activity().id().equals(done.activity().id())).findFirst().orElseThrow().late());
    }
    @Test void createSaveAndFinalizeRetriesAndConflicts() throws Exception {
        var input=details(Kind.MEETING,List.of(member)); var first=activities.create(input,"president");
        assertEquals(first,activities.create(input,"president"));
        assertThrows(ResponseStatusException.class,() -> activities.create(new Details(input.requestId(),term,"Different",input.kind(),at,"","",input.memberIds(),null),"president"));
        var draft=new Draft(UUID.randomUUID(),0L,List.of(new Mark(member,Status.PRESENT)));
        var marked=activities.saveDraft(first.activity().id(),draft,"president");
        assertEquals(marked,activities.saveDraft(first.activity().id(),draft,"president"));
        var request=new Change(UUID.randomUUID(),1L,"");
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<View> call=() -> { gate.await(); return activities.finalizeAttendance(first.activity().id(),request,"president"); };
            var a=pool.submit(call); var b=pool.submit(call); gate.countDown();
            assertEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
        }
        assertEquals(2,total()); assertEquals(1,points.ledger(term).entries().size());
        assertThrows(ResponseStatusException.class,() -> finish(marked));
        terms.transition(term,new OrganizationService.Change(1L),"president",false);
        assertEquals("FINALIZED",activities.finalizeAttendance(first.activity().id(),request,"president").activity().status());
    }
    @Test void historicalEligibilityFrozenAndPresidentNeverScores() {
        UUID executive=newMember("EXEC",MemberService.Category.EXECUTIVE);
        UUID president=newMember("PRES",MemberService.Category.PRESIDENT);
        var v=activities.create(details(Kind.EVENT,List.of(member,executive,president)),"president");
        members.eligibility(executive,new MemberService.Eligibility(true,"Eligible from now",0L),"president");
        members.eligibility(member,new MemberService.Eligibility(false,"Ineligible from now",0L),"president");
        var done=finish(mark(v,Status.PRESENT));
        assertEquals(3,total());
        assertEquals(0,done.attendees().stream().filter(m -> m.memberId().equals(executive)).findFirst().orElseThrow().points());
        assertEquals(0,done.attendees().stream().filter(m -> m.memberId().equals(president)).findFirst().orElseThrow().points());
        var fix=activities.correct(v.activity().id(),new Correction(UUID.randomUUID(),done.activity().version(),member,Status.LATE,"Actually late"),"president",false);
        assertEquals(2,total()); assertTrue(fix.attendees().stream().filter(m -> m.memberId().equals(member)).findFirst().orElseThrow().eligibleSnapshot());
    }
    @Test void zeroStatusesAndCorrectionChainsMatchLedgerWithoutIndependentEdits() {
        var done=finish(mark(create(Kind.EVENT),Status.ABSENT));
        assertEquals(0,total()); assertTrue(points.ledger(term).entries().isEmpty());
        var change=new Correction(UUID.randomUUID(),done.activity().version(),member,Status.PRESENT,"Attendance sheet corrected");
        var first=activities.correct(done.activity().id(),change,"president",false);
        assertEquals(first,activities.correct(done.activity().id(),change,"president",false)); assertEquals(3,total());
        var entry=points.ledger(term).entries().getFirst(); assertEquals(done.activity().id(),entry.activityId());
        assertThrows(ResponseStatusException.class,() -> points.correct(entry.id(),new PointService.Correction(UUID.randomUUID(),8,"Wrong route"),"president",false));
        var excused=activities.correct(done.activity().id(),new Correction(UUID.randomUUID(),first.activity().version(),member,Status.EXCUSED,"Excused instead"),"president",false);
        assertEquals(0,total()); assertEquals(3,points.ledger(term).entries().size());
        activities.correct(done.activity().id(),new Correction(UUID.randomUUID(),excused.activity().version(),member,Status.LATE,"Actually arrived late"),"president",false);
        assertEquals(2,total()); assertEquals(5,points.ledger(term).entries().size());
        assertEquals(entry,points.ledger(term).entries().getLast());
    }
    @Test void termClosureRequiresDraftCompletionOrCancellationAndExplicitClosedCorrection() {
        var draft=create(Kind.MEETING);
        assertThrows(ResponseStatusException.class,() -> terms.transition(term,new OrganizationService.Change(1L),"president",false));
        assertThrows(ResponseStatusException.class,() -> activities.cancel(draft.activity().id(),new Change(UUID.randomUUID(),0L,""),"president"));
        var cancellation=new Change(UUID.randomUUID(),0L,"Meeting cancelled");
        var cancelled=activities.cancel(draft.activity().id(),cancellation,"president");
        assertEquals(cancelled,activities.cancel(draft.activity().id(),cancellation,"president"));
        assertThrows(ResponseStatusException.class,() -> mark(cancelled,Status.PRESENT));
        var done=finish(mark(create(Kind.EVENT),Status.PRESENT));
        terms.transition(term,new OrganizationService.Change(1L),"president",false);
        var correction=new Correction(UUID.randomUUID(),done.activity().version(),member,Status.LATE,"Closed-term review");
        assertThrows(ResponseStatusException.class,() -> activities.correct(done.activity().id(),correction,"president",false));
        activities.correct(done.activity().id(),correction,"president",true);
        assertEquals(2,total()); assertEquals("CLOSED",terms.get(term).status());
        assertThrows(ResponseStatusException.class,() -> create(Kind.MEETING));
    }
    @Test void draftEditsKeepPointsRequireCurrentVersionAndInvalidBatchRollsBack() {
        var v=create(Kind.MEETING);
        var details=new Details(UUID.randomUUID(),term,"Renamed",Kind.MEETING,at.minusMinutes(10),"Hall","New text",List.of(member),0L);
        var edited=activities.edit(v.activity().id(),details,"president");
        assertEquals("Renamed",edited.activity().title()); assertEquals("Hall",edited.activity().location());
        assertThrows(ResponseStatusException.class,() -> activities.saveDraft(v.activity().id(),new Draft(UUID.randomUUID(),0L,List.of(new Mark(member,Status.PRESENT))),"president"));
        assertThrows(ResponseStatusException.class,() -> activities.saveDraft(v.activity().id(),new Draft(UUID.randomUUID(),1L,List.of(new Mark(member,Status.PRESENT),new Mark(UUID.randomUUID(),Status.LATE))),"president"));
        assertNull(activities.view(v.activity().id()).attendees().getFirst().status());
        assertEquals(1,activities.view(v.activity().id()).activity().version());
        assertEquals(2,edited.activity().presentPoints());
    }
    @Test void futureAndInvalidSchedulesRosterAndReasonsRejected() {
        assertThrows(ResponseStatusException.class,() -> activities.create(details(Kind.MEETING,List.of()),"president"));
        assertThrows(ResponseStatusException.class,() -> activities.create(details(Kind.MEETING,List.of(member,member)),"president"));
        var future=activities.create(new Details(UUID.randomUUID(),term,"Future",Kind.MEETING,at.plusDays(1),"","",List.of(member),null),"president");
        var marked=mark(future,Status.PRESENT);
        assertThrows(ResponseStatusException.class,() -> finish(marked));
        assertEquals(1,activities.summary(term).upcoming().size());
        assertThrows(ResponseStatusException.class,() -> activities.create(new Details(UUID.randomUUID(),term,"Outside term",Kind.EVENT,at.plusYears(1),"","",List.of(member),null),"president"));
        members.status(member,new MemberService.Status(false,0L));
        assertThrows(ResponseStatusException.class,() -> create(Kind.MEETING));
    }
    @Test void newMemberCannotEarnForAnActivityBeforeTheirEligibilityHistory() {
        jdbc.update("UPDATE member_eligibility_history SET effective_at=? WHERE member_id=?",at.plusMinutes(30),member);
        var done=finish(mark(create(Kind.MEETING),Status.LATE));
        assertEquals(Status.LATE,done.attendees().getFirst().status()); // No automatic absence, even an hour later.
        assertEquals(0,total()); assertFalse(done.attendees().getFirst().eligibleSnapshot());
    }
    @Test void concurrentCorrectionsUseVersionSoOnlyOneSucceeds() throws Exception {
        var done=finish(mark(create(Kind.MEETING),Status.PRESENT));
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task=() -> { gate.await(); try {
                activities.correct(done.activity().id(),new Correction(UUID.randomUUID(),done.activity().version(),member,Status.LATE,"Concurrent fix"),"president",false); return true;
            } catch(ResponseStatusException e) { assertEquals(409,e.getStatusCode().value()); return false; } };
            var a=pool.submit(task); var b=pool.submit(task); gate.countDown();
            assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
        }
        assertEquals(1,total()); assertEquals(3,points.ledger(term).entries().size());
    }
    @Test void apiRequiresLoginCsrfAndExposesSavedActivityAndSummary() throws Exception {
        var v=create(Kind.MEETING);
        String url="/api/v1/activities/"+v.activity().id();
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(get(url).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.activity.presentPoints").value(2));
        String body="{\"requestId\":\""+UUID.randomUUID()+"\",\"version\":0,\"marks\":[{\"memberId\":\""+member+"\",\"status\":\"PRESENT\"}]}";
        mvc.perform(put(url+"/attendance").session(session).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(put(url+"/attendance").with(csrf()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(put(url+"/attendance").session(session).with(csrf()).contentType("application/json").content(body.replace("PRESENT","INVALID"))).andExpect(status().isBadRequest());
        mvc.perform(put(url+"/attendance").session(session).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.attendees[0].status").value("PRESENT"));
        mvc.perform(get("/api/v1/activities/summary").param("termId",term.toString()).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.recent.length()").value(0));
        mvc.perform(delete(url).session(session).with(csrf())).andExpect(status().isMethodNotAllowed());
    }
}
