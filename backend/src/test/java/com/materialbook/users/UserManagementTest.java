package com.materialbook.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

class UserManagementTest extends AbstractUserApiTest {
    @Autowired PasswordEncoder encoder;

    @Test
    void createUserHashesPasswordNeverReturnsItAndAudits() throws Exception {
        String admin = superToken();
        String mobile = newMobile();
        MvcResult r = call(HttpMethod.POST, "/api/v1/users", admin, userBody(mobile, "VIEWER"));

        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = tree(r);
        assertThat(body.get("passwordChangeRequired").asBoolean()).isTrue();
        assertThat(body.get("roles").get(0).asText()).isEqualTo("VIEWER");
        assertThat(r.getResponse().getContentAsString()).doesNotContain("passwordHash").doesNotContain("password_hash").doesNotContain(PASSWORD);
        String hash = jdbc.queryForObject("select password_hash from users where mobile = ?", String.class, mobile);
        assertThat(hash).startsWith("$2a$12$");
        assertThat(encoder.matches(PASSWORD, hash)).isTrue();
        assertThat(auditCount("USER_CREATE", body.get("id").asLong())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select new_values::text from audit_logs where action = 'USER_CREATE' and record_id = ?",
                String.class, body.get("id").asLong())).doesNotContain("$2a$");
        assertThat(login(mobile, PASSWORD)).isNotBlank();
    }

    @Test
    void createRejectsDuplicatesWeakPasswordAndUnknownRole() throws Exception {
        String admin = superToken();
        String mobile = newMobile();
        createUser(admin, mobile, "VIEWER");

        assertThat(call(HttpMethod.POST, "/api/v1/users", admin, userBody(mobile, "VIEWER")).getResponse().getStatus()).isEqualTo(409);

        Map<String, Object> withEmail = Map.of("fullName", "E", "mobile", newMobile(), "email", "Dup@Example.com",
                "password", PASSWORD, "roleCodes", List.of("VIEWER"));
        assertThat(call(HttpMethod.POST, "/api/v1/users", admin, withEmail).getResponse().getStatus()).isEqualTo(201);
        Map<String, Object> sameEmail = Map.of("fullName", "E2", "mobile", newMobile(), "email", "dup@example.com",
                "password", PASSWORD, "roleCodes", List.of("VIEWER"));
        assertThat(call(HttpMethod.POST, "/api/v1/users", admin, sameEmail).getResponse().getStatus()).isEqualTo(409);

        Map<String, Object> weak = Map.of("fullName", "W", "mobile", newMobile(), "password", "short", "roleCodes", List.of("VIEWER"));
        assertThat(call(HttpMethod.POST, "/api/v1/users", admin, weak).getResponse().getStatus()).isEqualTo(422);

        assertThat(call(HttpMethod.POST, "/api/v1/users", admin, userBody(newMobile(), "NO_SUCH_ROLE")).getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void updateChangesProfileAndRejectsTakenMobile() throws Exception {
        String admin = superToken();
        String m1 = newMobile();
        String m2 = newMobile();
        long id = createUser(admin, m1, "VIEWER");
        createUser(admin, m2, "VIEWER");

        String newMobile = newMobile();
        MvcResult ok = call(HttpMethod.PUT, "/api/v1/users/" + id, admin,
                Map.of("fullName", "Renamed", "mobile", newMobile, "email", "renamed" + id + "@example.com"));
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(tree(ok).get("fullName").asText()).isEqualTo("Renamed");
        assertThat(auditCount("USER_UPDATE", id)).isEqualTo(1);

        MvcResult clash = call(HttpMethod.PUT, "/api/v1/users/" + id, admin, Map.of("fullName", "X", "mobile", m2));
        assertThat(clash.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void deactivateRevokesRefreshTokensBlocksLoginAndActivateRestores() throws Exception {
        String admin = superToken();
        String mobile = newMobile();
        long id = createUser(admin, mobile, "VIEWER");
        login(mobile, PASSWORD); // creates a refresh token

        assertThat(call(HttpMethod.POST, "/api/v1/users/" + id + "/deactivate", admin, null).getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where user_id = ? and revoked_at is null", Long.class, id)).isZero();
        assertThat(auditCount("USER_DEACTIVATE", id)).isEqualTo(1);
        assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType("application/json").content("{\"mobile\":\"" + mobile + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(401);

        assertThat(call(HttpMethod.POST, "/api/v1/users/" + id + "/activate", admin, null).getResponse().getStatus()).isEqualTo(200);
        assertThat(login(mobile, PASSWORD)).isNotBlank();
        assertThat(auditCount("USER_ACTIVATE", id)).isEqualTo(1);
    }

    @Test
    void resetPasswordSetsTemporaryPasswordAndRevokesTokens() throws Exception {
        String admin = superToken();
        String mobile = newMobile();
        long id = createUser(admin, mobile, "VIEWER");
        login(mobile, PASSWORD);

        MvcResult r = call(HttpMethod.POST, "/api/v1/users/" + id + "/reset-password", admin, Map.of("password", "Another-Temp-99"));
        assertThat(r.getResponse().getStatus()).isEqualTo(204);
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where user_id = ? and revoked_at is null", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("select password_change_required from users where id = ?", Boolean.class, id)).isTrue();
        assertThat(login(mobile, "Another-Temp-99")).isNotBlank();
        assertThat(auditCount("USER_PASSWORD_RESET", id)).isEqualTo(1);
        assertThat(call(HttpMethod.POST, "/api/v1/users/" + id + "/reset-password", admin, Map.of("password", "short")).getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void listPaginatesSearchesAndFiltersByStatus() throws Exception {
        String admin = superToken();
        String tag = "Zeta" + System.nanoTime();
        for (int i = 0; i < 3; i++) {
            String m = newMobile();
            call(HttpMethod.POST, "/api/v1/users", admin,
                    Map.of("fullName", tag + " person" + i, "mobile", m, "password", PASSWORD, "roleCodes", List.of("VIEWER")));
        }
        MvcResult page1 = call(HttpMethod.GET, "/api/v1/users?search=" + tag.toLowerCase() + "&size=2&page=0", admin, null);
        JsonNode b1 = tree(page1);
        assertThat(b1.get("totalElements").asLong()).isEqualTo(3);
        assertThat(b1.get("content").size()).isEqualTo(2);
        assertThat(b1.get("size").asInt()).isEqualTo(2);
        assertThat(tree(call(HttpMethod.GET, "/api/v1/users?search=" + tag + "&size=2&page=1", admin, null)).get("content").size()).isEqualTo(1);

        long id = b1.get("content").get(0).get("id").asLong();
        call(HttpMethod.POST, "/api/v1/users/" + id + "/deactivate", admin, null);
        assertThat(tree(call(HttpMethod.GET, "/api/v1/users?search=" + tag + "&status=INACTIVE", admin, null)).get("totalElements").asLong()).isEqualTo(1);
        assertThat(tree(call(HttpMethod.GET, "/api/v1/users?search=" + tag + "&status=ACTIVE", admin, null)).get("totalElements").asLong()).isEqualTo(2);

        String mobile = jdbc.queryForObject("select mobile from users where id = ?", String.class, id);
        assertThat(tree(call(HttpMethod.GET, "/api/v1/users?search=" + mobile, admin, null)).get("totalElements").asLong()).isEqualTo(1);
        assertThat(tree(call(HttpMethod.GET, "/api/v1/users?search=%25", admin, null)).get("totalElements").asLong()).isZero(); // wildcard is escaped
        assertThat(call(HttpMethod.GET, "/api/v1/users?size=1000", admin, null).getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void getReturnsUserWithoutPasswordHash() throws Exception {
        String admin = superToken();
        long id = createUser(admin, newMobile(), "VIEWER");
        MvcResult r = call(HttpMethod.GET, "/api/v1/users/" + id, admin, null);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(r.getResponse().getContentAsString()).doesNotContain("assword_hash").doesNotContain("passwordHash");
        assertThat(call(HttpMethod.GET, "/api/v1/users/999999999", admin, null).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void staffWithoutPermissionGets403OnEveryManagementEndpoint() throws Exception {
        String admin = superToken();
        String mobile = newMobile();
        long id = createUser(admin, mobile, "PURCHASE_STAFF");
        String staff = login(mobile, PASSWORD);

        for (String[] e : new String[][] {
                {"GET", "/api/v1/users"}, {"GET", "/api/v1/users/" + id}, {"POST", "/api/v1/users"},
                {"PUT", "/api/v1/users/" + id}, {"POST", "/api/v1/users/" + id + "/deactivate"},
                {"POST", "/api/v1/users/" + id + "/activate"}, {"POST", "/api/v1/users/" + id + "/reset-password"},
                {"PUT", "/api/v1/users/" + id + "/roles"}, {"PUT", "/api/v1/users/" + id + "/permissions"},
                {"GET", "/api/v1/users/" + id + "/effective-permissions"}, {"GET", "/api/v1/roles"},
                {"POST", "/api/v1/roles"}, {"PUT", "/api/v1/roles/1"}, {"GET", "/api/v1/permissions"}}) {
            Object body = bodyFor(e[0], e[1]);
            assertThat(call(HttpMethod.valueOf(e[0]), e[1], staff, body).getResponse().getStatus()).as(e[0] + " " + e[1]).isEqualTo(403);
        }
    }

    /** Valid bodies, because request validation runs before the method-security check. */
    private Object bodyFor(String method, String url) {
        if (method.equals("GET")) {
            return null;
        }
        if (url.endsWith("/roles") && url.contains("/users/")) {
            return Map.of("roleCodes", List.of("VIEWER"));
        }
        if (url.endsWith("/permissions")) {
            return List.of();
        }
        if (url.endsWith("/reset-password")) {
            return Map.of("password", PASSWORD);
        }
        if (url.contains("/roles")) {
            return Map.of("roleCode", "SOME_ROLE", "roleName", "x", "permissionCodes", List.of());
        }
        if (url.endsWith("/deactivate") || url.endsWith("/activate")) {
            return null;
        }
        return userBody(newMobile(), "VIEWER");
    }

    @Test
    void effectivePermissionsFollowRoleChangesAndOverrides() throws Exception {
        String admin = superToken();
        long id = createUser(admin, newMobile(), "VIEWER");
        String url = "/api/v1/users/" + id + "/effective-permissions";
        List<String> viewer = strings(tree(call(HttpMethod.GET, url, admin, null)).get("permissions"));
        assertThat(viewer).hasSize(5).contains("MATERIAL_IN.VIEW").doesNotContain("MATERIAL_IN.CREATE");

        call(HttpMethod.PUT, "/api/v1/users/" + id + "/roles", admin, Map.of("roleCodes", List.of("PURCHASE_STAFF")));
        List<String> purchase = strings(tree(call(HttpMethod.GET, url, admin, null)).get("permissions"));
        assertThat(purchase).containsExactlyInAnyOrder("DASHBOARD.VIEW", "PRODUCT.VIEW", "INVENTORY.VIEW", "PARTY.VIEW",
                "PARTY.CREATE", "MATERIAL_IN.VIEW", "MATERIAL_IN.CREATE");

        MvcResult set = call(HttpMethod.PUT, "/api/v1/users/" + id + "/permissions", admin, List.of(
                Map.of("permissionCode", "MATERIAL_IN.CREATE", "effect", "DENY"),
                Map.of("permissionCode", "REPORT.VIEW", "effect", "ALLOW")));
        assertThat(set.getResponse().getStatus()).isEqualTo(200);
        assertThat(tree(set).get("permissionOverrides").size()).isEqualTo(2);
        List<String> effective = strings(tree(call(HttpMethod.GET, url, admin, null)).get("permissions"));
        assertThat(effective).contains("REPORT.VIEW", "MATERIAL_IN.VIEW").doesNotContain("MATERIAL_IN.CREATE");
        assertThat(auditCount("USER_ROLES_ASSIGN", id)).isEqualTo(1);
        assertThat(auditCount("USER_PERMISSIONS_UPDATE", id)).isEqualTo(1);

        // replacing with an empty list clears all overrides
        call(HttpMethod.PUT, "/api/v1/users/" + id + "/permissions", admin, List.of());
        assertThat(strings(tree(call(HttpMethod.GET, url, admin, null)).get("permissions"))).containsExactlyInAnyOrderElementsOf(purchase);
    }

    @Test
    void unknownPermissionCodeInOverridesIs422AndDuplicatesAreRejected() throws Exception {
        String admin = superToken();
        long id = createUser(admin, newMobile(), "VIEWER");
        assertThat(call(HttpMethod.PUT, "/api/v1/users/" + id + "/permissions", admin,
                List.of(Map.of("permissionCode", "NOPE.NOPE", "effect", "ALLOW"))).getResponse().getStatus()).isEqualTo(422);
        assertThat(call(HttpMethod.PUT, "/api/v1/users/" + id + "/permissions", admin, List.of(
                Map.of("permissionCode", "REPORT.VIEW", "effect", "ALLOW"),
                Map.of("permissionCode", "REPORT.VIEW", "effect", "DENY"))).getResponse().getStatus()).isEqualTo(422);
        assertThat(call(HttpMethod.PUT, "/api/v1/users/" + id + "/permissions", admin,
                List.of(Map.of("permissionCode", "REPORT.VIEW"))).getResponse().getStatus()).isEqualTo(422);
    }
}
