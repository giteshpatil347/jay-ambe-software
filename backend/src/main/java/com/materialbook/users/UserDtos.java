package com.materialbook.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class UserDtos {
    private UserDtos() {}

    public static final String MOBILE_PATTERN = "^[0-9]{10,15}$";

    public record PermissionOverrideDto(@NotBlank String permissionCode, @NotNull OverrideEffect effect) {}

    public record UserResponse(Long id, String fullName, String mobile, String email, UserStatus status,
                               boolean passwordChangeRequired, Instant lastLoginAt, Instant createdAt,
                               List<String> roles, List<PermissionOverrideDto> permissionOverrides) {}

    public record EffectivePermissionsResponse(Long userId, List<String> permissions) {}

    public record CreateUserRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Pattern(regexp = MOBILE_PATTERN, message = "must be 10-15 digits") String mobile,
            @Email @Size(max = 150) String email,
            @NotBlank @Size(min = 10, max = 72) String password,
            @NotEmpty Set<String> roleCodes) {}

    public record UpdateUserRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Pattern(regexp = MOBILE_PATTERN, message = "must be 10-15 digits") String mobile,
            @Email @Size(max = 150) String email) {}

    public record ResetPasswordRequest(@NotBlank @Size(min = 10, max = 72) String password) {}

    public record AssignRolesRequest(@NotEmpty Set<String> roleCodes) {}
}
