package com.materialbook.permissions;

import com.materialbook.common.ApiException.ValidationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionService {
    public record PermissionResponse(String permissionCode, String actionCode, String description) {}

    public record ModuleGroup(String moduleCode, List<PermissionResponse> permissions) {}

    private final PermissionRepository permissions;

    public PermissionService(PermissionRepository permissions) {
        this.permissions = permissions;
    }

    /** The whole catalogue grouped by module, for the permission matrix. */
    @Transactional(readOnly = true)
    public List<ModuleGroup> listGroupedByModule() {
        Map<String, List<PermissionResponse>> byModule = new LinkedHashMap<>();
        for (Permission p : permissions.findAllByOrderByModuleCodeAscPermissionCodeAsc()) {
            byModule.computeIfAbsent(p.getModuleCode(), k -> new ArrayList<>())
                    .add(new PermissionResponse(p.getPermissionCode(), p.getActionCode(), p.getDescription()));
        }
        return byModule.entrySet().stream().map(e -> new ModuleGroup(e.getKey(), e.getValue())).toList();
    }

    /** Resolves codes to entities; any unknown code is a 422. */
    @Transactional(readOnly = true)
    public Map<String, Permission> requireByCodes(Set<String> codes) {
        Map<String, Permission> found = permissions.findByPermissionCodeIn(codes).stream()
                .collect(Collectors.toMap(Permission::getPermissionCode, p -> p));
        List<String> unknown = codes.stream().filter(c -> !found.containsKey(c)).sorted().toList();
        if (!unknown.isEmpty()) {
            throw new ValidationException("Unknown permission code(s): " + String.join(", ", unknown));
        }
        return found;
    }
}
