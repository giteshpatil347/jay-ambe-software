package com.materialbook.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtServiceTest {
    @Test
    void missingSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService("", Duration.ofMinutes(10))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService("x".repeat(31), Duration.ofMinutes(10))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exampleEnvPlaceholderSecretIsRejected() {
        assertThatThrownBy(() -> new JwtService("Change-Me-" + "x".repeat(40), Duration.ofMinutes(10))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void issuedTokenRoundTripsWithPermissions() {
        JwtService svc = new JwtService("x".repeat(32), Duration.ofMinutes(10));
        Jwt jwt = svc.decoder().decode(svc.issue(7L, List.of("MATERIAL_IN.CREATE")));
        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getClaimAsStringList("perms")).containsExactly("MATERIAL_IN.CREATE");
        assertThat(svc.expiresInSeconds()).isEqualTo(600);
    }
}
