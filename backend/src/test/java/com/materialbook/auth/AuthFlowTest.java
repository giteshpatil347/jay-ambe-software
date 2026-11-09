package com.materialbook.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.materialbook.AbstractIntegrationTest;
import com.materialbook.users.UserService;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class AuthFlowTest extends AbstractIntegrationTest {
    private static final String PASSWORD = "Correct-Horse-1";
    private static final AtomicInteger SEQ = new AtomicInteger(1000);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserService users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtDecoder jwtDecoder;
    @Autowired BootstrapAdminInitializer bootstrap;

    private String newUser(String role, boolean changeRequired) {
        String mobile = "98" + SEQ.incrementAndGet();
        users.create("Test " + mobile, mobile, encoder.encode(PASSWORD), changeRequired, role);
        return mobile;
    }

    private MvcResult login(String mobile, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("mobile", mobile, "password", password)))).andReturn();
    }

    private JsonNode body(MvcResult r) throws Exception { return json.readTree(r.getResponse().getContentAsString()); }

    private static String refreshCookieValue(MvcResult r) {
        String header = r.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        return header.substring(header.indexOf('=') + 1, header.indexOf(';'));
    }

    private MvcResult refresh(String cookie) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").header("X-Requested-With", "MaterialBook")
                .cookie(new Cookie("refresh_token", cookie))).andReturn();
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder b, MvcResult loginResult) throws Exception {
        return b.header(HttpHeaders.AUTHORIZATION, "Bearer " + body(loginResult).get("accessToken").asText());
    }

    @Test
    void loginSucceedsWithTokenCookieAndPermissions() throws Exception {
        String mobile = newUser("VIEWER", true);
        MvcResult r = login(mobile, PASSWORD);

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode b = body(r);
        assertThat(b.get("expiresIn").asLong()).isEqualTo(600);
        assertThat(b.get("user").get("passwordChangeRequired").asBoolean()).isTrue();
        assertThat(b.get("user").get("permissions").size()).isEqualTo(5); // VIEWER seed
        // Still on a temporary password: the UI gets the real permissions, but the token itself authorises nothing.
        assertThat(jwtDecoder.decode(b.get("accessToken").asText()).getClaimAsStringList("perms")).isEmpty();
        MvcResult settled = login(newUser("VIEWER", false), PASSWORD);
        assertThat(jwtDecoder.decode(body(settled).get("accessToken").asText()).getClaimAsStringList("perms"))
                .hasSize(5).contains("MATERIAL_IN.VIEW");
        String cookie = r.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth");
        assertThat(jdbc.queryForObject("select last_login_at is not null from users where mobile = ?", Boolean.class, mobile)).isTrue();
    }

    @Test
    void wrongPasswordUnknownUserAndInactiveUserGetIdenticalResponse() throws Exception {
        String mobile = newUser("VIEWER", false);
        String inactive = newUser("VIEWER", false);
        jdbc.update("update users set status = 'INACTIVE' where mobile = ?", inactive);

        MvcResult wrong = login(mobile, "nope-nope-nope");
        MvcResult unknown = login("0000000000", PASSWORD);
        MvcResult off = login(inactive, PASSWORD);

        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrong.getResponse().getContentAsString())
                .isEqualTo(unknown.getResponse().getContentAsString())
                .isEqualTo(off.getResponse().getContentAsString());
        assertThat(wrong.getResponse().getContentType()).contains("application/problem+json");
    }

    @Test
    void fiveFailuresLockAccountEvenForCorrectPasswordUntilLockExpires() throws Exception {
        String mobile = newUser("VIEWER", false);
        for (int i = 0; i < 5; i++) {
            assertThat(login(mobile, "bad-bad-bad").getResponse().getStatus()).isEqualTo(401);
        }
        assertThat(jdbc.queryForObject("select locked_until > now() from users where mobile = ?", Boolean.class, mobile)).isTrue();
        assertThat(login(mobile, PASSWORD).getResponse().getStatus()).isEqualTo(401);

        jdbc.update("update users set locked_until = now() - interval '1 minute' where mobile = ?", mobile);
        assertThat(login(mobile, PASSWORD).getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select failed_login_count from users where mobile = ?", Integer.class, mobile)).isZero();
    }

    @Test
    void refreshRotatesAndReuseOfOldTokenRevokesWholeFamily() throws Exception {
        MvcResult first = login(newUser("VIEWER", false), PASSWORD);
        String c1 = refreshCookieValue(first);

        MvcResult second = refresh(c1);
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        String c2 = refreshCookieValue(second);
        assertThat(c2).isNotEqualTo(c1);
        assertThat(body(second).get("accessToken").asText()).isNotBlank();

        assertThat(refresh(c1).getResponse().getStatus()).isEqualTo(401); // reuse
        assertThat(refresh(c2).getResponse().getStatus()).isEqualTo(401); // family revoked
    }

    @Test
    void refreshRequiresCustomHeaderAndCookie() throws Exception {
        String c = refreshCookieValue(login(newUser("VIEWER", false), PASSWORD));
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", c))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/refresh").header("X-Requested-With", "MaterialBook")).andExpect(status().isUnauthorized());
        assertThat(refresh(c).getResponse().getStatus()).isEqualTo(200); // header-less attempt did not burn it
    }

    @Test
    void refreshAfterExpiryOrDeactivationIsRejected() throws Exception {
        String mobile = newUser("VIEWER", false);
        String c = refreshCookieValue(login(mobile, PASSWORD));
        jdbc.update("update refresh_tokens set expires_at = now() - interval '1 second' where user_id = (select id from users where mobile = ?)", mobile);
        assertThat(refresh(c).getResponse().getStatus()).isEqualTo(401);

        String mobile2 = newUser("VIEWER", false);
        String c2 = refreshCookieValue(login(mobile2, PASSWORD));
        jdbc.update("update users set status = 'INACTIVE' where mobile = ?", mobile2);
        assertThat(refresh(c2).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void logoutRevokesFamilyAndClearsCookie() throws Exception {
        String c = refreshCookieValue(login(newUser("VIEWER", false), PASSWORD));
        MvcResult out = mvc.perform(post("/api/v1/auth/logout").header("X-Requested-With", "MaterialBook")
                .cookie(new Cookie("refresh_token", c))).andExpect(status().isNoContent()).andReturn();
        assertThat(out.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        assertThat(refresh(c).getResponse().getStatus()).isEqualTo(401);
        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("refresh_token", c))).andExpect(status().isForbidden());
    }

    @Test
    void meRequiresValidBearerToken() throws Exception {
        MvcResult r = login(newUser("VIEWER", false), PASSWORD);
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer garbage")).andExpect(status().isUnauthorized());
        mvc.perform(bearer(get("/api/v1/auth/me"), r)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.permissions.length()").value(5));
    }

    @Test
    void permissionOverridesAreAppliedToTokenAndMe() throws Exception {
        String mobile = newUser("VIEWER", false);
        jdbc.update("insert into user_permission_overrides(user_id, permission_id, effect) select u.id, p.id, 'DENY' from users u, permissions p where u.mobile = ? and p.permission_code = 'MATERIAL_IN.VIEW'", mobile);
        jdbc.update("insert into user_permission_overrides(user_id, permission_id, effect) select u.id, p.id, 'ALLOW' from users u, permissions p where u.mobile = ? and p.permission_code = 'MATERIAL_IN.CREATE'", mobile);

        MvcResult r = login(mobile, PASSWORD);
        assertThat(jwtDecoder.decode(body(r).get("accessToken").asText()).getClaimAsStringList("perms"))
                .contains("MATERIAL_IN.CREATE").doesNotContain("MATERIAL_IN.VIEW").hasSize(5);
    }

    @Test
    void changePasswordClearsFlagRevokesTokensAndChecksRules() throws Exception {
        String mobile = newUser("VIEWER", true);
        MvcResult r = login(mobile, PASSWORD);
        String c = refreshCookieValue(r);
        String newPw = "Brand-New-Pass-2";

        change(r, PASSWORD, "short").andExpect(status().isUnprocessableEntity());
        change(r, PASSWORD, PASSWORD).andExpect(status().isUnprocessableEntity());
        change(r, "wrong-current-pw", newPw).andExpect(status().isUnprocessableEntity());
        change(r, PASSWORD, newPw).andExpect(status().isNoContent());

        assertThat(refresh(c).getResponse().getStatus()).isEqualTo(401);
        assertThat(login(mobile, PASSWORD).getResponse().getStatus()).isEqualTo(401);
        MvcResult again = login(mobile, newPw);
        assertThat(again.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(again).get("user").get("passwordChangeRequired").asBoolean()).isFalse();
    }

    private org.springframework.test.web.servlet.ResultActions change(MvcResult login, String current, String next) throws Exception {
        return mvc.perform(bearer(post("/api/v1/auth/change-password"), login).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword", current, "newPassword", next))));
    }

    @Test
    void bootstrapAdminIsCreatedExactlyOnce() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from user_roles ur join roles r on r.id = ur.role_id where r.role_code = 'SUPER_ADMIN'", Integer.class)).isEqualTo(1);
        bootstrap.run(null);
        assertThat(jdbc.queryForObject("select count(*) from user_roles ur join roles r on r.id = ur.role_id where r.role_code = 'SUPER_ADMIN'", Integer.class)).isEqualTo(1);

        // Other test classes switch the flag off for their fixtures (skipForcedPasswordChange); restore the first-boot state.
        jdbc.update("update users set password_change_required = true where mobile = '9000000001'");
        MvcResult r = login("9000000001", "Bootstrap-Pass-1");
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(r).get("user").get("passwordChangeRequired").asBoolean()).isTrue();
        assertThat(body(r).get("user").get("permissions").size()).isEqualTo(48);
    }
}
