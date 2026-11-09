package com.materialbook.permissions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "permissions")
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "permission_code", nullable = false, updatable = false)
    private String permissionCode;

    @Column(name = "module_code", nullable = false, updatable = false)
    private String moduleCode;

    @Column(name = "action_code", nullable = false, updatable = false)
    private String actionCode;

    @Column(updatable = false)
    private String description;

    protected Permission() {}

    public Long getId() { return id; }
    public String getPermissionCode() { return permissionCode; }
    public String getModuleCode() { return moduleCode; }
    public String getActionCode() { return actionCode; }
    public String getDescription() { return description; }
}
