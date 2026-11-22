package cn.planet;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
class AccountSecurity {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    UserDetailsService parents(JdbcTemplate db) {
        return email -> db.query("SELECT email, password_hash FROM parent_account WHERE email = ?",
            (row, index) -> User.withUsername(row.getString("email"))
                .password(row.getString("password_hash")).roles("PARENT").build(),
            ParentAccounts.normalizeEmail(email)).stream().findFirst()
            .orElseThrow(() -> new UsernameNotFoundException("Account credentials rejected"));
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    HttpSessionEventPublisher sessionEvents() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SessionRegistry sessions) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/account/csrf", "/api/account/register", "/api/account/login",
                    "/api/account/password-reset", "/api/account/password-reset/confirm").permitAll()
                .requestMatchers("/api/account/**").hasRole("PARENT")
                .anyRequest().permitAll())
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/activities", "/api/storybooks"))
            .addFilterAfter(new AccountRequestLimit(), CsrfFilter.class)
            .requestCache(cache -> cache.disable())
            .sessionManagement(session -> session.maximumSessions(-1).sessionRegistry(sessions)
                .expiredSessionStrategy(event -> event.getResponse().setStatus(HttpServletResponse.SC_UNAUTHORIZED)))
            .exceptionHandling(errors -> errors.authenticationEntryPoint(
                (request, response, failure) -> response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)))
            .formLogin(login -> login.loginProcessingUrl("/api/account/login").usernameParameter("email")
                .successHandler((request, response, authentication) -> response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                .failureHandler((request, response, failure) -> response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)))
            .logout(logout -> logout.logoutUrl("/api/account/logout")
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) ->
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
            .build();
    }
}
