package ph.edu.slsu.psim.apex.auth;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

// Installed only in the Spring Security chain, not as a second servlet filter.
public class CredentialVersionFilter extends OncePerRequestFilter {
    private final PresidentAccounts accounts;
    public CredentialVersionFilter(PresidentAccounts accounts) { this.accounts = accounts; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof PresidentPrincipal principal
                && !accounts.isCurrent(principal)) {
            var session = request.getSession(false);
            if (session != null) session.invalidate();
            SecurityContextHolder.clearContext();
            response.setStatus(401);
            return;
        }
        chain.doFilter(request, response);
    }
}
