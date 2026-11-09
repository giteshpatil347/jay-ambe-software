package com.materialbook.permissions;

import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    List<Permission> findByPermissionCodeIn(Set<String> permissionCodes);

    List<Permission> findAllByOrderByModuleCodeAscPermissionCodeAsc();
}
