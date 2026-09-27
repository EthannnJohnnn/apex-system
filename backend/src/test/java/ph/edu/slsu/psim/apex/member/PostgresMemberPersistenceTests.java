package ph.edu.slsu.psim.apex.member;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import ph.edu.slsu.psim.apex.ApexApplication;
import ph.edu.slsu.psim.apex.auth.PresidentAccounts;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in check: uses a fresh random schema, never the application's public tables. */
@EnabledIfEnvironmentVariable(named = "APEX_PG_TEST_PASSWORD", matches = ".+")
class PostgresMemberPersistenceTests {
    private final String url = "jdbc:postgresql://localhost:5432/apex_db";
    private final String password = System.getenv("APEX_PG_TEST_PASSWORD");
    private static final String TEST_PASSWORD = "fictional-persistence-password";

    ConfigurableApplicationContext start(String schema) {
        return SpringApplication.run(ApexApplication.class,
            "--spring.datasource.url=" + url + "?currentSchema=" + schema,
            "--spring.datasource.username=apex_app",
            "--spring.datasource.password=" + password,
            "--spring.flyway.default-schema=" + schema,
            "--server.address=127.0.0.1", "--server.port=0");
    }

    @Test void realHttpMemberSurvivesApplicationRestartOnPostgres() throws Exception {
        String schema = "apex_stage9_test_" + UUID.randomUUID().toString().replace("-", "");
        // Identifier is generated here, never supplied by a user or environment variable.
        try (var connection = DriverManager.getConnection(url, "apex_app", password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            try {
                // Start with the previous Stage 9 schema, then verify the V4 upgrade.
                org.flywaydb.core.Flyway.configure().dataSource(url, "apex_app", password)
                    .defaultSchema(schema).target("3").load().migrate();
                String id;
                String closedTerm;
                String activeTerm;
                try (var first = start(schema)) {
                    first.getBean(PresidentAccounts.class).create("test_president", TEST_PASSWORD);
                    var browser = new Browser(first);
                    browser.login();
                    var created = browser.send("/api/v1/members", "POST",
                        "{\"memberCode\":\"PERSIST-001\",\"name\":\"Fictional Restart Member\",\"position\":\"Member\",\"category\":\"MEMBER\"}", false);
                    assertEquals(201, created.statusCode());
                    id = com.jayway.jsonpath.JsonPath.read(created.body(), "$.id");
                    var settings = browser.send("/api/v1/settings", "PUT",
                        "{\"organizationName\":\"Fictional Persistence Org\",\"meetingPresent\":4,\"meetingLate\":2,\"eventPresent\":5,\"eventLate\":3,\"version\":0}", false);
                    assertEquals(200, settings.statusCode());
                    var term = browser.send("/api/v1/terms", "POST", termDetails("Old term", 0, ""), false);
                    assertEquals(201, term.statusCode());
                    closedTerm = com.jayway.jsonpath.JsonPath.read(term.body(), "$.id");
                    assertEquals(200, browser.send("/api/v1/terms/"+closedTerm+"/activate", "POST", "{\"version\":0}", false).statusCode());
                    assertEquals(200, browser.send("/api/v1/terms/"+closedTerm+"/close", "POST", "{\"version\":1}", false).statusCode());
                    assertEquals(200, browser.send("/api/v1/terms/"+closedTerm+"/corrections", "POST", termDetails("Old term corrected", 2, "Corrected typo"), false).statusCode());
                    var next = browser.send("/api/v1/terms", "POST", termDetails("New term", 0, ""), false);
                    assertEquals(201, next.statusCode());
                    activeTerm = com.jayway.jsonpath.JsonPath.read(next.body(), "$.id");
                    assertEquals(200, browser.send("/api/v1/terms/"+activeTerm+"/activate", "POST", "{\"version\":0}", false).statusCode());
                }
                // A new application context, connection pool and HTTP session must read the same row.
                try (var restarted = start(schema)) {
                    var browser = new Browser(restarted);
                    browser.login();
                    var list = browser.send("/api/v1/members", "GET", null, false);
                    assertEquals(200, list.statusCode());
                    assertEquals(id, com.jayway.jsonpath.JsonPath.read(list.body(), "$[0].id"));
                    var history = browser.send("/api/v1/members/" + id + "/eligibility-history", "GET", null, false);
                    assertEquals(200, history.statusCode());
                    assertEquals("test_president", com.jayway.jsonpath.JsonPath.read(history.body(), "$[0].changedBy"));
                    var settings = browser.send("/api/v1/settings", "GET", null, false);
                    assertEquals("Fictional Persistence Org", com.jayway.jsonpath.JsonPath.read(settings.body(), "$.organizationName"));
                    var terms = restarted.getBean(ph.edu.slsu.psim.apex.organization.OrganizationService.class);
                    assertEquals("ACTIVE", terms.get(UUID.fromString(activeTerm)).status());
                    assertEquals("CLOSED", terms.get(UUID.fromString(closedTerm)).status());
                    assertEquals(4, terms.history(UUID.fromString(closedTerm)).size());
                    assertEquals("Corrected typo", terms.history(UUID.fromString(closedTerm)).getFirst().reason());
                }
            } finally {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private static String termDetails(String name, long version, String reason) {
        return "{\"name\":\""+name+"\",\"startDate\":\"2026-08-01\",\"endDate\":\"2026-12-31\",\"version\":"+version+",\"reason\":\""+reason+"\"}";
    }

    static class Browser {
        final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        final String base;
        Browser(ConfigurableApplicationContext context) {
            base = "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
        }
        void login() throws Exception {
            assertEquals(204, send("/api/v1/auth/login", "POST", "username=test_president&password=" + TEST_PASSWORD, true).statusCode());
        }
        HttpResponse<String> send(String path, String method, String body, boolean form) throws Exception {
            var request = HttpRequest.newBuilder(URI.create(base + path));
            if (body != null) {
                var csrf = client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
                String token = com.jayway.jsonpath.JsonPath.read(csrf.body(), "$.token");
                String header = com.jayway.jsonpath.JsonPath.read(csrf.body(), "$.headerName");
                request.header(header, token).header("Content-Type", form ? "application/x-www-form-urlencoded" : "application/json");
            }
            return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
