package com.materialbook.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.materialbook.common.ApiException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

class UserSafetyRulesTest extends AbstractUserApiTest {
    @Autowired UserService userService;

    private int roles(String token, long id, String... codes) throws Exception {
        return call(HttpMethod.PUT, "/api/v1/users/" + id + "/roles", token, Map.of("roleCodes", List.of(codes))).getResponse().getStatus();
    }

    private String tokenOf(String actorToken, String role, long[] idOut) throws Exception {
        String mobile = newMobile();
        idOut[0] = createUser(actorToken, mobile, role);
        return login(mobile, PASSWORD);
    }

    @Test
    void onlySuperAdminCanCreateAssignOrRemoveSuperAdminRole() throws Exception {
        String root = superToken();
        long[] adminId = new long[1];
        String admin = tokenOf(root, "ADMIN", adminId);
        long viewer = createUser(root, newMobile(), "VIEWER");

        assertThat(call(HttpMethod.POST, "/api/v1/users", admin, userBody(newMobile(), "SUPER_ADMIN")).getResponse().getStatus()).isEqualTo(403);
        assertThat(roles(admin, viewer, "SUPER_ADMIN")).isEqualTo(403);

        long superB = createUser(root, newMobile(), "SUPER_ADMIN"); // a SUPER_ADMIN can
        assertThat(roles(admin, superB, "VIEWER")).isEqualTo(403);  // admin cannot remove it
        assertThat(roles(root, viewer, "SUPER_ADMIN")).isEqualTo(200);
        assertThat(roles(root, viewer, "VIEWER")).isEqualTo(200);   // back, root may remove it (another super admin remains)
    }

