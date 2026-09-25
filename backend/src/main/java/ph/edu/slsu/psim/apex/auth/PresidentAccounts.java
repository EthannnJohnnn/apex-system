package ph.edu.slsu.psim.apex.auth;

import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PresidentAccounts implements UserDetailsService {
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public PresidentAccounts(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public PresidentPrincipal loadUserByUsername(String username) {
        return jdbc.query("SELECT * FROM president_account WHERE username = ?",
                (rs, row) -> new PresidentPrincipal(rs.getString("username"),
                        rs.getString("password_hash"), rs.getLong("credential_version")), username)
                .stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    public boolean isCurrent(PresidentPrincipal principal) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT COUNT(*) = 1 FROM president_account WHERE username = ? AND credential_version = ?",
                Boolean.class, principal.getUsername(), principal.credentialVersion()));
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Use at least 12 characters and at most 72 UTF-8 bytes.");
        }
    }

    public void create(String username, String password) {
        if (username == null || !username.matches("[A-Za-z0-9._-]{3,64}")) {
            throw new IllegalArgumentException("Username: 3–64 letters, numbers, dots, underscores or hyphens.");
        }
        validatePassword(password);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM president_account", Integer.class) != 0) {
            throw new IllegalArgumentException("The president account already exists. Use reset instead.");
        }
        jdbc.update("INSERT INTO president_account (id, username, password_hash) VALUES (1, ?, ?)",
                username, encoder.encode(password));
    }

    public void reset(String username, String password) {
        validatePassword(password);
        int changed = jdbc.update(
                "UPDATE president_account SET password_hash = ?, credential_version = credential_version + 1 WHERE username = ?",
                encoder.encode(password), username);
        if (changed != 1) throw new IllegalArgumentException("President account not found.");
    }

    public void change(PresidentPrincipal principal, String currentPassword, String newPassword) {
        validatePassword(newPassword);
        var account = loadUserByUsername(principal.getUsername());
        if (currentPassword == null || !encoder.matches(currentPassword, account.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        int changed = jdbc.update(
                "UPDATE president_account SET password_hash = ?, credential_version = credential_version + 1 WHERE id = 1 AND credential_version = ?",
                encoder.encode(newPassword), principal.credentialVersion());
        if (changed != 1) throw new IllegalArgumentException("Account changed. Sign in again.");
    }
}
