package ph.edu.slsu.psim.apex.points;

import java.time.LocalDate;
import org.springframework.boot.SpringApplication;
import ph.edu.slsu.psim.apex.ApexApplication;
import ph.edu.slsu.psim.apex.auth.PresidentAccounts;
import ph.edu.slsu.psim.apex.member.MemberService;
import ph.edu.slsu.psim.apex.organization.OrganizationService;

/** Disposable practice database; excluded from the release JAR. */
public class Stage12Preview {
    public static void main(String[] args) {
        var context=SpringApplication.run(ApexApplication.class,
            "--spring.datasource.url=jdbc:h2:mem:stage12_preview;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            "--spring.datasource.username=sa","--spring.datasource.password=",
            "--server.address=127.0.0.1","--server.port=8080");
        context.getBean(PresidentAccounts.class).create("preview_president","fictional-preview-password");
        var members=context.getBean(MemberService.class);
        members.create(new MemberService.Details("DEMO-001","Fictional Member","Member",MemberService.Category.MEMBER,null,"",null,null),"preview_president");
        members.create(new MemberService.Details("DEMO-002","Fictional Executive","Executive",MemberService.Category.EXECUTIVE,null,"",null,null),"preview_president");
        var terms=context.getBean(OrganizationService.class);
        var term=terms.create(new OrganizationService.Details("Practice term",LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),null,null),"preview_president");
        terms.transition(term.id(),new OrganizationService.Change(0L),"preview_president",true);
    }
}
