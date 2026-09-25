package ph.edu.slsu.psim.apex.auth;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class PresidentPrincipal extends User {
    private final long credentialVersion;

    public PresidentPrincipal(String username, String hash, long credentialVersion) {
        super(username, hash, List.of(new SimpleGrantedAuthority("ROLE_PRESIDENT")));
        this.credentialVersion = credentialVersion;
    }

    public long credentialVersion() { return credentialVersion; }
}
