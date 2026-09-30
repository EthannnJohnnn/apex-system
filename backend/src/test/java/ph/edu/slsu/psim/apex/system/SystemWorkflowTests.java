package ph.edu.slsu.psim.apex.system;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import ph.edu.slsu.psim.apex.ApexApplication;
import ph.edu.slsu.psim.apex.auth.PresidentAccounts;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP, cookies and CSRF across the complete workflow, in a disposable database. */
class SystemWorkflowTests {
    private static final String PASSWORD = "fictional-system-password";

    @Test void completeLaptopWorkflowWithTwoSessions() throws Exception {
        // Explicit command-line settings cannot fall back to the user's PostgreSQL database.
        try (var app = SpringApplication.run(ApexApplication.class,
                "--spring.datasource.url=jdbc:h2:mem:system_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
                "--spring.datasource.username=sa", "--spring.datasource.password=",
                "--server.address=127.0.0.1", "--server.port=0")) {
            app.getBean(PresidentAccounts.class).create("test_president", PASSWORD);
            String base = "http://127.0.0.1:" + ((WebServerApplicationContext) app).getWebServer().getPort();
            var first = new Session(base);
            var second = new Session(base);
            first.expect(401, "/api/v1/members", "GET", null);
            first.login(); second.login();

            first.expect(200, "/api/v1/settings", "PUT", Map.of("organizationName", "Fictional Stage 16 Org",
                    "meetingPresent", 2, "meetingLate", 1, "eventPresent", 3, "eventLate", 2, "version", 0));
            var today = LocalDate.now(ZoneId.of("Asia/Manila"));
            String term = id(first.expect(201, "/api/v1/terms", "POST", Map.of("name", "Fictional system term",
                    "startDate", today.minusDays(2).toString(), "endDate", today.plusDays(2).toString())), "$.id");
            first.expect(200, "/api/v1/terms/" + term + "/activate", "POST", Map.of("version", 0));
            String member = first.member("SYS-M", "MEMBER");
            String officer = first.member("SYS-O", "OFFICER");
            String executive = first.member("SYS-E", "EXECUTIVE");
            String president = first.member("SYS-P", "PRESIDENT");

            // Two independently authenticated sessions read the same version. A stale save must not overwrite.
            first.expect(200, "/api/v1/members", "GET", null);
            second.expect(200, "/api/v1/members", "GET", null);
            var edit = Map.of("memberCode", "SYS-M", "name", "Fictional updated member", "position", "Member",
                    "category", "MEMBER", "eligible", true, "version", 0);
            first.expect(200, "/api/v1/members/" + member, "PUT", edit);
            String conflict = second.expect(409, "/api/v1/members/" + member, "PUT", edit);
            assertFalse(id(conflict, "$.message").isBlank());
            first.expect(400, "/api/v1/points", "POST", award(term, president));
            first.expect(400, "/api/v1/points", "POST", award(term, executive));

            // The activity occurs after member creation but before the executive opts in.
            String activity = id(first.expect(200, "/api/v1/activities", "POST", Map.of(
                    "requestId", UUID.randomUUID().toString(), "termId", term, "title", "Fictional meeting",
                    "kind", "MEETING", "scheduledAt", OffsetDateTime.now(ZoneOffset.ofHours(8)).toString(),
                    "memberIds", List.of(member, officer, executive, president))), "$.activity.id");
            first.expect(200, "/api/v1/members/" + executive + "/eligibility", "POST",
                    Map.of("eligible", true, "reason", "Future activities only", "version", 0));
            List<Map<String, String>> marks = List.of(member, officer, executive, president).stream()
                    .map(m -> Map.of("memberId", m, "status", "PRESENT")).toList();
            first.expect(200, "/api/v1/activities/" + activity + "/attendance", "PUT",
                    Map.of("requestId", UUID.randomUUID().toString(), "version", 0, "marks", marks));
            var finalize = Map.of("requestId", UUID.randomUUID().toString(), "version", 1);
            String finalized = first.expect(200, "/api/v1/activities/" + activity + "/finalize", "POST", finalize);
            assertEquals(finalized, second.expect(200, "/api/v1/activities/" + activity + "/finalize", "POST", finalize));
            first.totals(term, Map.of(member, 2, officer, 2, executive, 0, president, 0), 2);

            first.expect(200, "/api/v1/activities/" + activity + "/corrections", "POST", Map.of(
                    "requestId", UUID.randomUUID().toString(), "version", 2, "memberId", member,
                    "status", "LATE", "reason", "Corrected fictional arrival"));
            first.totals(term, Map.of(member, 1, officer, 2, executive, 0, president, 0), 4);

            String warning = UUID.randomUUID().toString();
            first.expect(200, "/api/v1/warnings", "POST", Map.of("requestId", warning, "termId", term,
                    "memberId", member, "incident", "PRIVATE-SYS-INCIDENT", "severity", "MINOR",
                    "deduction", 3, "reason", "PRIVATE fictional narrative"));
            first.totals(term, Map.of(member, -2, officer, 2, executive, 0, president, 0), 5);
            first.expect(200, "/api/v1/warnings/" + warning + "/resolve", "POST", Map.of(
                    "requestId", UUID.randomUUID().toString(), "version", 0, "reason", "Fictional review"));
            first.totals(term, Map.of(member, -2, officer, 2, executive, 0, president, 0), 5);
            var cancel = Map.of("requestId", UUID.randomUUID().toString(), "version", 1, "reason", "Fictional cancellation");
            first.expect(200, "/api/v1/warnings/" + warning + "/cancel", "POST", cancel);
            second.expect(200, "/api/v1/warnings/" + warning + "/cancel", "POST", cancel);
            first.totals(term, Map.of(member, 1, officer, 2, executive, 0, president, 0), 6);

            String board = first.expect(200, "/api/v1/leaderboard?termId=" + term + "&view=all", "GET", null);
            assertEquals(List.of(officer, member, executive), JsonPath.read(board, "$.members[*].memberId"));
            String dashboard = first.expect(200, "/api/v1/dashboard?termId=" + term, "GET", null);
            assertEquals(3, (int) JsonPath.read(dashboard, "$.counts.netPoints"));
            assertFalse(dashboard.contains("PRIVATE")); assertFalse(board.contains(president));

            first.expect(200, "/api/v1/terms/" + term + "/close", "POST", Map.of("version", 1));
            first.expect(409, "/api/v1/points", "POST", award(term, member));
            first.expect(409, "/api/v1/activities/" + activity + "/corrections", "POST", Map.of(
                    "requestId", UUID.randomUUID().toString(), "version", 3, "memberId", member,
                    "status", "PRESENT", "reason", "Ordinary closed edit must fail"));
            // Explicit corrections are audited and historical reports use the resulting ledger.
            first.expect(200, "/api/v1/activities/" + activity + "/closed-corrections", "POST", Map.of(
                    "requestId", UUID.randomUUID().toString(), "version", 3, "memberId", member,
                    "status", "PRESENT", "reason", "Authorized fictional closed correction"));
            first.totals(term, Map.of(member, 2, officer, 2, executive, 0, president, 0), 8);
            String historical = first.expect(200, "/api/v1/leaderboard?termId=" + term, "GET", null);
            assertEquals(List.of(1, 1, 3), JsonPath.read(historical, "$.members[*].rank"));
            assertEquals("CLOSED", id(historical, "$.term.status"));
            first.expect(204, "/api/v1/auth/logout", "POST", Map.of());
            for (String path : List.of("/members", "/points?termId=" + term, "/warnings?termId=" + term,
                    "/activities?termId=" + term, "/leaderboard?termId=" + term, "/dashboard")) {
                first.expect(401, "/api/v1" + path, "GET", null);
            }
            second.expect(200, "/api/v1/dashboard", "GET", null); // Logout affects the intended session.
        }
    }

