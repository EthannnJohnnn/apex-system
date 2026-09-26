package ph.edu.slsu.psim.apex.member;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import ph.edu.slsu.psim.apex.auth.PresidentAccounts;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MemberTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PresidentAccounts accounts;
    @Autowired MemberService members;
    MockHttpSession session;

    @BeforeEach void setup() throws Exception {
        jdbc.update("DELETE FROM member_eligibility_history");
        jdbc.update("DELETE FROM member");
        jdbc.update("DELETE FROM president_account");
        accounts.create("president", "fictional-test-password");
        session = (MockHttpSession) mvc.perform(post("/api/v1/auth/login").with(csrf())
            .param("username", "president").param("password", "fictional-test-password"))
            .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }
    String payload(String code, String category, String extra) {
        return "{\"memberCode\":\"" + code + "\",\"name\":\"Fictional Member\",\"position\":\"Test position\",\"category\":\"" + category + "\"" + extra + "}";
    }
    String create(String code, String category, boolean eligible) throws Exception {
        String json = mvc.perform(post("/api/v1/members").session(session).with(csrf()).contentType("application/json")
            .content(payload(code, category, ""))).andExpect(status().isCreated())
            .andExpect(jsonPath("$.eligible").value(eligible)).andExpect(jsonPath("$.active").value(true))
            .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(json, "$.id");
    }

    @Test void defaultsAndUniqueNormalizedIds() throws Exception {
        create("test-001", "MEMBER", true);
        create("test-002", "OFFICER", true);
        create("test-003", "EXECUTIVE", false);
        create("test-004", "PRESIDENT", false);
        mvc.perform(post("/api/v1/members").session(session).with(csrf()).contentType("application/json")
            .content(payload(" TEST-001 ", "MEMBER", ""))).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/members").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(4));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM member_eligibility_history", Integer.class));
    }

    @Test void editsAreValidatedAndStaleWritesRejected() throws Exception {
        String id = create("test-edit", "MEMBER", true);
        mvc.perform(put("/api/v1/members/" + id).session(session).with(csrf()).contentType("application/json")
            .content(payload("test-edit", "OFFICER", ",\"eligible\":true,\"version\":0,\"notes\":\"Fictional note\"")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.notes").value("Fictional note"));
        mvc.perform(put("/api/v1/members/" + id).session(session).with(csrf()).contentType("application/json")
            .content(payload("test-edit", "MEMBER", ",\"eligible\":true,\"version\":0")))
            .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/members/" + id).session(session).with(csrf()).contentType("application/json")
            .content(payload("test-edit", "PRESIDENT", ",\"eligible\":true,\"version\":1")))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/members").session(session).with(csrf()).contentType("application/json")
            .content(payload(" ", "MEMBER", ""))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/members").session(session).with(csrf()).contentType("application/json")
            .content(payload("invalid", "WRONG", ""))).andExpect(status().isBadRequest());
    }

    @Test void eligibilityNeedsReasonAndPreservesEarlierHistory() throws Exception {
        String id = create("test-exec", "EXECUTIVE", false);
        var initial = members.history(UUID.fromString(id)).getFirst();
        mvc.perform(post("/api/v1/members/" + id + "/eligibility").session(session).with(csrf()).contentType("application/json")
            .content("{\"eligible\":true,\"version\":0,\"reason\":\" \"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/members/" + id + "/eligibility").session(session).with(csrf()).contentType("application/json")
            .content("{\"eligible\":true,\"version\":0,\"reason\":\"Opted in for future activities\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(true));
        mvc.perform(get("/api/v1/members/" + id + "/eligibility-history").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].changedBy").value("president"))
            .andExpect(jsonPath("$[1].eligible").value(false));
        assertEquals(initial, members.history(UUID.fromString(id)).get(1));
        assertFalse(members.history(UUID.fromString(id)).getFirst().effectiveAt().isBefore(initial.effectiveAt()));
        String president = create("test-president", "PRESIDENT", false);
        mvc.perform(post("/api/v1/members/" + president + "/eligibility").session(session).with(csrf()).contentType("application/json")
            .content("{\"eligible\":true,\"version\":0,\"reason\":\"Not permitted\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test void editEligibilityAndCreationOverridesRequireReasons() throws Exception {
        mvc.perform(post("/api/v1/members").session(session).with(csrf()).contentType("application/json")
            .content(payload("test-optin", "EXECUTIVE", ",\"eligible\":true"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/members").session(session).with(csrf()).contentType("application/json")
            .content(payload("test-optin", "EXECUTIVE", ",\"eligible\":true,\"reason\":\"Opted in\"")))
            .andExpect(status().isCreated());
        String id = create("test-category", "MEMBER", true);
        mvc.perform(put("/api/v1/members/" + id).session(session).with(csrf()).contentType("application/json")
            .content(payload("test-category", "PRESIDENT", ",\"eligible\":false,\"version\":0")))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/members/" + id).session(session).with(csrf()).contentType("application/json")
            .content(payload("test-category", "PRESIDENT", ",\"eligible\":false,\"version\":0,\"reason\":\"Became president\"")))
            .andExpect(status().isOk());
        assertEquals(2, members.history(UUID.fromString(id)).size());
    }

    @Test void deactivationPreservesRecordsAndHistory() throws Exception {
        String id = create("test-inactive", "MEMBER", true);
        for (int version = 0; version < 2; version++) {
            mvc.perform(post("/api/v1/members/" + id + "/status").session(session).with(csrf()).contentType("application/json")
                .content("{\"active\":" + (version == 1) + ",\"version\":" + version + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(version == 1));
        }
        assertEquals(1, members.list().size());
        assertEquals(1, members.history(UUID.fromString(id)).size());
        mvc.perform(delete("/api/v1/members/" + id).session(session).with(csrf())).andExpect(status().isMethodNotAllowed());
    }

    @Test void endpointsRequireLoginCsrfAndExistingRecord() throws Exception {
        String id = create("test-secure", "MEMBER", true);
        mvc.perform(get("/api/v1/members")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/members/" + id + "/eligibility-history")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/members").session(session).contentType("application/json")
            .content(payload("test-csrf", "MEMBER", ""))).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/members/" + id).with(csrf()).contentType("application/json")
            .content(payload("test-secure", "MEMBER", ",\"eligible\":true,\"version\":0")))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/members/" + UUID.randomUUID() + "/eligibility-history").session(session))
            .andExpect(status().isNotFound());
    }
}