    @Test
    void adminCannotModifyDeactivateResetOrOverrideASuperAdminUser() throws Exception {
        String root = superToken();
        String admin = tokenOf(root, "ADMIN", new long[1]);
        long superB = createUser(root, newMobile(), "SUPER_ADMIN");
        String base = "/api/v1/users/" + superB;

        assertThat(call(HttpMethod.PUT, base, admin, Map.of("fullName", "Hacked", "mobile", newMobile())).getResponse().getStatus()).isEqualTo(403);
        assertThat(call(HttpMethod.POST, base + "/deactivate", admin, null).getResponse().getStatus()).isEqualTo(403);
        assertThat(call(HttpMethod.POST, base + "/reset-password", admin, Map.of("password", "Hacked-Pass-123")).getResponse().getStatus()).isEqualTo(403);
        assertThat(call(HttpMethod.PUT, base + "/permissions", admin,
                List.of(Map.of("permissionCode", "REPORT.VIEW", "effect", "DENY"))).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void nobodyCanRemoveOwnAdminAccessOrDeactivateThemselves() throws Exception {
        String root = superToken();
        long[] adminId = new long[1];
        String admin = tokenOf(root, "ADMIN", adminId);
        // give the admin a second role so that the role list is valid either way
        assertThat(roles(admin, adminId[0], "VIEWER")).isEqualTo(409); // drops ADMIN from self
        assertThat(roles(admin, adminId[0], "ADMIN", "VIEWER")).isEqualTo(200); // keeping ADMIN is fine
        assertThat(call(HttpMethod.POST, "/api/v1/users/" + adminId[0] + "/deactivate", admin, null).getResponse().getStatus()).isEqualTo(409);
        assertThat(call(HttpMethod.PUT, "/api/v1/users/" + adminId[0] + "/permissions", admin,
                List.of(Map.of("permissionCode", "USER.VIEW", "effect", "DENY"))).getResponse().getStatus()).isEqualTo(409);

        // a SUPER_ADMIN cannot de-role or deactivate themselves either
        long superId = createUser(root, newMobile(), "SUPER_ADMIN");
        String mobile = jdbc.queryForObject("select mobile from users where id = ?", String.class, superId);
        String superB = login(mobile, PASSWORD);
        assertThat(roles(superB, superId, "ADMIN")).isEqualTo(409);
        assertThat(call(HttpMethod.POST, "/api/v1/users/" + superId + "/deactivate", superB, null).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void lastActiveSuperAdminCannotBeDeactivatedOrDeRoled() throws Exception {
        String root = superToken();
        long other = createUser(root, newMobile(), "SUPER_ADMIN");
        List<Long> supers = jdbc.queryForList("""
                select u.id from users u join user_roles ur on ur.user_id = u.id join roles r on r.id = ur.role_id
                where r.role_code = 'SUPER_ADMIN' and u.status = 'ACTIVE'""", Long.class);
        long keep = supers.get(0);
        List<Long> deactivated = supers.stream().filter(id -> id != keep).toList();
        deactivated.forEach(id -> jdbc.update("update users set status = 'INACTIVE' where id = ?", id));
        try {
            Actor someSuperAdmin = new Actor(-1L, true, Set.of());
            assertThatThrownBy(() -> userService.deactivate(someSuperAdmin, keep))
                    .isInstanceOf(ApiException.class).hasMessageContaining("last active SUPER_ADMIN");
            assertThatThrownBy(() -> userService.assignRoles(someSuperAdmin, keep, Set.of("VIEWER")))
                    .isInstanceOf(ApiException.class).hasMessageContaining("last active SUPER_ADMIN");
            assertThat(userService.get(keep).status()).isEqualTo(UserStatus.ACTIVE);
        } finally {
            deactivated.forEach(id -> jdbc.update("update users set status = 'ACTIVE' where id = ?", id));
        }
        assertThat(other).isPositive();
    }

    @Test
    void adminCannotGrantPermissionsTheyDoNotHoldViaRolesOrOverrides() throws Exception {
        String root = superToken();
        long[] adminId = new long[1];
        String admin = tokenOf(root, "ADMIN", adminId); // ADMIN lacks SETTINGS.MANAGE
        long target = createUser(root, newMobile(), "VIEWER");

        // a custom role carrying SETTINGS.MANAGE, created by the super admin
        assertThat(call(HttpMethod.POST, "/api/v1/roles", root, Map.of("roleCode", "SETTINGS_ROLE" + target, "roleName", "Settings",
                "permissionCodes", List.of("SETTINGS.MANAGE"))).getResponse().getStatus()).isEqualTo(201);
        assertThat(roles(admin, target, "VIEWER", "SETTINGS_ROLE" + target)).isEqualTo(403);
        assertThat(call(HttpMethod.POST, "/api/v1/users", admin, userBody(newMobile(), "SETTINGS_ROLE" + target)).getResponse().getStatus()).isEqualTo(403);

        assertThat(call(HttpMethod.PUT, "/api/v1/users/" + target + "/permissions", admin,
                List.of(Map.of("permissionCode", "SETTINGS.MANAGE", "effect", "ALLOW"))).getResponse().getStatus()).isEqualTo(403);
        // the same override is fine for the super admin, and DENY of anything is fine for the admin
        assertThat(call(HttpMethod.PUT, "/api/v1/users/" + target + "/permissions", root,
                List.of(Map.of("permissionCode", "SETTINGS.MANAGE", "effect", "ALLOW"))).getResponse().getStatus()).isEqualTo(200);
        assertThat(call(HttpMethod.PUT, "/api/v1/users/" + target + "/permissions", admin,
                List.of(Map.of("permissionCode", "SETTINGS.MANAGE", "effect", "ALLOW"),
                        Map.of("permissionCode", "REPORT.VIEW", "effect", "DENY"))).getResponse().getStatus()).isEqualTo(200); // unchanged ALLOW is not a new grant
    }

    @Test
    void unknownRoleCodesAre422() throws Exception {
        String root = superToken();
        long id = createUser(root, newMobile(), "VIEWER");
        assertThat(roles(root, id, "VIEWER", "GHOST_ROLE")).isEqualTo(422);
        assertThat(call(HttpMethod.POST, "/api/v1/roles", root, Map.of("roleCode", "BAD_PERMS" + id, "roleName", "x",
                "permissionCodes", List.of("GHOST.PERM"))).getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void deactivatedActorsTokenIsNoLongerHonouredForManagement() throws Exception {
        String root = superToken();
        long[] adminId = new long[1];
        String admin = tokenOf(root, "ADMIN", adminId);
        call(HttpMethod.POST, "/api/v1/users/" + adminId[0] + "/deactivate", root, null);
        assertThat(call(HttpMethod.POST, "/api/v1/users", admin, userBody(newMobile(), "VIEWER")).getResponse().getStatus()).isEqualTo(403);
    }
}
