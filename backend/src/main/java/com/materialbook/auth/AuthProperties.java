package com.materialbook.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthProperties(
        Duration refreshTokenTtl,
        boolean cookieSecure,
        int maxFailedLogins,
        Duration lockoutDuration,
        int rateLimitPerMinute,
        BootstrapAdmin bootstrapAdmin) {

    /** All three values come from env; bootstrap is skipped when mobile or password is blank. */
    public record BootstrapAdmin(String mobile, String password, String name) {}
}
