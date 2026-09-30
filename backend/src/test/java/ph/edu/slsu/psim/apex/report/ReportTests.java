package ph.edu.slsu.psim.apex.report;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import ph.edu.slsu.psim.apex.member.MemberService;
import ph.edu.slsu.psim.apex.organization.OrganizationService;
import ph.edu.slsu.psim.apex.points.PointService;
import ph.edu.slsu.psim.apex.warning.WarningService;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:reports_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ReportTests {
    @Autowired ReportService reports;
    @Autowired OrganizationService terms;
    @Autowired MemberService members;
    @Autowired PointService points;
    @Autowired WarningService warnings;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ph.edu.slsu.psim.apex.activity.ActivityService activities;
    UUID term;
    @BeforeEach void setup() {
        for(String table:List.of("warning_history","warning_action","attendance_history","activity_action","attendance","point_entry","point_request","warning_record","activity","term_history","academic_term","member_eligibility_history","member")) jdbc.update("DELETE FROM "+table);
        var today=LocalDate.now();
        term=terms.create(new OrganizationService.Details("Report term",today.minusDays(10),today.plusDays(10),null,null),"president").id();
        terms.transition(term,new OrganizationService.Change(0L),"president",true);
    }
    UUID member(String code) { return members.create(new MemberService.Details(code,"Fictional "+code,"Member",MemberService.Category.MEMBER,null,"",null,null),"president").id(); }
    PointService.Entry award(UUID id,int amount) { return points.award(new PointService.Command(UUID.randomUUID(),term,id,amount,"Fictional contribution"),"president").getFirst(); }
    @Test void tiesZeroNegativeTotalsAndExcludedMembers() {
        var a=member("A"); var b=member("B"); var zero=member("ZERO"); var negative=member("NEGATIVE"); var inactive=member("INACTIVE");
        award(a,10); award(b,10); award(negative,-2); award(inactive,99);
        jdbc.update("UPDATE member SET active=false WHERE id=?",inactive);
        members.create(new MemberService.Details("P","President","President",MemberService.Category.PRESIDENT,null,"",null,null),"president");
        members.create(new MemberService.Details("E","Executive","Executive",MemberService.Category.EXECUTIVE,null,"",null,null),"president");
        var rows=reports.leaderboard(term,"all").members();
        assertEquals(List.of(a,b,zero,negative),rows.stream().map(r -> r.memberId()).toList());
        assertEquals(List.of(1,1,3,4),rows.stream().map(r -> r.rank()).toList());
        assertEquals(List.of(10L,10L,0L,-2L),rows.stream().map(r -> r.total()).toList());
    }
    @Test void topTenIncludesBoundaryTiesAndFullListIncludesEveryone() {
        for(int i=1;i<=12;i++) { var id=member(String.format("%02d",i)); award(id,i<10 ? 30-i : i<12 ? 10 : 1); }
        var top=reports.leaderboard(term,"top10");
        assertEquals(12,top.eligibleCount()); assertEquals(11,top.members().size());
        assertEquals(10,top.members().getLast().rank()); assertEquals(12,reports.leaderboard(term,"all").members().size());
    }
    @Test void closedEligibilityAndTermIsolationSurviveLaterChanges() {
        var old=member("OLD"); var zero=member("OLDZERO"); var entry=award(old,5);
        terms.transition(term,new OrganizationService.Change(1L),"president",false);
        jdbc.update("UPDATE member SET active=false,eligible=false WHERE id=?",old);
        jdbc.update("INSERT INTO member_eligibility_history(id,member_id,eligible,reason,changed_by,effective_at,member_version) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),old,false,"Later change","president",OffsetDateTime.now().plusSeconds(1),1);
        member("NEW");
        // Make timestamps explicit rather than relying on clock resolution.
        jdbc.update("UPDATE member_eligibility_history SET effective_at=? WHERE member_id IN (?,?) AND member_version=0",OffsetDateTime.now().minusDays(1),old,zero);
        jdbc.update("UPDATE member_eligibility_history SET effective_at=? WHERE member_id NOT IN (?,?)",OffsetDateTime.now().plusSeconds(1),old,zero);
        var rows=reports.leaderboard(term,"all").members(); assertEquals(2,rows.size()); assertEquals(old,rows.getFirst().memberId());
        points.correct(entry.id(),new PointService.Correction(UUID.randomUUID(),0,"Closed correction"),"president",true);
        assertTrue(reports.leaderboard(term,"all").members().stream().allMatch(r -> r.total()==0 && r.rank()==1));
        var today=LocalDate.now(); var next=terms.create(new OrganizationService.Details("Next term",today,today.plusDays(20),null,null),"president");
        assertTrue(reports.leaderboard(next.id(),"all").members().stream().allMatch(r -> r.total()==0));
    }
    @Test void dashboardReadsLedgerReversalsAndKeepsWarningNarrativePrivate() throws Exception {
        var id=member("MEMBER"); award(id,7);
        var warning=warnings.create(new WarningService.Create(UUID.randomUUID(),term,id,"PRIVATE-INCIDENT",WarningService.Severity.MAJOR,null,"",3,"PRIVATE WARNING REASON"),"president");
        assertEquals(4,reports.dashboard(term).counts().netPoints()); assertEquals(1,reports.dashboard(term).counts().openWarnings());
        warnings.change(warning.warning().id(),new WarningService.Change(UUID.randomUUID(),0L,"PRIVATE CANCEL REASON"),"president",true,false);
        assertEquals(7,reports.dashboard(term).leaders().getFirst().total()); assertEquals(0,reports.dashboard(term).counts().openWarnings());
        var response=mvc.perform(get("/api/v1/dashboard").param("termId",term.toString()).with(user("president").roles("PRESIDENT"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("PRIVATE")); assertFalse(response.contains("severity")); assertFalse(response.contains("warningId"));
        mvc.perform(get("/api/v1/leaderboard").param("termId",term.toString())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/leaderboard").param("termId",term.toString()).param("view","invalid").with(user("president").roles("PRESIDENT"))).andExpect(status().isBadRequest());
    }
    @Test void noActiveTermReturnsEmptyTermSummary() {
        terms.transition(term,new OrganizationService.Change(1L),"president",false);
        var result=reports.dashboard(null); assertNull(result.term()); assertTrue(result.leaders().isEmpty()); assertTrue(result.changes().isEmpty());
    }
    @Test void dashboardIncludesUpcomingActivitiesAndActualAttendanceCounts() {
        var id=member("ATTENDEE");
        var now=OffsetDateTime.now(ZoneOffset.ofHours(8));
        var upcoming=activities.create(new ph.edu.slsu.psim.apex.activity.ActivityService.Details(UUID.randomUUID(),term,"Upcoming meeting",ph.edu.slsu.psim.apex.activity.ActivityService.Kind.MEETING,now.plusHours(1),"Hall","",List.of(id),null),"president");
        var past=activities.create(new ph.edu.slsu.psim.apex.activity.ActivityService.Details(UUID.randomUUID(),term,"Past event",ph.edu.slsu.psim.apex.activity.ActivityService.Kind.EVENT,now.minusHours(1),"Hall","",List.of(id),null),"president");
        var saved=activities.saveDraft(past.activity().id(),new ph.edu.slsu.psim.apex.activity.ActivityService.Draft(UUID.randomUUID(),0L,List.of(new ph.edu.slsu.psim.apex.activity.ActivityService.Mark(id,ph.edu.slsu.psim.apex.activity.ActivityService.Status.PRESENT))),"president");
        activities.finalizeAttendance(past.activity().id(),new ph.edu.slsu.psim.apex.activity.ActivityService.Change(UUID.randomUUID(),saved.activity().version(),""),"president");
        var result=reports.dashboard(term);
        assertEquals(1,result.counts().totalMembers()); assertEquals(1,result.counts().activeEligible());
        assertEquals(upcoming.activity().id(),result.attendance().upcoming().getFirst().id());
        assertEquals(1,result.attendance().recent().getFirst().present());
    }
}
