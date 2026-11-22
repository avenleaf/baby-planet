package cn.planet;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/account")
class ParentAccounts {
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;

    ParentAccounts(JdbcTemplate db, PasswordEncoder passwords) {
        this.db = db;
        this.passwords = passwords;
    }

    static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    record Registration(String email, String password) {}
    record Account(String email) {}

    @GetMapping("/csrf")
    CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @GetMapping
    Account current(Principal principal) {
        return new Account(principal.getName());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    void register(@RequestBody Registration registration) {
        String email = normalizeEmail(registration.email());
        String password = registration.password();
        if (!validEmail(email) || !validPassword(password))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        try {
            db.update("INSERT INTO parent_account(email, password_hash) VALUES (?, ?)", email, passwords.encode(password));
        } catch (DuplicateKeyException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
    }

    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");

    static boolean validEmail(String email) {
        return email.length() <= 254 && EMAIL.matcher(email).matches();
    }

    static boolean validPassword(String password) {
        return password != null && password.length() >= 12 &&
            password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
