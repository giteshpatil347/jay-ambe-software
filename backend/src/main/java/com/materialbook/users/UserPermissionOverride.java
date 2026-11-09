package com.materialbook.users;

import com.materialbook.permissions.Permission;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;

@Entity
@Table(name = "user_permission_overrides")
public class UserPermissionOverride {
    @Embeddable
    public record Key(@Column(name = "user_id") Long userId, @Column(name = "permission_id") Long permissionId)
            implements Serializable {}

    @EmbeddedId
    private Key id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("permissionId")
    @JoinColumn(name = "permission_id")
    private Permission permission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OverrideEffect effect;

    protected UserPermissionOverride() {}

    public UserPermissionOverride(Long userId, Permission permission, OverrideEffect effect) {
        this.id = new Key(userId, permission.getId());
        this.permission = permission;
        this.effect = effect;
    }

    public void setEffect(OverrideEffect effect) { this.effect = effect; }

    public Key getId() { return id; }
    public Permission getPermission() { return permission; }
    public OverrideEffect getEffect() { return effect; }
}
