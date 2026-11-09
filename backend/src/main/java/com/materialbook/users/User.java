package com.materialbook.users;

import com.materialbook.common.AuditedEntity;
import com.materialbook.roles.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "users")
public class User extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String mobile;

    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "password_change_required", nullable = false)
    private boolean passwordChangeRequired = true;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @BatchSize(size = 50)
    @ManyToMany
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected User() {}

    public User(String fullName, String mobile, String passwordHash, boolean passwordChangeRequired) {
        this.fullName = fullName;
        this.mobile = mobile;
        this.passwordHash = passwordHash;
        this.passwordChangeRequired = passwordChangeRequired;
    }

    public boolean isActive() { return status == UserStatus.ACTIVE; }

    public boolean isLocked(Instant now) { return lockedUntil != null && lockedUntil.isAfter(now); }

    /** Counts a failure; at {@code maxFailures} the account is locked and the counter restarts. */
    public void registerFailedLogin(Instant now, int maxFailures, Duration lockDuration) {
        failedLoginCount++;
        if (failedLoginCount >= maxFailures) {
            lockedUntil = now.plus(lockDuration);
            failedLoginCount = 0;
        }
    }

    public void registerSuccessfulLogin(Instant now) {
        failedLoginCount = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public void changePassword(String newHash) {
        passwordHash = newHash;
        passwordChangeRequired = false;
    }

    public void addRole(Role role) { roles.add(role); }

    public void replaceRoles(Set<Role> newRoles) {
        roles.clear();
        roles.addAll(newRoles);
    }

    public boolean hasRole(String roleCode) {
        return roles.stream().anyMatch(r -> r.getRoleCode().equals(roleCode));
    }

    public void updateProfile(String fullName, String mobile, String email) {
        this.fullName = fullName;
        this.mobile = mobile;
        this.email = email;
    }

    public void setStatus(UserStatus status) { this.status = status; }

    /** Admin-set temporary password: forces a change at next login and lifts any lockout. */
    public void resetPassword(String newHash) {
        passwordHash = newHash;
        passwordChangeRequired = true;
        failedLoginCount = 0;
        lockedUntil = null;
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getMobile() { return mobile; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public UserStatus getStatus() { return status; }
    public boolean isPasswordChangeRequired() { return passwordChangeRequired; }
    public int getFailedLoginCount() { return failedLoginCount; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Set<Role> getRoles() { return roles; }
}
