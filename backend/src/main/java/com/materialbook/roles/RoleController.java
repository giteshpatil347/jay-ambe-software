package com.materialbook.roles;

import com.materialbook.roles.RoleDtos.CreateRoleRequest;
import com.materialbook.roles.RoleDtos.RoleResponse;
import com.materialbook.roles.RoleDtos.UpdateRoleRequest;
import com.materialbook.users.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {
    private final RoleService roles;
    private final UserService users;

    public RoleController(RoleService roles, UserService users) {
        this.roles = roles;
        this.users = users;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE.VIEW')")
    List<RoleResponse> list() {
        return roles.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE.CREATE')")
    RoleResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRoleRequest request) {
        return roles.create(users.actor(Long.valueOf(jwt.getSubject())), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE.UPDATE')")
    RoleResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return roles.update(users.actor(Long.valueOf(jwt.getSubject())), id, request);
    }
}
