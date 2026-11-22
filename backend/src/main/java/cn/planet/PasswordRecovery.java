package cn.planet;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/account/password-reset")
class PasswordRecovery {
    private final SecureRandom random = new SecureRandom();
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final SessionRegistry sessions;
    private final ObjectProvider<JavaMailSender> mail;
    private final String origin;
    private final String from;

    PasswordRecovery(JdbcTemplate db, PasswordEncoder passwords, SessionRegistry sessions,
                     ObjectProvider<JavaMailSender> mail,
                     @Value("${planet.account.reset-origin:}") String origin,
                     @Value("${planet.account.mail-from:}") String from) {
        this.db = db;
        this.passwords = passwords;
        this.sessions = sessions;
        this.mail = mail;
        this.origin = origin;
        this.from = from;
    }

    record ResetRequest(String email) {}
    record ResetPassword(String token, String password) {}
    private record Parent(long id, String email) {}

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    void request(@RequestBody ResetRequest request) {
        String email = ParentAccounts.normalizeEmail(request.email());
        if (!ParentAccounts.validEmail(email)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null || from.isBlank() || !origin.startsWith("https://") ||
                URI.create(origin).getHost() == null)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        var matches = db.queryForList("SELECT id FROM parent_account WHERE email = ?", Long.class, email);
        if (matches.isEmpty()) return;

        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random.generateSeed(32));
        byte[] hash = digest(token);
        db.update("""
            INSERT INTO parent_password_reset(account_id, token_hash, expires_at)
            VALUES (?, ?, CURRENT_TIMESTAMP(6) + INTERVAL 30 MINUTE)
            ON DUPLICATE KEY UPDATE token_hash = ?, expires_at = CURRENT_TIMESTAMP(6) + INTERVAL 30 MINUTE
            """, matches.get(0), hash, hash);

        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("重置小芽星球密码");
        String link = origin.replaceAll("/+$", "") + "/#reset-password=" + token;
        message.setText("请在 30 分钟内点击链接重置密码。\n" + link);
        try {
            sender.send(message);
        } catch (MailException failure) {
            db.update("DELETE FROM parent_password_reset WHERE token_hash = ?", hash);
            org.slf4j.LoggerFactory.getLogger(PasswordRecovery.class).warn("Password reset mail delivery failed");
        }
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void confirm(@RequestBody ResetPassword request) {
        if (request.token() == null || !request.token().matches("[A-Za-z0-9_-]{43}") ||
                !ParentAccounts.validPassword(request.password()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        byte[] hash = digest(request.token());
        Parent parent = db.query("""
            SELECT p.id, p.email FROM parent_password_reset r JOIN parent_account p ON p.id = r.account_id
            WHERE r.token_hash = ? AND r.expires_at > CURRENT_TIMESTAMP(6) FOR UPDATE
            """, (row, index) -> new Parent(row.getLong("id"), row.getString("email")), hash)
            .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        db.update("DELETE FROM parent_password_reset WHERE account_id = ?", parent.id());
        db.update("UPDATE parent_account SET password_hash = ? WHERE id = ?", passwords.encode(request.password()), parent.id());
        for (Object principal : sessions.getAllPrincipals()) {
            if (principal instanceof UserDetails user && user.getUsername().equals(parent.email()))
                sessions.getAllSessions(user, false).forEach(session -> session.expireNow());
        }
    }

    private byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
