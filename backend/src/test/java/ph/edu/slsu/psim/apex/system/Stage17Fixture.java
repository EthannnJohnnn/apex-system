package ph.edu.slsu.psim.apex.system;

import java.time.*;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import ph.edu.slsu.psim.apex.ApexApplication;
import ph.edu.slsu.psim.apex.activity.ActivityService;
import ph.edu.slsu.psim.apex.auth.PresidentAccounts;
import ph.edu.slsu.psim.apex.member.MemberService;
import ph.edu.slsu.psim.apex.organization.OrganizationService;
import ph.edu.slsu.psim.apex.points.PointService;
import ph.edu.slsu.psim.apex.report.ReportService;
import ph.edu.slsu.psim.apex.warning.WarningService;

/** Used only by Test-ApexBackup.ps1 against its temporary, password-protected PostgreSQL cluster. */
public class Stage17Fixture {
    public static void main(String[] args) {
        String url = System.getenv("APEX_BACKUP_TEST_URL");
        String password = System.getenv("APEX_BACKUP_TEST_PASSWORD");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/(apex_backup_fixture|apex_restore_test)"))
            throw new IllegalArgumentException("Only the isolated backup-test database is allowed.");
        if (url.contains(":5432/") || password == null || password.isBlank())
            throw new IllegalArgumentException("Never use the normal PostgreSQL port or credentials for this fixture.");
        boolean verify = url.endsWith("/apex_restore_test");
        try (var app = SpringApplication.run(ApexApplication.class,
                "--spring.datasource.url=" + url, "--spring.datasource.username=apex_app",
                "--spring.datasource.password=" + password, "--server.address=127.0.0.1", "--server.port=0")) {
            var accounts = app.getBean(PresidentAccounts.class);
            var jdbc = app.getBean(JdbcTemplate.class);
            var members = app.getBean(MemberService.class);
            var terms = app.getBean(OrganizationService.class);
            var points = app.getBean(PointService.class);
            var activities = app.getBean(ActivityService.class);
            var warnings = app.getBean(WarningService.class);
            String actor = "backup_test_president";
            if (!verify) {
                if (!members.list().isEmpty() || !terms.terms().isEmpty()) throw new IllegalStateException("Fixture database is not empty.");
                accounts.create(actor, "fictional-backup-password");
                var member = members.create(new MemberService.Details("BACKUP-1", "Fictional Recovery Member", "Member", MemberService.Category.MEMBER, null, "Recovery fixture", null, null), actor);
                var officer = members.create(new MemberService.Details("BACKUP-2", "Fictional Recovery Officer", "Officer", MemberService.Category.OFFICER, null, "", null, null), actor);
                var now = OffsetDateTime.now(ZoneOffset.ofHours(8));
                var term = terms.create(new OrganizationService.Details("Fictional recovery term", now.toLocalDate().minusDays(1), now.toLocalDate().plusDays(1), null, null), actor);
                terms.transition(term.id(), new OrganizationService.Change(0L), actor, true);
                var activity = activities.create(new ActivityService.Details(UUID.randomUUID(), term.id(), "Fictional recovery meeting", ActivityService.Kind.MEETING, now, "Test room", "", List.of(member.id(), officer.id()), null), actor);
                activities.saveDraft(activity.activity().id(), new ActivityService.Draft(UUID.randomUUID(), 0L, List.of(
                        new ActivityService.Mark(member.id(), ActivityService.Status.PRESENT), new ActivityService.Mark(officer.id(), ActivityService.Status.LATE))), actor);
                activities.finalizeAttendance(activity.activity().id(), new ActivityService.Change(UUID.randomUUID(), 1L, ""), actor);
                var incident = warnings.create(new WarningService.Create(UUID.randomUUID(), term.id(), member.id(), "BACKUP-CANCELLED", WarningService.Severity.MINOR, null, "", 3, "Fictional cancelled warning"), actor);
                warnings.change(incident.warning().id(), new WarningService.Change(UUID.randomUUID(), 0L, "Fictional reversal"), actor, true, false);
                warnings.create(new WarningService.Create(UUID.randomUUID(), term.id(), member.id(), "BACKUP-OPEN", WarningService.Severity.MAJOR, null, "", 1, "Fictional open warning"), actor);
                System.out.println("BACKUP_FIXTURE_READY: members=2, attendance=2, warnings=2, point_entries=5, net_points=2");
            } else {
                var principal = accounts.loadUserByUsername(actor);
                if (!new BCryptPasswordEncoder().matches("fictional-backup-password", principal.getPassword())) throw new IllegalStateException("Restored login hash differs.");
                var term = terms.terms().getFirst();
                var dashboard = app.getBean(ReportService.class).dashboard(term.id());
                if (dashboard.counts().totalMembers() != 2 || dashboard.counts().netPoints() != 2 || dashboard.counts().openWarnings() != 1)
                    throw new IllegalStateException("Restored application summary differs.");
                if (activities.summary(term.id()).recent().getFirst().present() != 1 || warnings.list(term.id()).size() != 2)
                    throw new IllegalStateException("Restored attendance/warnings differ.");
                long previous = jdbc.queryForObject("SELECT MAX(sequence_no) FROM point_entry", Long.class);
                var entry = points.award(new PointService.Command(UUID.randomUUID(), term.id(), members.list().getFirst().id(), 1, "Post-restore sequence verification"), actor).getFirst();
                if (entry.sequence() <= previous) throw new IllegalStateException("Identity sequence was not restored.");
                System.out.println("RESTORED_APP_VERIFIED: account hash, summaries, attendance, warnings and new ledger sequence passed");
            }
        }
    }
}
