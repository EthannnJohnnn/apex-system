package ph.edu.slsu.psim.apex.auth;

import java.io.Console;
import java.util.Arrays;

public final class AccountConsole {
    private AccountConsole() {}

    public static void run(String operation, PresidentAccounts accounts, Console console) {
        if (console == null) throw new IllegalStateException("Run this command in a real terminal; a hidden password prompt is required.");
        if (!operation.equals("create") && !operation.equals("reset")) {
            throw new IllegalArgumentException("Use --apex.account=create or --apex.account=reset.");
        }
        String username = console.readLine("President username: ");
        char[] password = console.readPassword("New password (12+ characters): ");
        char[] confirmation = console.readPassword("Confirm password: ");
        try {
            if (password == null || confirmation == null || !Arrays.equals(password, confirmation)) {
                throw new IllegalArgumentException("Passwords do not match.");
            }
            if (operation.equals("create")) accounts.create(username, new String(password));
            else accounts.reset(username, new String(password));
            console.printf("President account %s successful.%n", operation);
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }
}
