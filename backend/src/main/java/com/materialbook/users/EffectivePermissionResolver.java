package com.materialbook.users;

import java.util.HashSet;
import java.util.Set;

/** Effective permissions = (role permissions − DENY overrides) + ALLOW overrides. */
public final class EffectivePermissionResolver {
    private EffectivePermissionResolver() {}

    public static Set<String> resolve(Set<String> rolePermissions, Set<String> allowed, Set<String> denied) {
        Set<String> result = new HashSet<>(rolePermissions);
        result.removeAll(denied);
        result.addAll(allowed);
        return result;
    }
}
