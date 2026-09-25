package ph.edu.slsu.psim.apex.auth;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final PresidentAccounts accounts;
    public AuthController(PresidentAccounts accounts) { this.accounts = accounts; }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @GetMapping("/me")
    public Map<String, String> me(@AuthenticationPrincipal PresidentPrincipal president) {
        return Map.of("username", president.getUsername(), "role", "PRESIDENT");
    }

    public record PasswordChange(String currentPassword, String newPassword) {}

    @PostMapping("/password")
    public ResponseEntity<?> password(@AuthenticationPrincipal PresidentPrincipal president,
            @RequestBody PasswordChange change, HttpServletRequest request) {
        try {
            accounts.change(president, change.currentPassword(), change.newPassword());
            request.getSession().invalidate();
            SecurityContextHolder.clearContext();
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }
}
