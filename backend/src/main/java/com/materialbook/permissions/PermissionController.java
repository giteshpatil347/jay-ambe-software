package com.materialbook.permissions;

import com.materialbook.permissions.PermissionService.ModuleGroup;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionController {
    private final PermissionService permissions;

    public PermissionController(PermissionService permissions) {
        this.permissions = permissions;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION.VIEW')")
    List<ModuleGroup> list() {
        return permissions.listGroupedByModule();
    }
}
