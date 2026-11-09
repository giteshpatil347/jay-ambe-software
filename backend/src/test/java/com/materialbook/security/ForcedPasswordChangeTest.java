package com.materialbook.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.materialbook.AbstractIntegrationTest;
import com.materialbook.users.UserService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** A user holding a temporary/bootstrap password can do nothing but change it, even by calling the API directly. */
@AutoConfigureMockMvc
class ForcedPasswordChangeTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserService users;
    @Autowired PasswordEncoder encoder;

    private JsonNode login(String mobile, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("mobile", mobile, "password", password)))).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private int status(String url, String token) throws Exception {
        return mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn().getResponse().getStatus();
    }

    @Test
    void oversizedLoginFieldsAreRejectedBeforeAnyPasswordWork() throws Exception {
        int status = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mobile\":\"" + "9".repeat(65) + "\",\"password\":\"x\"}")).andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(422);
    }

    @Test
    void tokenOfAUserWhoMustChangePasswordAuthorisesOnlyMeAndChangePassword() throws Exception {
        users.create("Forced Change", "9611111111", encoder.encode("Temporary-Pass-1"), true, "ADMIN");

        JsonNode session = login("9611111111", "Temporary-Pass-1");
        String token = session.get("accessToken").asText();
        assertThat(session.get("user").get("passwordChangeRequired").asBoolean()).isTrue();
        assertThat(status("/api/v1/auth/me", token)).isEqualTo(200);
        assertThat(status("/api/v1/users", token)).isEqualTo(403);
        assertThat(status("/api/v1/products", token)).isEqualTo(403);
        assertThat(status("/api/v1/attachments?ownerType=EXPENSE&ownerId=1", token)).isNotEqualTo(200);

        int changed = mvc.perform(post("/api/v1/auth/change-password").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword", "Temporary-Pass-1", "newPassword", "Brand-New-Pass-2"))))
                .andReturn().getResponse().getStatus();
        assertThat(changed).isEqualTo(204);

        String fresh = login("9611111111", "Brand-New-Pass-2").get("accessToken").asText();
        assertThat(status("/api/v1/users", fresh)).isEqualTo(200);
    }
}
