package ph.edu.slsu.psim.apex.points;

import java.time.LocalDate;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:points_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class PointTests {
    @Autowired PointService points;
    @Autowired OrganizationService terms;
    @Autowired MemberService members;
    @Autowired JdbcTemplate jdbc;
    @Autowired PresidentAccounts accounts;
    @Autowired MockMvc mvc;
    UUID term, member;
    MockHttpSession session;
    @BeforeEach void setup() throws Exception {
        jdbc.update("DELETE FROM point_entry"); jdbc.update("DELETE FROM point_request");
        jdbc.update("DELETE FROM term_history"); jdbc.update("DELETE FROM academic_term");
        jdbc.update("DELETE FROM member_eligibility_history"); jdbc.update("DELETE FROM member");
        jdbc.update("DELETE FROM president_account");
        accounts.create("president","fictional-test-password");
        session=(MockHttpSession)mvc.perform(post("/api/v1/auth/login").with(csrf()).param("username","president").param("password","fictional-test-password"))
            .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
        term=newTerm("First");
        terms.transition(term,new OrganizationService.Change(0L),"president",true);
        member=newMember("MEMBER",MemberService.Category.MEMBER);
    }
    UUID newTerm(String name) { return terms.create(new OrganizationService.Details(name,LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),null,null),"president").id(); }
    UUID newMember(String code,MemberService.Category category) { return members.create(new MemberService.Details(code,"Fictional "+code,"Position",category,null,"",null,null),"president").id(); }
    PointService.Command command(int amount) { return new PointService.Command(UUID.randomUUID(),term,member,amount,"Fictional contribution"); }
    long total(UUID t) { return points.ledger(t).totals().stream().filter(m -> m.memberId().equals(member)).findFirst().orElseThrow().total(); }
    @Test void awardsDeductionsTotalsAndTermIsolation() {
        assertEquals(0,total(term));
        points.award(command(5),"president"); points.award(command(-2),"president");
        assertEquals(3,total(term));
        terms.transition(term,new OrganizationService.Change(1L),"president",false);
        UUID next=newTerm("Second"); terms.transition(next,new OrganizationService.Change(0L),"president",true);
        assertEquals(0,total(next)); assertEquals(3,total(term));
        assertThrows(ResponseStatusException.class,() -> points.award(command(2),"president"));
    }
    @Test void correctionPreservesOriginalReversesOnceAndChains() {
        var original=points.award(command(5),"president").getFirst();
        var correction=new PointService.Correction(UUID.randomUUID(),3,"Correct amount");
        var entries=points.correct(original.id(),correction,"president",false);
        assertEquals(3,total(term)); assertEquals(-5,entries.getFirst().amount());
        assertEquals(original.id(),entries.getFirst().reversesId()); assertEquals(original.id(),entries.getLast().replacesId());
        assertEquals(original,points.ledger(term).entries().getLast());
        assertEquals(entries,points.correct(original.id(),correction,"president",false));
        assertThrows(ResponseStatusException.class,() -> points.correct(original.id(),new PointService.Correction(UUID.randomUUID(),1,"Again"),"president",false));
        points.correct(entries.getLast().id(),new PointService.Correction(UUID.randomUUID(),0,"Cancel"),"president",false);
        assertEquals(0,total(term)); assertEquals(5,points.ledger(term).entries().size());
    }
    @Test void retriesAreSafeAndPayloadCannotChange() throws Exception {
        var request=command(8);
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<List<PointService.Entry>> action=() -> { start.await(); return points.award(request,"president"); };
            var a=pool.submit(action); var b=pool.submit(action); start.countDown();
            assertEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
        }
        assertEquals(8,total(term)); assertEquals(1,points.ledger(term).entries().size());
        assertThrows(ResponseStatusException.class,() -> points.award(new PointService.Command(request.requestId(),term,member,9,request.reason()),"president"));
        terms.transition(term,new OrganizationService.Change(1L),"president",false);
        assertEquals(1,points.award(request,"president").size());
    }
    @Test void eligibilityAndValidationBlockInvalidEntriesButAllowCancellation() {
        for(var category:List.of(MemberService.Category.PRESIDENT,MemberService.Category.EXECUTIVE)) {
            var id=newMember(category.name(),category);
            assertThrows(ResponseStatusException.class,() -> points.award(new PointService.Command(UUID.randomUUID(),term,id,5,"Not allowed"),"president"));
        }
        var saved=points.award(command(2),"president").getFirst();
        members.status(member,new MemberService.Status(false,0L));
        assertThrows(ResponseStatusException.class,() -> points.award(command(-1),"president"));
        assertThrows(ResponseStatusException.class,() -> points.correct(saved.id(),new PointService.Correction(UUID.randomUUID(),4,"Cannot add"),"president",false));
        points.correct(saved.id(),new PointService.Correction(UUID.randomUUID(),0,"Cancel after deactivation"),"president",false);
        assertEquals(0,total(term));
        assertThrows(ResponseStatusException.class,() -> points.award(command(0),"president"));
        assertThrows(ResponseStatusException.class,() -> points.award(command(1001),"president"));
        assertThrows(ResponseStatusException.class,() -> points.award(new PointService.Command(UUID.randomUUID(),term,member,1," "),"president"));
        assertThrows(ResponseStatusException.class,() -> points.award(new PointService.Command(null,term,member,1,"Reason"),"president"));
    }
    @Test void closedTermNeedsExplicitCorrectionAndRemainsClosed() {
        var entry=points.award(command(-3),"president").getFirst();
        terms.transition(term,new OrganizationService.Change(1L),"president",false);
        var fix=new PointService.Correction(UUID.randomUUID(),-1,"Closed-term review");
        assertThrows(ResponseStatusException.class,() -> points.correct(entry.id(),fix,"president",false));
        assertThrows(ResponseStatusException.class,() -> points.correct(entry.id(),new PointService.Correction(UUID.randomUUID(),-1,""),"president",true));
        points.correct(entry.id(),fix,"president",true);
        assertEquals(-1,total(term)); assertEquals("CLOSED",terms.get(term).status());
    }
    @Test void simultaneousDifferentCorrectionsOnlyReverseOnce() throws Exception {
        var entry=points.award(command(5),"president").getFirst();
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> action=() -> { gate.await(); try {
                points.correct(entry.id(),new PointService.Correction(UUID.randomUUID(),3,"Concurrent correction"),"president",false);
                return true;
            } catch(ResponseStatusException e) { assertEquals(409,e.getStatusCode().value()); return false; } };
            var a=pool.submit(action); var b=pool.submit(action); gate.countDown();
            assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
        }
        assertEquals(3,total(term)); assertEquals(3,points.ledger(term).entries().size());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM point_request",Integer.class));
    }
    @Test void apiRequiresSessionCsrfAndRejectsFractionsAndMutationRoutes() throws Exception {
        String body="{\"requestId\":\""+UUID.randomUUID()+"\",\"termId\":\""+term+"\",\"memberId\":\""+member+"\",\"amount\":2,\"reason\":\"Fictional work\"}";
        mvc.perform(get("/api/v1/points").param("termId",term.toString())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/points").session(session).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/points").with(csrf()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/points").session(session).with(csrf()).contentType("application/json").content(body.replace("\"amount\":2","\"amount\":2.5"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/points").session(session).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$[0].amount").value(2));
        mvc.perform(get("/api/v1/points").session(session).param("termId",term.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.totals[0].total").value(2));
        mvc.perform(delete("/api/v1/points").session(session).with(csrf())).andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/api/v1/points").session(session).with(csrf()).contentType("application/json").content(body)).andExpect(status().isMethodNotAllowed());
    }
}
