package com.materialbook.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class EffectivePermissionResolverTest {
    @Test
    void roleOnly() {
        assertThat(EffectivePermissionResolver.resolve(Set.of("A", "B"), Set.of(), Set.of())).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void denyBeatsRole() {
        assertThat(EffectivePermissionResolver.resolve(Set.of("A", "B"), Set.of(), Set.of("B"))).containsExactly("A");
    }

    @Test
    void allowAddsPermissionNotInRole() {
        assertThat(EffectivePermissionResolver.resolve(Set.of("A"), Set.of("C"), Set.of())).containsExactlyInAnyOrder("A", "C");
    }

    @Test
    void denyOfUnheldPermissionIsHarmless() {
        assertThat(EffectivePermissionResolver.resolve(Set.of("A"), Set.of(), Set.of("Z"))).containsExactly("A");
    }
}
