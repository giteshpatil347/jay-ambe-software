package com.materialbook.roles;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByRoleCode(String roleCode);

    boolean existsByRoleCode(String roleCode);

    List<Role> findByRoleCodeIn(Set<String> roleCodes);

    List<Role> findAllByOrderByRoleCodeAsc();
}
