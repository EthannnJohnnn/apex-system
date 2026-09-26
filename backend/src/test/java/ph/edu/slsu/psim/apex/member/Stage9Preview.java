package ph.edu.slsu.psim.apex.member;

import org.springframework.boot.SpringApplication;
import ph.edu.slsu.psim.apex.ApexApplication;
import ph.edu.slsu.psim.apex.auth.PresidentAccounts;

/** Disposable browser-check fixture. Not included in the production JAR. */
public class Stage9Preview {
    public static void main(String[] args) {
        var context = SpringApplication.run(ApexApplication.class,
            "--spring.datasource.url=jdbc:h2:mem:stage9_preview;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            "--spring.datasource.username=sa", "--spring.datasource.password=",
            "--server.address=127.0.0.1", "--server.port=8080");
        context.getBean(PresidentAccounts.class).create("preview_president", "fictional-preview-password");
    }
}
