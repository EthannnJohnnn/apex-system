package ph.edu.slsu.psim.apex.system;

import java.time.*;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import ph.edu.slsu.psim.apex.ApexApplication;
import ph.edu.slsu.psim.apex.auth.PresidentAccounts;
import ph.edu.slsu.psim.apex.member.MemberService;
import ph.edu.slsu.psim.apex.organization.OrganizationService;
import ph.edu.slsu.psim.apex.activity.ActivityService;

/** Fictional UI verification only: isolated in-memory database, loopback, no production data. */
public class DashboardPreview {
    public static void main(String[] args) {
        var context = SpringApplication.run(ApexApplication.class,
            "--spring.datasource.url=jdbc:h2:mem:dashboard_preview;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            "--spring.datasource.username=sa", "--spring.datasource.password=",
            "--server.address=127.0.0.1", "--server.port=8080");
        String actor = "preview_president";
        context.getBean(PresidentAccounts.class).create(actor, "fictional-preview-password");
        var members = context.getBean(MemberService.class);
        var roster = new ArrayList<UUID>();
        for (String name : List.of("Alex Rivera", "Jamie Santos", "Sam Reyes", "Taylor Cruz"))
            roster.add(members.create(new MemberService.Details("DEMO-" + roster.size(), name, "Member",
                MemberService.Category.MEMBER, null, "", null, null), actor).id());
        var now = OffsetDateTime.now(ZoneOffset.ofHours(8));
        context.getBean(JdbcTemplate.class).update("UPDATE member_eligibility_history SET effective_at=?", now.minusDays(60));
        var terms = context.getBean(OrganizationService.class);
        var previous = terms.create(new OrganizationService.Details("Previous practice term", now.toLocalDate().minusDays(60), now.toLocalDate().minusDays(31), null, null), actor);
        previous = terms.transition(previous.id(), new OrganizationService.Change(previous.version()), actor, true);
        terms.transition(previous.id(), new OrganizationService.Change(previous.version()), actor, false);
        var term = terms.create(new OrganizationService.Details("Practice term · 2026–2027", now.toLocalDate().minusDays(30), now.toLocalDate().plusDays(30), null, null), actor);
        terms.transition(term.id(), new OrganizationService.Change(term.version()), actor, true);
        var activities = context.getBean(ActivityService.class);
        for (var kind : ActivityService.Kind.values()) {
            var view = activities.create(new ActivityService.Details(UUID.randomUUID(), term.id(), kind == ActivityService.Kind.MEETING ? "General assembly" : "Community outreach", kind, now.minusHours(1), "Practice room", "Fictional dashboard verification", roster, null), actor);
            var marks = new ArrayList<ActivityService.Mark>();
            for (int i = 0; i < roster.size(); i++) marks.add(new ActivityService.Mark(roster.get(i), ActivityService.Status.values()[i]));
            view = activities.saveDraft(view.activity().id(), new ActivityService.Draft(UUID.randomUUID(), view.activity().version(), marks), actor);
            activities.finalizeAttendance(view.activity().id(), new ActivityService.Change(UUID.randomUUID(), view.activity().version(), ""), actor);
        }
        activities.create(new ActivityService.Details(UUID.randomUUID(), term.id(), "Officer planning session", ActivityService.Kind.MEETING, now.plusDays(1), "Organization room", "Fictional upcoming activity", roster, null), actor);
    }
}
