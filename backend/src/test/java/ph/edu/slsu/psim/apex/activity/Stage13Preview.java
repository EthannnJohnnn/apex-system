package ph.edu.slsu.psim.apex.activity;

import java.time.*;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import ph.edu.slsu.psim.apex.ApexApplication;
import ph.edu.slsu.psim.apex.auth.PresidentAccounts;
import ph.edu.slsu.psim.apex.member.MemberService;
import ph.edu.slsu.psim.apex.organization.OrganizationService;

/** Fictional, in-memory practice only. Never packaged in the release JAR. */
public class Stage13Preview {
    public static void main(String[] args) {
        var context=SpringApplication.run(ApexApplication.class,
            "--spring.datasource.url=jdbc:h2:mem:stage13_preview;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            "--spring.datasource.username=sa","--spring.datasource.password=",
            "--server.address=127.0.0.1","--server.port=8080");
        context.getBean(PresidentAccounts.class).create("preview_president","fictional-preview-password");
        var members=context.getBean(MemberService.class);
        for(var category:MemberService.Category.values()) members.create(new MemberService.Details("DEMO-"+category,"Fictional "+category,category.name(),category,null,"",null,null),"preview_president");
        var now=OffsetDateTime.now(ZoneOffset.ofHours(8));
        context.getBean(JdbcTemplate.class).update("UPDATE member_eligibility_history SET effective_at=?",now.minusDays(2));
        var terms=context.getBean(OrganizationService.class);
        var term=terms.create(new OrganizationService.Details("Practice term",now.toLocalDate().minusDays(30),now.toLocalDate().plusDays(30),null,null),"preview_president");
        terms.transition(term.id(),new OrganizationService.Change(0L),"preview_president",true);
    }
}
