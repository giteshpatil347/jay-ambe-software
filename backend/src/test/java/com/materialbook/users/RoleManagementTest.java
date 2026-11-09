package com.materialbook.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

class RoleManagementTest extends AbstractUserApiTest {
    private JsonNode roleByCode(String token, String code) throws Exception {
        for (JsonNode r : tree(call(HttpMethod.GET, "/api/v1/roles", token, null))) {
            if (r.get("roleCode").asText().equals(code)) {
                return r;
            }
        }
        throw new AssertionError("role not listed: " + code);
    }

    @Test
    void listIncludesPermissionCodesAndPermissionsAreGroupedByModule() throws Exception {
        String root = superToken();
        assertThat(roleByCode(root, "VIEWER").get("permissionCodes")).hasSize(5);
        assertThat(roleByCode(root, "VIEWER").get("system").asBoolean()).isTrue();

        JsonNode groups = tree(call(HttpMethod.GET, "/api/v1/permissions", root, null));
        int total = 0;
        for (JsonNode g : groups) {
            assertThat(g.get("moduleCode").asText()).isNotBlank();
            total += g.get("permissions").size();
        }
        assertThat(total).isEqualTo(48);
        assertThat(groups.get(0).get("permissions").get(0).has("permissionCode")).isTrue();
    }

    @Test
    void createAndUpdateCustomRoleAreAudited() throws Exception {
        String root = superToken();
        String code = "CUSTOM_" + System.nanoTime();
        MvcResult created = call(HttpMethod.POST, "/api/v1/roles", root,
                Map.of("roleCode", code, "roleName", "Custom", "permissionCodes", List.of("REPORT.VIEW")));
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        long id = tree(created).get("id").asLong();
        assertThat(tree(created).get("system").asBoolean()).isFalse();
        assertThat(auditCount("ROLE_CREATE", id)).isEqualTo(1);

        assertThat(call(HttpMethod.POST, "/api/v1/roles", root,
                Map.of("roleCode", code, "roleName", "Dup", "permissionCodes", List.of())).getResponse().getStatus()).isEqualTo(409);

        MvcResult updated = call(HttpMethod.PUT, "/api/v1/roles/" + id, root, Map.of("roleName", "Renamed",
                "description", "d", "permissionCodes", List.of("REPORT.VIEW", "PRODUCT.VIEW")));
        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat(tree(updated).get("permissionCodes")).hasSize(2);
        assertThat(auditCount("ROLE_UPDATE", id)).isEqualTo(1);
    }

    @Test
    void superAdminRoleIsNeverEditable() throws Exception {
        String root = superToken();
        long id = roleByCode(root, "SUPER_ADMIN").get("id").asLong();
        assertThat(call(HttpMethod.PUT, "/api/v1/roles/" + id, root,
                Map.of("roleName", "Hax", "permissionCodes", List.of("USER.VIEW"))).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void systemRolePermissionsAreImmutableExceptBySuperAdmin() throws Exception {
        String root = superToken();
        String admin = login(createdMobile(root), PASSWORD);
        JsonNode viewer = roleByCode(root, "VIEWER");
        long id = viewer.get("id").asLong();
        List<String> original = strings(viewer.get("permissionCodes"));

        Map<String, Object> changed = Map.of("roleName", "Viewer", "permissionCodes", List.of("MATERIAL_IN.VIEW"));
        assertThat(call(HttpMethod.PUT, "/api/v1/roles/" + id, admin, changed).getResponse().getStatus()).isEqualTo(403);
        // name/description only (same codes) is allowed for an admin
        assertThat(call(HttpMethod.PUT, "/api/v1/roles/" + id, admin,
                Map.of("roleName", "Viewer", "description", "read only", "permissionCodes", original)).getResponse().getStatus()).isEqualTo(200);
        // the super admin may change codes; restore afterwards for other tests
        assertThat(call(HttpMethod.PUT, "/api/v1/roles/" + id, root, changed).getResponse().getStatus()).isEqualTo(200);
        assertThat(call(HttpMethod.PUT, "/api/v1/roles/" + id, root,
                Map.of("roleName", "Viewer", "permissionCodes", original)).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void adminCannotCreateOrExtendRolesBeyondTheirOwnPermissions() throws Exception {
        String root = superToken();
        String admin = login(createdMobile(root), PASSWORD);
        String code = "ESC_" + System.nanoTime();
        assertThat(call(HttpMethod.POST, "/api/v1/roles", admin, Map.of("roleCode", code, "roleName", "Esc",
                "permissionCodes", List.of("SETTINGS.MANAGE"))).getResponse().getStatus()).isEqualTo(403);

        long id = tree(call(HttpMethod.POST, "/api/v1/roles", admin, Map.of("roleCode", code, "roleName", "Ok",
                "permissionCodes", List.of("REPORT.VIEW")))).get("id").asLong();
        assertThat(call(HttpMethod.PUT, "/api/v1/roles/" + id, admin, Map.of("roleName", "Ok",
                "permissionCodes", List.of("REPORT.VIEW", "SETTINGS.MANAGE"))).getResponse().getStatus()).isEqualTo(403);
    }

    private String createdMobile(String root) throws Exception {
        String mobile = newMobile();
        createUser(root, mobile, "ADMIN");
        return mobile;
    }
}
