package ph.edu.slsu.psim.apex.warning;

import java.time.*;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import ph.edu.slsu.psim.apex.ApexApplication;
import ph.edu.slsu.psim.apex.auth.PresidentAccounts;
import ph.edu.slsu.psim.apex.member.MemberService;
import ph.edu.slsu.psim.apex.organization.OrganizationService;
import ph.edu.slsu.psim.apex.activity.ActivityService;

/** Disposable local practice; excluded from the release JAR. */
public class Stage14Preview {
    public static void main(String[] args) {
        var context=SpringApplication.run(ApexApplication.class,
            "--spring.datasource.url=jdbc:h2:mem:stage14_preview;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            "--spring.datasource.username=sa","--spring.datasource.password=",
            "--server.address=127.0.0.1","--server.port=8080");
        String actor="preview_president";
        context.getBean(PresidentAccounts.class).create(actor,"fictional-preview-password");
        var members=context.getBean(MemberService.class);
        var roster=new ArrayList<UUID>();
        for(var category:MemberService.Category.values()) roster.add(members.create(new MemberService.Details("DEMO-"+category,"Fictional "+category,category.name(),category,null,"",null,null),actor).id());
        var now=OffsetDateTime.now(ZoneOffset.ofHours(8));
        context.getBean(JdbcTemplate.class).update("UPDATE member_eligibility_history SET effective_at=?",now.minusDays(2));
        var terms=context.getBean(OrganizationService.class);
        var term=terms.create(new OrganizationService.Details("Practice term",now.toLocalDate().minusDays(30),now.toLocalDate().plusDays(30),null,null),actor);
        terms.transition(term.id(),new OrganizationService.Change(0L),actor,true);
        var activities=context.getBean(ActivityService.class);
        for(var kind:ActivityService.Kind.values()) {
            var v=activities.create(new ActivityService.Details(UUID.randomUUID(),term.id(),"Fictional "+kind,kind,now.minusHours(1),"Practice room","Fictional no-show practice",roster,null),actor);
            v=activities.saveDraft(v.activity().id(),new ActivityService.Draft(UUID.randomUUID(),0L,roster.stream().map(id -> new ActivityService.Mark(id,ActivityService.Status.ABSENT)).toList()),actor);
            activities.finalizeAttendance(v.activity().id(),new ActivityService.Change(UUID.randomUUID(),v.activity().version(),""),actor);
        }
    }
}
