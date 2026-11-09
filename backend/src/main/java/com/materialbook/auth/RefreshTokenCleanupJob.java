package com.materialbook.auth;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily purge of expired refresh tokens. Revoked-but-unexpired rows are kept on purpose:
 * they are what makes reuse detection work.
 */
@Component
@EnableScheduling
public class RefreshTokenCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenRepository refreshTokens;

    public RefreshTokenCleanupJob(RefreshTokenRepository refreshTokens) { this.refreshTokens = refreshTokens; }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpired() {
        log.info("Purged {} expired refresh tokens", refreshTokens.deleteExpired(Instant.now()));
    }
}
