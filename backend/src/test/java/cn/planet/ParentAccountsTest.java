package cn.planet;

import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"planet.account.reset-origin=https://planet.example.test",
    "planet.account.mail-from=team@example.test"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "PLANET_TEST_DB", matches = "true")
@Transactional
class ParentAccountsTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @MockBean JavaMailSender mail;

    @Test
    void registrationLoginAndLogoutRequireTheCorrectSession() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        String password = "test-only-long-password";
        String body = json.writeValueAsString(new ParentAccounts.Registration(email, password));

        mvc.perform(get("/api/account")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/account/register").contentType("application/json").content(body))
            .andExpect(status().isForbidden());
        MockHttpSession session = new MockHttpSession();
        String token = csrf(session);
        mvc.perform(post("/api/account/register").session(session).header("X-CSRF-TOKEN", token)
            .contentType("application/json").content(body)).andExpect(status().isCreated());
        mvc.perform(post("/api/account/register").session(session).header("X-CSRF-TOKEN", token)
            .contentType("application/json").content(body)).andExpect(status().isConflict());
        assertNotEquals(password, db.queryForObject(
            "SELECT password_hash FROM parent_account WHERE email = ?", String.class, email));

        mvc.perform(post("/api/account/login").session(session).header("X-CSRF-TOKEN", token)
            .param("email", email).param("password", "wrong-password")).andExpect(status().isUnauthorized());
        String beforeLogin = session.getId();
        mvc.perform(post("/api/account/login").session(session).header("X-CSRF-TOKEN", token)
            .param("email", email.toUpperCase(java.util.Locale.ROOT)).param("password", password))
            .andExpect(status().isNoContent());
        assertNotEquals(beforeLogin, session.getId());
        mvc.perform(get("/api/account").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(email));
        mvc.perform(get("/api/account").session(new MockHttpSession())).andExpect(status().isUnauthorized());
        String library = """
            {"revision":0,"data":{"planetLogs":[{"title":"旧活动","i":0}],"planetSaved":[],"planet.storybooks.v1":[]}}
            """;
        var libraryToken = csrf(session);
        mvc.perform(put("/api/account/library").session(session).header("X-CSRF-TOKEN", libraryToken)
            .contentType("application/json").content(library)).andExpect(status().isOk())
            .andExpect(jsonPath("$.revision").value(1));
        mvc.perform(put("/api/account/library").session(session).header("X-CSRF-TOKEN", libraryToken)
            .contentType("application/json").content(library)).andExpect(status().isConflict());
        mvc.perform(get("/api/account/library").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.planetLogs[0].title").value("旧活动"));
        mvc.perform(get("/api/account/library")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/account/logout").session(session).header("X-CSRF-TOKEN", csrf(session)))
            .andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
    }

    private String csrf(MockHttpSession session) throws Exception {
        var response = mvc.perform(get("/api/account/csrf").session(session))
            .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray()).path("token").asText();
    }

    @Test
    void resetLinkWorksOnceAndExpiresTheOldSession() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        String oldPassword = "test-only-old-password";
        String newPassword = "test-only-new-password";
        MockHttpSession session = new MockHttpSession();
        String token = csrf(session);
        mvc.perform(post("/api/account/register").session(session).header("X-CSRF-TOKEN", token)
            .contentType("application/json").content(json.writeValueAsString(new ParentAccounts.Registration(email, oldPassword))))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/account/login").session(session).header("X-CSRF-TOKEN", token)
            .param("email", email).param("password", oldPassword)).andExpect(status().isNoContent());

        MockHttpSession recovery = new MockHttpSession();
        String recoveryCsrf = csrf(recovery);
        mvc.perform(post("/api/account/password-reset").session(recovery).header("X-CSRF-TOKEN", recoveryCsrf)
            .contentType("application/json").content(json.writeValueAsString(new PasswordRecovery.ResetRequest(email))))
            .andExpect(status().isAccepted());
        var sent = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(sent.capture());
        assertArrayEquals(new String[]{email}, sent.getValue().getTo());
        String link = sent.getValue().getText();
        String prefix = "https://planet.example.test/#reset-password=";
        assertTrue(link.contains(prefix));
        String resetToken = link.substring(link.indexOf(prefix) + prefix.length());
        String body = json.writeValueAsString(new PasswordRecovery.ResetPassword(resetToken, newPassword));
        mvc.perform(post("/api/account/password-reset/confirm").session(recovery).header("X-CSRF-TOKEN", recoveryCsrf)
            .contentType("application/json").content(body)).andExpect(status().isNoContent());
        mvc.perform(get("/api/account").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/account/password-reset/confirm").session(recovery).header("X-CSRF-TOKEN", recoveryCsrf)
            .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/account/login").session(recovery).header("X-CSRF-TOKEN", recoveryCsrf)
            .param("email", email).param("password", newPassword)).andExpect(status().isNoContent());
    }
}
