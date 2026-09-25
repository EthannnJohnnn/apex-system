package ph.edu.slsu.psim.apex.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ph.edu.slsu.psim.apex.auth.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, PresidentAccounts accounts) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/health", "/api/v1/auth/csrf", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/**").hasRole("PRESIDENT")
                        .anyRequest().denyAll())
                .formLogin(form -> form.loginProcessingUrl("/api/v1/auth/login")
                    .successHandler((request, response, auth) -> response.setStatus(204))
                    .failureHandler((request, response, error) -> response.setStatus(401)))
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(logout -> logout.logoutUrl("/api/v1/auth/logout").deleteCookies("JSESSIONID")
                    .logoutSuccessHandler((request, response, auth) -> response.setStatus(204)))
                .exceptionHandling(errors -> errors
                    .authenticationEntryPoint((request, response, error) -> response.setStatus(401))
                    .accessDeniedHandler((request, response, error) -> response.setStatus(403)))
                .addFilterBefore(new CredentialVersionFilter(accounts), UsernamePasswordAuthenticationFilter.class)
                .requestCache(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
