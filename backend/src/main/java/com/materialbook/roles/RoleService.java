package com.materialbook.roles;

import com.materialbook.audit.service.AuditService;
import com.materialbook.common.ApiException.ConflictException;
import com.materialbook.common.ApiException.NotFoundException;
import com.materialbook.common.ApiException.ValidationException;
import com.materialbook.permissions.Permission;
import com.materialbook.permissions.PermissionService;
import com.materialbook.roles.RoleDtos.CreateRoleRequest;
import com.materialbook.roles.RoleDtos.RoleResponse;
import com.materialbook.roles.RoleDtos.UpdateRoleRequest;
import com.materialbook.users.Actor;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final String MODULE = "ROLE";

    private final RoleRepository roles;
    private final PermissionService permissions;
    private final AuditService audit;

    public RoleService(RoleRepository roles, PermissionService permissions, AuditService audit) {
        this.roles = roles;
        this.permissions = permissions;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roles.findAllByOrderByRoleCodeAsc().stream().map(RoleService::toResponse).toList();
    }

    /** Resolves codes to roles; any unknown code is a 422. */
    @Transactional(readOnly = true)
    public Set<Role> requireByCodes(Set<String> codes) {
        Set<Role> found = new HashSet<>(roles.findByRoleCodeIn(codes));
        Set<String> foundCodes = found.stream().map(Role::getRoleCode).collect(Collectors.toSet());
        List<String> unknown = codes.stream().filter(c -> !foundCodes.contains(c)).sorted().toList();
        if (!unknown.isEmpty()) {
            throw new ValidationException("Unknown role code(s): " + String.join(", ", unknown));
        }
        return found;
    }

    @Transactional
    public RoleResponse create(Actor actor, CreateRoleRequest request) {
        if (roles.existsByRoleCode(request.roleCode())) {
            throw new ConflictException("Role code already exists: " + request.roleCode());
        }
        Set<Permission> granted = new HashSet<>(permissions.requireByCodes(request.permissionCodes()).values());
        actor.requireCanGrant(request.permissionCodes());
        Role role = roles.save(new Role(request.roleCode(), request.roleName().trim(), blankToNull(request.description()), granted));
        RoleResponse response = toResponse(role);
        audit.log("ROLE_CREATE", MODULE, role.getId(), null, response);
        return response;
    }

    @Transactional
    public RoleResponse update(Actor actor, Long id, UpdateRoleRequest request) {
        Role role = roles.findById(id).orElseThrow(() -> new NotFoundException("Role not found"));
        if (SUPER_ADMIN.equals(role.getRoleCode())) {
            throw Actor.forbidden("The SUPER_ADMIN role cannot be modified");
        }
        RoleResponse before = toResponse(role);
        Set<String> current = role.permissionCodes();
        if (!current.equals(request.permissionCodes())) {
            if (role.isSystem() && !actor.superAdmin()) {
                throw Actor.forbidden("Only a SUPER_ADMIN can change the permissions of a system role");
            }
            Set<Permission> granted = new HashSet<>(permissions.requireByCodes(request.permissionCodes()).values());
            Set<String> added = new HashSet<>(request.permissionCodes());
            added.removeAll(current);
            actor.requireCanGrant(added);
            role.replacePermissions(granted);
        }
        role.update(request.roleName().trim(), blankToNull(request.description()));
        RoleResponse after = toResponse(role);
        audit.log("ROLE_UPDATE", MODULE, role.getId(), before, after);
        return after;
    }

    private static RoleResponse toResponse(Role r) {
        return new RoleResponse(r.getId(), r.getRoleCode(), r.getRoleName(), r.getDescription(), r.isSystem(),
                r.permissionCodes().stream().sorted().toList());
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
