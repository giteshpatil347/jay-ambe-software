package com.materialbook.roles;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;

public final class RoleDtos {
    private RoleDtos() {}

    public record RoleResponse(Long id, String roleCode, String roleName, String description, boolean system,
                               List<String> permissionCodes) {}

    public record CreateRoleRequest(
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$", message = "must be UPPER_SNAKE_CASE") String roleCode,
            @NotBlank @Size(max = 100) String roleName,
            @Size(max = 1000) String description,
            @NotNull Set<String> permissionCodes) {}

    public record UpdateRoleRequest(
            @NotBlank @Size(max = 100) String roleName,
            @Size(max = 1000) String description,
            @NotNull Set<String> permissionCodes) {}
}
