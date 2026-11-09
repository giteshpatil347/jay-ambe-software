package com.materialbook.roles;

import com.materialbook.permissions.Permission;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "roles")
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_code", nullable = false, updatable = false)
    private String roleCode;

    @Column(name = "role_name", nullable = false)
    private String roleName;

    private String description;

    @Column(name = "is_system", nullable = false, updatable = false)
    private boolean system;

    @BatchSize(size = 50)
    @ManyToMany
    @JoinTable(name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();

    protected Role() {}

    public Role(String roleCode, String roleName, String description, Set<Permission> permissions) {
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.description = description;
        this.permissions = new HashSet<>(permissions);
    }

    public void update(String roleName, String description) {
        this.roleName = roleName;
        this.description = description;
    }

    public void replacePermissions(Set<Permission> newPermissions) {
        permissions.clear();
        permissions.addAll(newPermissions);
    }

    public Set<String> permissionCodes() {
        return permissions.stream().map(Permission::getPermissionCode).collect(Collectors.toSet());
    }

    public Long getId() { return id; }
    public String getRoleCode() { return roleCode; }
    public String getRoleName() { return roleName; }
    public String getDescription() { return description; }
    public boolean isSystem() { return system; }
    public Set<Permission> getPermissions() { return permissions; }
}
