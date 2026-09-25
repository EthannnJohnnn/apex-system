package ph.edu.slsu.psim.apex;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.WebApplicationType;
import ph.edu.slsu.psim.apex.auth.AccountConsole;
import ph.edu.slsu.psim.apex.auth.PresidentAccounts;

@SpringBootApplication
public class ApexApplication {

	public static void main(String[] args) {
		String operation = java.util.Arrays.stream(args)
				.filter(arg -> arg.startsWith("--apex.account="))
				.map(arg -> arg.substring("--apex.account=".length())).findFirst().orElse(null);
		if (operation == null) {
			SpringApplication.run(ApexApplication.class, args);
			return;
		}
		var application = new SpringApplication(ApexApplication.class);
		application.setWebApplicationType(WebApplicationType.NONE);
		try (var context = application.run(args)) {
			AccountConsole.run(operation, context.getBean(PresidentAccounts.class), System.console());
		}
	}

}
