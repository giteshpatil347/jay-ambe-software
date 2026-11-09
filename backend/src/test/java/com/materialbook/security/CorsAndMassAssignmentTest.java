package com.materialbook.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.materialbook.AbstractIntegrationTest;
import com.materialbook.users.UserService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class CorsAndMassAssignmentTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserService users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwtService;

    @Test
    void onlyTheConfiguredOriginMayCallCrossOrigin() throws Exception {
        MvcResult foreign = mvc.perform(options("/api/v1/users").header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")).andReturn();
        assertThat(foreign.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
        assertThat(foreign.getResponse().getStatus()).isEqualTo(403);

        // a look-alike of the configured origin (suffix / different port) is not the origin either
        for (String lookalike : List.of("http://localhost:5173.evil.example", "http://localhost:5174", "https://localhost:5173")) {
            MvcResult r = mvc.perform(options("/api/v1/users").header(HttpHeaders.ORIGIN, lookalike)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")).andReturn();
            assertThat(r.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).as(lookalike).isNull();
        }

        MvcResult own = mvc.perform(options("/api/v1/users").header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")).andReturn();
        assertThat(own.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("http://localhost:5173");
        assertThat(own.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNotEqualTo("*");
    }

    @Test
    void unknownBodyFieldsCannotChangeStatusRolesOrPasswordHash() throws Exception {
        long id = users.create("Victim", "9622222222", encoder.encode("Temporary-Pass-1"), true, "VIEWER").getId();
        String hashBefore = jdbc.queryForObject("select password_hash from users where id = ?", String.class, id);
        Long adminId = jdbc.queryForObject("select id from users where mobile = '9000000001'", Long.class);
        String token = jwtService.issue(adminId, List.of("USER.UPDATE"));

        Map<String, Object> body = Map.of("fullName", "Renamed", "mobile", "9622222222",
                "status", "INACTIVE", "passwordHash", "x", "roleCodes", List.of("SUPER_ADMIN"), "failedLoginCount", 99,
                "passwordChangeRequired", false, "id", 1);
        int status = mvc.perform(put("/api/v1/users/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        assertThat(jdbc.queryForObject("select full_name from users where id = ?", String.class, id)).isEqualTo("Renamed");
        assertThat(jdbc.queryForObject("select status from users where id = ?", String.class, id)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select password_hash from users where id = ?", String.class, id)).isEqualTo(hashBefore);
        assertThat(jdbc.queryForObject("select password_change_required from users where id = ?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("select failed_login_count from users where id = ?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForList("select r.role_code from user_roles ur join roles r on r.id = ur.role_id where ur.user_id = ?", String.class, id))
                .containsExactly("VIEWER");
    }
}
