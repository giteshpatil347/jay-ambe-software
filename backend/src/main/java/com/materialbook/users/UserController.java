package com.materialbook.users;

import com.materialbook.common.PageResponse;
import com.materialbook.users.UserDtos.AssignRolesRequest;
import com.materialbook.users.UserDtos.CreateUserRequest;
import com.materialbook.users.UserDtos.EffectivePermissionsResponse;
import com.materialbook.users.UserDtos.PermissionOverrideDto;
import com.materialbook.users.UserDtos.ResetPasswordRequest;
import com.materialbook.users.UserDtos.UpdateUserRequest;
import com.materialbook.users.UserDtos.UserResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER.VIEW')")
    PageResponse<UserResponse> list(@RequestParam(required = false) String search,
                                    @RequestParam(required = false) UserStatus status,
                                    @RequestParam(defaultValue = "0") @Min(0) int page,
                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return users.list(search, status, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER.VIEW')")
    UserResponse get(@PathVariable Long id) {
        return users.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER.CREATE')")
    UserResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateUserRequest request) {
        return users.createUser(actor(jwt), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER.UPDATE')")
    UserResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return users.update(actor(jwt), id, request);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('USER.DEACTIVATE')")
    UserResponse deactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return users.deactivate(actor(jwt), id);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('USER.DEACTIVATE')")
    UserResponse activate(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return users.activate(actor(jwt), id);
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('USER.UPDATE')")
    void resetPassword(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        users.resetPassword(actor(jwt), id, request.password());
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('ROLE.ASSIGN')")
    UserResponse assignRoles(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody AssignRolesRequest request) {
        return users.assignRoles(actor(jwt), id, request.roleCodes());
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('PERMISSION.MANAGE')")
    UserResponse setPermissionOverrides(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                        @RequestBody @Valid List<@Valid PermissionOverrideDto> overrides) {
        return users.setPermissionOverrides(actor(jwt), id, overrides);
    }

    @GetMapping("/{id}/effective-permissions")
    @PreAuthorize("hasAuthority('PERMISSION.VIEW')")
    EffectivePermissionsResponse effectivePermissions(@PathVariable Long id) {
        return users.effectivePermissionsOf(id);
    }

    private Actor actor(Jwt jwt) {
        return users.actor(Long.valueOf(jwt.getSubject()));
    }
}
