package com.materialbook;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-test-secret-test-secret-0123456789",
        "app.auth.rate-limit-per-minute=1000",
        "app.auth.bootstrap-admin.mobile=9000000001",
        "app.auth.bootstrap-admin.password=Bootstrap-Pass-1"
})
@Testcontainers
public abstract class AbstractIntegrationTest {
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start(); // shared by all subclasses for the whole JVM
    }

    @Autowired
    protected JdbcTemplate jdbc;

    /**
     * Production issues a permission-less token to a user who still has a temporary password (see ForcedPasswordChangeTest).
     * Fixtures that log in bootstrap/created users to exercise other features switch the flag off first.
     */
    protected void skipForcedPasswordChange(String mobile) {
        jdbc.update("update users set password_change_required = false where mobile = ?", mobile);
    }
}