    static Map<String, Object> award(String term, String member) {
        return Map.of("requestId", UUID.randomUUID().toString(), "termId", term,
                "memberId", member, "amount", 5, "reason", "Fictional contribution");
    }
    static String id(String body, String path) { return JsonPath.read(body, path); }

    static class Session {
        final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager())
                .connectTimeout(Duration.ofSeconds(10)).build();
        final String base;
        Session(String base) { this.base = base; }
        void login() throws Exception {
            expect(204, "/api/v1/auth/login", "POST", "username=test_president&password=" + PASSWORD);
        }
        String member(String code, String category) throws Exception {
            return id(expect(201, "/api/v1/members", "POST", Map.of("memberCode", code,
                    "name", "Fictional " + code, "position", category, "category", category)), "$.id");
        }
        String expect(int status, String path, String method, Object body) throws Exception {
            var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15));
            String payload = "";
            if (body != null) {
                String csrf = expect(200, "/api/v1/auth/csrf", "GET", null);
                request.header(id(csrf, "$.headerName"), id(csrf, "$.token"));
                boolean form = body instanceof String;
                request.header("Content-Type", form ? "application/x-www-form-urlencoded" : "application/json");
                payload = form ? (String) body : com.jayway.jsonpath.Configuration.defaultConfiguration().jsonProvider().toJson(body);
            }
            var response = client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(payload)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(status, response.statusCode(), method + " " + path + ": " + response.body());
            return response.body();
        }
        void totals(String term, Map<String, Integer> expected, int entryCount) throws Exception {
            String ledger = expect(200, "/api/v1/points?termId=" + term, "GET", null);
            for (var entry : expected.entrySet()) {
                List<Integer> values = JsonPath.read(ledger, "$.totals[?(@.memberId=='" + entry.getKey() + "')].total");
                assertEquals(List.of(entry.getValue()), values);
            }
            assertEquals(entryCount, (int) JsonPath.read(ledger, "$.entries.length()"));
        }
    }
}
