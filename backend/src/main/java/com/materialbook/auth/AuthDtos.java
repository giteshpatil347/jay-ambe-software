package com.materialbook.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(@NotBlank @Size(max = 64) String mobile, @NotBlank @Size(max = 128) String password) {}

    public record ChangePasswordRequest(@NotBlank @Size(max = 128) String currentPassword, @NotBlank @Size(min = 10, max = 72) String newPassword) {}

    public record UserInfo(Long id, String fullName, List<String> permissions, boolean passwordChangeRequired) {}

    public record TokenResponse(String accessToken, long expiresIn, UserInfo user) {}
}
