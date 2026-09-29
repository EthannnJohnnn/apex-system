package ph.edu.slsu.psim.apex.warning;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import ph.edu.slsu.psim.apex.activity.ActivityService;
import ph.edu.slsu.psim.apex.member.MemberService;
import ph.edu.slsu.psim.apex.organization.OrganizationService;
import ph.edu.slsu.psim.apex.points.PointService;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:warnings_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class WarningTests {
    @Autowired WarningService warnings;
    @Autowired MemberService members;
    @Autowired OrganizationService terms;
    @Autowired PointService points;
    @Autowired ActivityService activities;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    UUID term,member;
    OffsetDateTime at;
    @BeforeEach void setup() {
        for (String table:List.of("warning_history","warning_action","attendance_history","activity_action","attendance","point_entry","point_request","warning_record","activity","term_history","academic_term","member_eligibility_history","member")) jdbc.update("DELETE FROM "+table);
        at=OffsetDateTime.now(ZoneOffset.ofHours(8)).minusHours(1);
        var t=terms.create(new OrganizationService.Details("Warning term",at.toLocalDate().minusDays(2),at.toLocalDate().plusDays(2),null,null),"president");
        term=t.id(); terms.transition(term,new OrganizationService.Change(0L),"president",true);
        member=members.create(new MemberService.Details("W1","Fictional member","Member",MemberService.Category.MEMBER,null,"",null,null),"president").id();
        jdbc.update("UPDATE member_eligibility_history SET effective_at=? WHERE member_id=?",at.minusDays(1),member);
    }
    WarningService.Create command(String incident,int deduction,WarningService.Severity severity) {
        return new WarningService.Create(UUID.randomUUID(),term,member,incident,severity,null,"",deduction,"Fictional incident reason");
    }
    WarningService.View create(WarningService.Create c) { return warnings.create(c,"president"); }
    WarningService.Change change(long version) { return new WarningService.Change(UUID.randomUUID(),version,"Fictional reviewed reason"); }
    long total() { return points.ledger(term).totals().stream().filter(t -> t.memberId().equals(member)).findFirst().orElseThrow().total(); }
    @Test void warningWithoutDeductionAndResolutionKeepHistory() {
        var w=create(command("INC-1",0,WarningService.Severity.MINOR));
        assertEquals(0,total()); assertTrue(points.ledger(term).entries().isEmpty());
        var done=warnings.change(w.warning().id(),change(0),"president",false,false);
        assertEquals("RESOLVED",done.warning().status()); assertEquals(2,done.history().size());
        var cancelled=warnings.change(w.warning().id(),change(1),"president",true,false);
        assertEquals("CANCELLED",cancelled.warning().status()); assertEquals(3,cancelled.history().size());
    }
    @Test void optionalDeductionResolveThenCancelReversesExactlyOnce() {
        var request=command("INC-2",4,WarningService.Severity.MAJOR);
        var w=create(request); create(request); assertEquals(-4,total());
        warnings.change(w.warning().id(),change(0),"president",false,false); assertEquals(-4,total());
        var cancel=change(1);
        warnings.change(w.warning().id(),cancel,"president",true,false);
        warnings.change(w.warning().id(),cancel,"president",true,false);
        assertEquals(0,total()); assertEquals(2,points.ledger(term).entries().size());
        assertThrows(ResponseStatusException.class,() -> warnings.change(w.warning().id(),change(2),"president",true,false));
        assertThrows(ResponseStatusException.class,() -> create(command(" inc-2 ",4,WarningService.Severity.MAJOR)));
        var source=points.ledger(term).entries().getFirst();
        assertThrows(ResponseStatusException.class,() -> points.correct(source.id(),new PointService.Correction(UUID.randomUUID(),0,"Bypass"),"president",false));
    }
    ActivityService.View absent(ActivityService.Kind kind) {
        var v=activities.create(new ActivityService.Details(UUID.randomUUID(),term,"Fictional activity",kind,at,"","",List.of(member),null),"president");
        v=activities.saveDraft(v.activity().id(),new ActivityService.Draft(UUID.randomUUID(),0L,List.of(new ActivityService.Mark(member,ActivityService.Status.ABSENT))),"president");
        return activities.finalizeAttendance(v.activity().id(),new ActivityService.Change(UUID.randomUUID(),v.activity().version(),""),"president");
    }
    WarningService.Create role(ActivityService.View v,int amount) {
        return new WarningService.Create(UUID.randomUUID(),term,member,"Assigned role incident",null,v.activity().id(),"Registration desk",amount,"Assigned but did not attend");
    }
    @Test void fixedRolePenaltiesAndAttendanceConsistency() {
        var meeting=absent(ActivityService.Kind.MEETING);
        assertThrows(ResponseStatusException.class,() -> create(role(meeting,5)));
        var w=create(role(meeting,3)); assertEquals(-3,total());
        assertThrows(ResponseStatusException.class,() -> create(role(meeting,3)));
        var correction=new ActivityService.Correction(UUID.randomUUID(),meeting.activity().version(),member,ActivityService.Status.PRESENT,"Attendance was incorrect");
        assertThrows(ResponseStatusException.class,() -> activities.correct(meeting.activity().id(),correction,"president",false));
        warnings.change(w.warning().id(),change(0),"president",true,false);
        activities.correct(meeting.activity().id(),correction,"president",false); assertEquals(2,total());
        assertThrows(ResponseStatusException.class,() -> create(role(meeting,3)));
        var event=absent(ActivityService.Kind.EVENT); create(role(event,5)); assertEquals(-3,total());
    }
    @Test void concurrentDuplicateIncidentHasOnePenalty() throws Exception {
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> work=() -> { gate.await(); try { create(command("same incident",3,WarningService.Severity.MINOR)); return true; } catch(ResponseStatusException e) { assertEquals(409,e.getStatusCode().value()); return false; } };
            var a=pool.submit(work); var b=pool.submit(work); gate.countDown();
            assertNotEquals(a.get(),b.get());
        }
        assertEquals(-3,total()); assertEquals(1,warnings.list(term).size());
    }
    @Test void concurrentCancellationRetriesReverseOnce() throws Exception {
        var w=create(command("concurrent cancel",5,WarningService.Severity.MAJOR));
        var request=change(0);
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<WarningService.View> work=() -> { gate.await(); return warnings.change(w.warning().id(),request,"president",true,false); };
            var a=pool.submit(work); var b=pool.submit(work); gate.countDown();
            assertEquals(a.get(),b.get());
        }
        assertEquals(0,total()); assertEquals(2,points.ledger(term).entries().size()); assertEquals(2,warnings.view(w.warning().id()).history().size());
    }
    @Test void validationAndClosedTermCancellation() {
        assertThrows(ResponseStatusException.class,() -> create(command("",3,WarningService.Severity.MINOR)));
        assertThrows(ResponseStatusException.class,() -> create(command("empty",0,null)));
        jdbc.update("UPDATE member SET eligible=false WHERE id=?",member);
        assertThrows(ResponseStatusException.class,() -> create(command("ineligible",3,WarningService.Severity.MINOR)));
        create(command("warning only",0,WarningService.Severity.MINOR));
        jdbc.update("UPDATE member SET eligible=true WHERE id=?",member);
        var w=create(command("closed test",2,WarningService.Severity.MAJOR));
        var t=terms.get(term); terms.transition(term,new OrganizationService.Change(t.version()),"president",false);
        assertThrows(ResponseStatusException.class,() -> warnings.change(w.warning().id(),change(0),"president",true,false));
        assertThrows(ResponseStatusException.class,() -> create(command("closed new",2,WarningService.Severity.MINOR)));
        jdbc.update("UPDATE member SET active=false,eligible=false WHERE id=?",member);
        warnings.change(w.warning().id(),change(0),"president",true,true); assertEquals(0,total());
    }
    @Test void requestConflictStaleVersionAndFailedWritesLeaveNoPartialHistory() {
        var request=command("retry",2,WarningService.Severity.MINOR);
        var w=create(request);
        assertThrows(ResponseStatusException.class,() -> create(new WarningService.Create(request.requestId(),term,member,"different",WarningService.Severity.MAJOR,null,"",2,"Reason")));
        assertThrows(ResponseStatusException.class,() -> warnings.change(w.warning().id(),change(99),"president",true,false));
        assertThrows(ResponseStatusException.class,() -> warnings.change(w.warning().id(),new WarningService.Change(UUID.randomUUID(),0L,""),"president",true,false));
        assertEquals(1,warnings.view(w.warning().id()).history().size()); assertEquals(-2,total());
    }
    @Test void historicalEligibilityControlsRoleDeductions() {
        jdbc.update("UPDATE member_eligibility_history SET eligible=false WHERE member_id=?",member);
        var event=absent(ActivityService.Kind.EVENT);
        assertThrows(ResponseStatusException.class,() -> create(role(event,5)));
        assertEquals(0,total()); assertTrue(warnings.list(term).isEmpty());
        jdbc.update("UPDATE member SET category='PRESIDENT',eligible=false WHERE id=?",member);
        assertThrows(ResponseStatusException.class,() -> create(command("president",1,WarningService.Severity.MINOR)));
    }
    @Test void endpointsRequireLoginCsrfAndWholeNumbers() throws Exception {
        var get=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/warnings?termId="+term);
        mvc.perform(get).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        String body="{\"requestId\":\""+UUID.randomUUID()+"\",\"termId\":\""+term+"\",\"memberId\":\""+member+"\",\"incident\":\"HTTP test\",\"severity\":\"MINOR\",\"deduction\":1.5,\"reason\":\"Fictional test\"}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/warnings").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("president").roles("PRESIDENT")).contentType("application/json").content(body))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/warnings").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("president").roles("PRESIDENT")).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(body))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/warnings").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(body.replace("1.5","2")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/warnings").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("president").roles("PRESIDENT")).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(body.replace("1.5","2")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertEquals(-2,total());
    }
}
