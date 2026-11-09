package com.materialbook.users;

import com.materialbook.audit.service.AuditService;
import com.materialbook.auth.RefreshTokenRepository;
import com.materialbook.common.ApiException.ConflictException;
import com.materialbook.common.ApiException.NotFoundException;
import com.materialbook.common.ApiException.ValidationException;
import com.materialbook.common.PageResponse;
import com.materialbook.permissions.Permission;
import com.materialbook.permissions.PermissionService;
import com.materialbook.roles.Role;
import com.materialbook.roles.RoleService;
import com.materialbook.users.UserDtos.CreateUserRequest;
import com.materialbook.users.UserDtos.EffectivePermissionsResponse;
import com.materialbook.users.UserDtos.PermissionOverrideDto;
import com.materialbook.users.UserDtos.UpdateUserRequest;
import com.materialbook.users.UserDtos.UserResponse;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User lookups for auth plus the user-management use cases, including the Super Admin safety rules. */
@Service
public class UserService {
    private static final String SUPER_ADMIN = RoleService.SUPER_ADMIN;
    private static final String ADMIN = "ADMIN";
    private static final String MODULE = "USER";

    private final UserRepository users;
    private final UserPermissionOverrideRepository overrides;
    private final RoleService roles;
    private final PermissionService permissions;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserService(UserRepository users, UserPermissionOverrideRepository overrides, RoleService roles,
                       PermissionService permissions, RefreshTokenRepository refreshTokens, PasswordEncoder encoder,
                       AuditService audit) {
        this.users = users;
        this.overrides = overrides;
        this.roles = roles;
        this.permissions = permissions;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.audit = audit;
    }

    // ---- lookups used by auth -------------------------------------------------------------

