package ph.edu.slsu.psim.apex.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthTests {
    @Autowired MockMvc mvc;
    @Autowired PresidentAccounts accounts;
    @Autowired JdbcTemplate jdbc;
    private static final String PASSWORD = "fictional-test-password";

    @BeforeEach void prepare() {
        jdbc.update("DELETE FROM president_account");
        accounts.create("president", PASSWORD);
    }

    MockHttpSession login() throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/v1/auth/login").with(csrf())
                .param("username", "president").param("password", PASSWORD))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }

    @Test void loginSessionAndLogout() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/members")).andExpect(status().isUnauthorized());
        var session = login();
        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("president"))
                .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void incorrectCredentialsAndMissingCsrfFail() throws Exception {
        mvc.perform(post("/api/v1/auth/login").with(csrf()).param("username", "president")
                .param("password", "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").param("username", "president")
                .param("password", PASSWORD)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test void browserCsrfTokenAndSessionRotationWork() throws Exception {
        var csrfResult = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        String originalId = session.getId();
        String token = com.jayway.jsonpath.JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.token");
        String header = com.jayway.jsonpath.JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.headerName");
        mvc.perform(post("/api/v1/auth/login").session(session).header(header, token)
                .param("username", "president").param("password", PASSWORD))
                .andExpect(status().isNoContent());
        assertNotEquals(originalId, session.getId());
        mvc.perform(post("/api/v1/auth/password").session(session)
                .contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk());
    }

    @Test void passwordChangeRequiresCurrentPasswordAndInvalidatesOtherSessions() throws Exception {
        var first = login();
        var second = login();
        mvc.perform(post("/api/v1/auth/password").session(first).with(csrf())
                .contentType("application/json")
                .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"another-test-password\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/password").session(first).with(csrf())
                .contentType("application/json")
                .content("{\"currentPassword\":\"fictional-test-password\",\"newPassword\":\"another-test-password\"}"))
                .andExpect(status().isNoContent());
        assertTrue(first.isInvalid());
        mvc.perform(get("/api/v1/auth/me").session(second)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").with(csrf()).param("username", "president")
                .param("password", PASSWORD)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").with(csrf()).param("username", "president")
                .param("password", "another-test-password")).andExpect(status().isNoContent());
    }

    @Test void localRecoveryInvalidatesSessionsAndOnlyOneAccountExists() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> accounts.create("another", PASSWORD));
        assertThrows(IllegalArgumentException.class, () -> accounts.reset("president", "short"));
        var session = login();
        accounts.reset("president", "recovered-test-password");
        mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());
        assertNotEquals("recovered-test-password", jdbc.queryForObject(
                "SELECT password_hash FROM president_account WHERE id = 1", String.class));
        mvc.perform(post("/api/v1/auth/login").with(csrf()).param("username", "president")
                .param("password", "recovered-test-password")).andExpect(status().isNoContent());
    }
}
