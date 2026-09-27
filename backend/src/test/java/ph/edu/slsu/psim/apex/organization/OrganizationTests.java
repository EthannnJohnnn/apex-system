package ph.edu.slsu.psim.apex.organization;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import ph.edu.slsu.psim.apex.auth.PresidentAccounts;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrganizationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PresidentAccounts accounts;
    @Autowired OrganizationService service;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactions;
    @MockitoBean TermClosureCheck drafts;
    MockHttpSession session;
    @BeforeEach void setup() throws Exception {
        jdbc.update("DELETE FROM term_history");
        jdbc.update("DELETE FROM academic_term");
        jdbc.update("UPDATE organization_settings SET organization_name='PSIM - SLSU SU',meeting_present=2,meeting_late=1,event_present=3,event_late=2,version=0");
        jdbc.update("DELETE FROM president_account");
        accounts.create("president", "fictional-test-password");
        session = (MockHttpSession) mvc.perform(post("/api/v1/auth/login").with(csrf())
            .param("username", "president").param("password", "fictional-test-password"))
            .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }
    OrganizationService.Term create(String name) {
        return service.create(new OrganizationService.Details(name, LocalDate.of(2026,8,1), LocalDate.of(2026,12,31), null, null), "president");
    }
    String details(String name, long version, String reason) {
        return "{\"name\":\""+name+"\",\"startDate\":\"2026-08-01\",\"endDate\":\"2026-12-31\",\"version\":"+version+",\"reason\":\""+reason+"\"}";
    }
    @Test void settingsDefaultsValidationAndOptimisticLock() throws Exception {
        mvc.perform(get("/api/v1/settings").session(session)).andExpect(jsonPath("$.meetingPresent").value(2)).andExpect(jsonPath("$.eventLate").value(2));
        String settings = "{\"organizationName\":\"Fictional Org\",\"meetingPresent\":4,\"meetingLate\":2,\"eventPresent\":5,\"eventLate\":3,\"version\":0}";
        mvc.perform(put("/api/v1/settings").session(session).with(csrf()).contentType("application/json").content(settings))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(put("/api/v1/settings").session(session).with(csrf()).contentType("application/json").content(settings)).andExpect(status().isConflict());
        assertThrows(ResponseStatusException.class, () -> service.saveSettings(new OrganizationService.Settings("",2,1,3,2,1L)));
        assertThrows(ResponseStatusException.class, () -> service.saveSettings(new OrganizationService.Settings("Test",2,3,3,2,1L)));
        assertThrows(ResponseStatusException.class, () -> service.saveSettings(new OrganizationService.Settings("Test",-1,0,3,2,1L)));
        assertThrows(ResponseStatusException.class, () -> service.saveSettings(new OrganizationService.Settings("Test",2,null,3,2,1L)));
        mvc.perform(put("/api/v1/settings").session(session).with(csrf()).contentType("application/json")
            .content(settings.replace("\"meetingPresent\":4", "\"meetingPresent\":4.5").replace("\"version\":0", "\"version\":1")))
            .andExpect(status().isBadRequest());
    }
    @Test void lifecyclePreservesHistoryAndOnlyOneActive() throws Exception {
        var first = create("First term");
        var second = create("Second term");
        service.transition(first.id(), new OrganizationService.Change(0L), "president", true);
        assertThrows(ResponseStatusException.class, () -> service.transition(second.id(), new OrganizationService.Change(0L), "president", true));
        service.transition(first.id(), new OrganizationService.Change(1L), "president", false);
        service.transition(second.id(), new OrganizationService.Change(0L), "president", true);
        assertEquals("CLOSED", service.get(first.id()).status());
        assertEquals("ACTIVE", service.get(second.id()).status());
        assertEquals(2, service.terms().size());
        assertEquals(3, service.history(first.id()).size());
        assertEquals("DRAFT", service.history(first.id()).getLast().status());
        mvc.perform(get("/api/v1/terms/"+first.id()+"/history").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$[0].changedBy").value("president"));
    }
    @Test void draftsBlockClosingAndClosedCorrectionsAreAudited() throws Exception {
        var term = create("Closed test");
        service.transition(term.id(), new OrganizationService.Change(0L), "president", true);
        when(drafts.hasUnfinishedDrafts(term.id())).thenReturn(true);
        assertThrows(ResponseStatusException.class, () -> service.transition(term.id(), new OrganizationService.Change(1L), "president", false));
        assertEquals("ACTIVE", service.get(term.id()).status());
        when(drafts.hasUnfinishedDrafts(term.id())).thenReturn(false);
        service.transition(term.id(), new OrganizationService.Change(1L), "president", false);
        String base = "/api/v1/terms/"+term.id();
        mvc.perform(put(base).session(session).with(csrf()).contentType("application/json").content(details("Changed",2,""))).andExpect(status().isConflict());
        mvc.perform(post(base+"/corrections").session(session).with(csrf()).contentType("application/json").content(details("Changed",2," "))).andExpect(status().isBadRequest());
        mvc.perform(post(base+"/corrections").session(session).with(csrf()).contentType("application/json").content(details("Changed",2,"Fixed label")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        assertEquals("Fixed label", service.history(term.id()).getFirst().reason());
        assertEquals("Closed test", service.history(term.id()).getLast().name());
        assertThrows(ResponseStatusException.class, () -> service.transition(term.id(), new OrganizationService.Change(3L), "president", true));
    }
    @Test void malformedDuplicateStaleAndMissingTerms() throws Exception {
        mvc.perform(post("/api/v1/terms").session(session).with(csrf()).contentType("application/json").content(details("Term",0,"")))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/terms").session(session).with(csrf()).contentType("application/json").content(details(" term ",0,"")))
            .andExpect(status().isConflict());
        var term = service.terms().getFirst();
        mvc.perform(put("/api/v1/terms/"+term.id()).session(session).with(csrf()).contentType("application/json").content(details("Renamed",0,"")))
            .andExpect(status().isOk());
        mvc.perform(put("/api/v1/terms/"+term.id()).session(session).with(csrf()).contentType("application/json").content(details("Stale",0,"")))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/terms").session(session).with(csrf()).contentType("application/json").content("{\"name\":\"Bad\",\"startDate\":\"not-a-date\"}"))
            .andExpect(status().isBadRequest());
        assertThrows(ResponseStatusException.class, () -> service.create(new OrganizationService.Details("Dates",LocalDate.of(2026,12,31),LocalDate.of(2026,1,1),null,null),"president"));
        mvc.perform(get("/api/v1/terms/"+UUID.randomUUID()+"/history").session(session)).andExpect(status().isNotFound());
    }
    @Test void protectedEndpointsAndCsrf() throws Exception {
        for (String url : new String[]{"/api/v1/settings", "/api/v1/terms", "/api/v1/terms/"+UUID.randomUUID()+"/history"})
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/terms").session(session).contentType("application/json").content(details("Forbidden",0,""))).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/settings").session(session).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/terms").with(csrf()).contentType("application/json").content(details("Unauthorized",0,""))).andExpect(status().isUnauthorized());
    }
    @Test void competingActivationsYieldOneWinner() throws Exception {
        var a = create("Race A"); var b = create("Race B");
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> one = () -> { gate.await(); try { service.transition(a.id(), new OrganizationService.Change(0L),"president",true); return true; } catch (ResponseStatusException e) { return false; } };
            Callable<Boolean> two = () -> { gate.await(); try { service.transition(b.id(), new OrganizationService.Change(0L),"president",true); return true; } catch (ResponseStatusException e) { return false; } };
            var x = pool.submit(one); var y = pool.submit(two); gate.countDown();
            assertNotEquals(x.get(10,TimeUnit.SECONDS), y.get(10,TimeUnit.SECONDS));
            assertEquals(1, service.terms().stream().filter(t -> t.status().equals("ACTIVE")).count());
        }
    }
    @Test void ordinaryWorkRequiresActiveTermAndDatabaseEnforcesActiveSlot() {
        var term = create("Work guard");
        assertThrows(ResponseStatusException.class, () -> transactions.execute(status -> service.requireActiveForWork(term.id())));
        service.transition(term.id(), new OrganizationService.Change(0L), "president", true);
        assertEquals(term.id(), transactions.execute(status -> service.requireActiveForWork(term.id())).id());
        service.transition(term.id(), new OrganizationService.Change(1L), "president", false);
        assertThrows(ResponseStatusException.class, () -> transactions.execute(status -> service.requireActiveForWork(term.id())));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
            () -> jdbc.update("UPDATE academic_term SET status='ACTIVE',active_slot=NULL WHERE id=?", term.id()));
    }
}