    @Transactional
    public Optional<User> findByMobileForLogin(String mobile) {
        return users.findByMobileForUpdate(mobile);
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

    /** Sorted, fresh-from-DB effective permission codes. */
    @Transactional(readOnly = true)
    public Set<String> effectivePermissions(Long userId) {
        Set<String> allowed = new HashSet<>();
        Set<String> denied = new HashSet<>();
        for (UserPermissionOverride o : overrides.findByUserId(userId)) {
            (o.getEffect() == OverrideEffect.ALLOW ? allowed : denied).add(o.getPermission().getPermissionCode());
        }
        return new TreeSet<>(EffectivePermissionResolver.resolve(users.findRolePermissionCodes(userId), allowed, denied));
    }

    @Transactional(readOnly = true)
    public boolean existsByMobile(String mobile) { return users.existsByMobile(mobile); }

    @Transactional(readOnly = true)
    public boolean existsWithRole(String roleCode) { return users.existsWithRole(roleCode); }

    /** Used by the bootstrap initializer; the caller supplies an already-hashed password. */
    @Transactional
    public User create(String fullName, String mobile, String passwordHash, boolean passwordChangeRequired, String roleCode) {
        User user = new User(fullName, mobile, passwordHash, passwordChangeRequired);
        user.replaceRoles(roles.requireByCodes(Set.of(roleCode)));
        return users.save(user);
    }

    // ---- management API -------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Actor actor(Long userId) {
        User user = getById(userId);
        if (!user.isActive()) {
            throw Actor.forbidden("Account is deactivated");
        }
        return new Actor(userId, user.hasRole(SUPER_ADMIN), effectivePermissions(userId));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String search, UserStatus status, int page, int size) {
        Page<User> result = users.search(likePattern(search), status,
                PageRequest.of(page, size, Sort.by("fullName", "id")));
        Map<Long, List<PermissionOverrideDto>> byUser = overridesByUser(result.getContent().stream().map(User::getId).toList());
        return PageResponse.of(result, u -> toResponse(u, byUser.getOrDefault(u.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return toResponse(getById(id));
    }

    @Transactional
    public UserResponse createUser(Actor actor, CreateUserRequest request) {
        String mobile = request.mobile().trim();
        String email = blankToNull(request.email());
        requireUnique(mobile, email, null);
        Set<Role> assigned = roles.requireByCodes(request.roleCodes());
        requireCanAssign(actor, assigned);
        User user = new User(request.fullName().trim(), mobile, encoder.encode(request.password()), true);
        user.updateProfile(user.getFullName(), mobile, email);
        user.replaceRoles(assigned);
        user = users.save(user);
        UserResponse response = toResponse(user);
        audit.log("USER_CREATE", MODULE, user.getId(), null, response);
        return response;
    }

    @Transactional
    public UserResponse update(Actor actor, Long id, UpdateUserRequest request) {
        User user = lock(id);
        requireCanModify(actor, user);
        UserResponse before = toResponse(user);
        String mobile = request.mobile().trim();
        String email = blankToNull(request.email());
        requireUnique(mobile, email, id);
        user.updateProfile(request.fullName().trim(), mobile, email);
        UserResponse after = toResponse(user);
        audit.log("USER_UPDATE", MODULE, id, before, after);
        return after;
    }

    @Transactional
    public UserResponse deactivate(Actor actor, Long id) {
        User user = lock(id);
        requireCanModify(actor, user);
        if (actor.id().equals(id)) {
            throw new ConflictException("You cannot deactivate your own account");
        }
        if (user.isActive() && user.hasRole(SUPER_ADMIN)) {
            requireNotLastSuperAdmin("deactivate");
        }
        UserResponse before = toResponse(user);
        user.setStatus(UserStatus.INACTIVE);
        refreshTokens.revokeAllForUser(id, Instant.now());
        UserResponse after = toResponse(user);
        audit.log("USER_DEACTIVATE", MODULE, id, before, after);
        return after;
    }

    @Transactional
    public UserResponse activate(Actor actor, Long id) {
        User user = lock(id);
        requireCanModify(actor, user);
        UserResponse before = toResponse(user);
        user.setStatus(UserStatus.ACTIVE);
        UserResponse after = toResponse(user);
        audit.log("USER_ACTIVATE", MODULE, id, before, after);
        return after;
    }

    @Transactional
    public void resetPassword(Actor actor, Long id, String temporaryPassword) {
        User user = lock(id);
        requireCanModify(actor, user);
        user.resetPassword(encoder.encode(temporaryPassword));
        refreshTokens.revokeAllForUser(id, Instant.now());
        audit.log("USER_PASSWORD_RESET", MODULE, id, null, null);
    }

    @Transactional
    public UserResponse assignRoles(Actor actor, Long id, Set<String> roleCodes) {
        User user = lock(id);
        requireCanModify(actor, user);
        Set<Role> newRoles = roles.requireByCodes(roleCodes);
        Set<String> before = roleCodes(user.getRoles());
        Set<String> after = roleCodes(newRoles);
        Set<String> added = new HashSet<>(after);
        added.removeAll(before);
        Set<String> removed = new HashSet<>(before);
        removed.removeAll(after);

        if ((added.contains(SUPER_ADMIN) || removed.contains(SUPER_ADMIN)) && !actor.superAdmin()) {
            throw Actor.forbidden("Only a SUPER_ADMIN can assign or remove the SUPER_ADMIN role");
        }
        if (actor.id().equals(id) && (removed.contains(SUPER_ADMIN) || removed.contains(ADMIN))) {
            throw new ConflictException("You cannot remove your own administrator access");
        }
        if (removed.contains(SUPER_ADMIN) && user.isActive()) {
            requireNotLastSuperAdmin("remove the SUPER_ADMIN role from");
        }
        requireCanAssign(actor, newRoles.stream().filter(r -> added.contains(r.getRoleCode())).collect(Collectors.toSet()));

        user.replaceRoles(newRoles);
        audit.log("USER_ROLES_ASSIGN", MODULE, id, Map.of("roles", new TreeSet<>(before)), Map.of("roles", new TreeSet<>(after)));
        return toResponse(user);
    }

    @Transactional
    public UserResponse setPermissionOverrides(Actor actor, Long id, List<PermissionOverrideDto> requested) {
        User user = lock(id);
        requireCanModify(actor, user);
        if (actor.id().equals(id)) {
            throw new ConflictException("You cannot change your own permission overrides");
        }
        Map<String, OverrideEffect> wanted = new HashMap<>();
        for (PermissionOverrideDto dto : requested) {
            if (wanted.put(dto.permissionCode(), dto.effect()) != null) {
                throw new ValidationException("Duplicate permission code: " + dto.permissionCode());
            }
        }
        Map<String, Permission> catalogue = permissions.requireByCodes(wanted.keySet());

        List<UserPermissionOverride> existing = overrides.findByUserId(id);
        List<PermissionOverrideDto> before = toDtos(existing);
        Map<String, UserPermissionOverride> existingByCode = existing.stream()
                .collect(Collectors.toMap(o -> o.getPermission().getPermissionCode(), o -> o));

        Set<String> newOrChangedAllows = wanted.entrySet().stream()
                .filter(e -> e.getValue() == OverrideEffect.ALLOW)
                .filter(e -> !existingByCode.containsKey(e.getKey()) || existingByCode.get(e.getKey()).getEffect() != OverrideEffect.ALLOW)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        actor.requireCanGrant(newOrChangedAllows);

        overrides.deleteAll(existing.stream().filter(o -> !wanted.containsKey(o.getPermission().getPermissionCode())).toList());
        for (Map.Entry<String, OverrideEffect> e : wanted.entrySet()) {
            UserPermissionOverride current = existingByCode.get(e.getKey());
            if (current == null) {
                overrides.save(new UserPermissionOverride(id, catalogue.get(e.getKey()), e.getValue()));
            } else {
                current.setEffect(e.getValue());
            }
        }
        List<PermissionOverrideDto> after = wanted.entrySet().stream()
                .map(e -> new PermissionOverrideDto(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(PermissionOverrideDto::permissionCode)).toList();
        audit.log("USER_PERMISSIONS_UPDATE", MODULE, id, Map.of("overrides", before), Map.of("overrides", after));
        return toResponse(user, after);
    }

    @Transactional(readOnly = true)
    public EffectivePermissionsResponse effectivePermissionsOf(Long id) {
        getById(id);
        return new EffectivePermissionsResponse(id, List.copyOf(effectivePermissions(id)));
    }

    // ---- helpers ---------------------------------------------------------------------------

    private User lock(Long id) {
        return users.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

    /** Only a SUPER_ADMIN may touch a SUPER_ADMIN user. */
    private static void requireCanModify(Actor actor, User target) {
        if (target.hasRole(SUPER_ADMIN) && !actor.superAdmin()) {
            throw Actor.forbidden("Only a SUPER_ADMIN can modify a SUPER_ADMIN user");
        }
    }

    /** Newly assigned roles: SUPER_ADMIN is super-admin-only, and every permission must be one the actor holds. */
    private static void requireCanAssign(Actor actor, Set<Role> newlyAssigned) {
        if (newlyAssigned.stream().anyMatch(r -> SUPER_ADMIN.equals(r.getRoleCode())) && !actor.superAdmin()) {
            throw Actor.forbidden("Only a SUPER_ADMIN can assign the SUPER_ADMIN role");
        }
        actor.requireCanGrant(newlyAssigned.stream().flatMap(r -> r.permissionCodes().stream()).collect(Collectors.toSet()));
    }

    private void requireNotLastSuperAdmin(String verb) {
        users.lockSuperAdminChanges(); // two admins deactivating each other concurrently must not both pass the count
        if (users.countActiveWithRole(SUPER_ADMIN) <= 1) {
            throw new ConflictException("Cannot " + verb + " the last active SUPER_ADMIN");
        }
    }

    private void requireUnique(String mobile, String email, Long selfId) {
        boolean mobileTaken = selfId == null ? users.existsByMobile(mobile) : users.existsByMobileAndIdNot(mobile, selfId);
        if (mobileTaken) {
            throw new ConflictException("Mobile number is already in use");
        }
        if (email != null) {
            boolean emailTaken = selfId == null ? users.existsByEmailIgnoreCase(email) : users.existsByEmailIgnoreCaseAndIdNot(email, selfId);
            if (emailTaken) {
                throw new ConflictException("Email is already in use");
            }
        }
    }

    private Map<Long, List<PermissionOverrideDto>> overridesByUser(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return overrides.findByUserIds(userIds).stream().collect(Collectors.groupingBy(
                o -> o.getId().userId(), Collectors.collectingAndThen(Collectors.toList(), UserService::toDtos)));
    }

    private UserResponse toResponse(User u) {
        return toResponse(u, toDtos(overrides.findByUserId(u.getId())));
    }

    private static UserResponse toResponse(User u, List<PermissionOverrideDto> overrideDtos) {
        return new UserResponse(u.getId(), u.getFullName(), u.getMobile(), u.getEmail(), u.getStatus(),
                u.isPasswordChangeRequired(), u.getLastLoginAt(), u.getCreatedAt(),
                roleCodes(u.getRoles()).stream().sorted().toList(), overrideDtos);
    }

    private static List<PermissionOverrideDto> toDtos(List<UserPermissionOverride> list) {
        return list.stream().map(o -> new PermissionOverrideDto(o.getPermission().getPermissionCode(), o.getEffect()))
                .sorted(Comparator.comparing(PermissionOverrideDto::permissionCode)).toList();
    }

    private static Set<String> roleCodes(Set<Role> roleSet) {
        return roleSet.stream().map(Role::getRoleCode).collect(Collectors.toSet());
    }

    /** Lower-cased LIKE pattern with wildcards in the user's input escaped. */
    private static String likePattern(String search) {
        if (search == null || search.isBlank()) {
            return "%";
        }
        String escaped = search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
