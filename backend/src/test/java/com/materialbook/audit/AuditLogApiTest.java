package com.materialbook.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.materialbook.AbstractIntegrationTest;
import com.materialbook.audit.service.AuditService;
import com.materialbook.users.User;
import com.materialbook.users.UserService;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AuditLogApiTest extends AbstractIntegrationTest {
    private static final String PASSWORD = "Correct-Horse-1";
    private static final AtomicInteger SEQ = new AtomicInteger(5000);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserService users;
    @Autowired PasswordEncoder encoder;
    @Autowired AuditService audit;

    private User newUser(String role) {
        String mobile = "97" + SEQ.incrementAndGet();
        return users.create("Auditor " + mobile, mobile, encoder.encode(PASSWORD), false, role);
    }

    private String bearer(User user, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("mobile", user.getMobile(), "password", password))))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("accessToken").asText();
    }

    @Test
    void endpointRequiresAuditViewPermission() throws Exception {
        String viewer = bearer(newUser("VIEWER"), PASSWORD); // VIEWER seed has no AUDIT.VIEW
        mvc.perform(get("/api/v1/audit-logs").header(HttpHeaders.AUTHORIZATION, viewer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit-logs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/audit-logs").header(HttpHeaders.AUTHORIZATION, bearer(newUser("ADMIN"), PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void filtersPaginatesAndResolvesUserNameNewestFirst() throws Exception {
        User actor = newUser("ADMIN");
        for (int i = 1; i <= 3; i++) {
            audit.logAs(actor.getId(), "CREATE", "FILTERMOD", (long) i, null, Map.of("n", i));
        }
        audit.logAs(null, "DELETE", "FILTERMOD", 2L, Map.of("n", 2), null);
        audit.logAs(actor.getId(), "CREATE", "OTHERMOD", 1L, null, null);
        String auth = bearer(actor, PASSWORD);

        mvc.perform(get("/api/v1/audit-logs").header(HttpHeaders.AUTHORIZATION, auth).param("module", "FILTERMOD")
                        .param("page", "0").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].action").value("DELETE"))
                .andExpect(jsonPath("$.content[0].userFullName").doesNotExist())
                .andExpect(jsonPath("$.content[0].oldValues.n").value(2))
                .andExpect(jsonPath("$.content[1].userFullName").value(actor.getFullName()))
                .andExpect(jsonPath("$.content[1].newValues.n").value(3));
        mvc.perform(get("/api/v1/audit-logs").header(HttpHeaders.AUTHORIZATION, auth).param("module", "FILTERMOD")
                        .param("page", "1").param("size", "3"))
                .andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/api/v1/audit-logs").header(HttpHeaders.AUTHORIZATION, auth).param("module", "FILTERMOD")
                        .param("recordId", "2").param("action", "CREATE").param("userId", String.valueOf(actor.getId())))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/audit-logs").header(HttpHeaders.AUTHORIZATION, auth).param("module", "FILTERMOD")
                        .param("from", "2000-01-01").param("to", "2000-01-02"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/audit-logs").header(HttpHeaders.AUTHORIZATION, auth).param("module", "FILTERMOD")
                        .param("from", "2000-01-01").param("to", "2100-01-01"))
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void failedLoginWritesAuditRowWithMobileButNeverThePassword() throws Exception {
        User user = newUser("VIEWER");
        String wrongPassword = "Wrong-Password-99";
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("mobile", user.getMobile(), "password", wrongPassword))))
                .andExpect(status().isUnauthorized());

        String row = jdbc.queryForObject(
                "select action || ' ' || new_values::text from audit_logs where user_id = ? and action = 'LOGIN_FAILED'",
                String.class, user.getId());
        assertThat(row).contains(user.getMobile()).doesNotContain(wrongPassword);
    }
}
