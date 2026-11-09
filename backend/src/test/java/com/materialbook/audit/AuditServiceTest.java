package com.materialbook.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.materialbook.AbstractIntegrationTest;
import com.materialbook.audit.service.AuditService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

class AuditServiceTest extends AbstractIntegrationTest {
    @Autowired AuditService audit;

    record Credentials(String mobile, String passwordHash, String refreshToken) {}

    private Long insertedId(String module) {
        return jdbc.queryForObject("select max(id) from audit_logs where module = ?", Long.class, module);
    }

    @Test
    void redactsSensitiveKeysInMapsBeansAndNestedStructures() {
        audit.logAs(null, "UPDATE", "REDACT", 1L,
                Map.of("Password", "p1", "nested", List.of(Map.of("accessToken", "t1", "keep", "ok"))),
                new Credentials("9876543210", "$2a$hash", "raw-refresh"));

        String dump = jdbc.queryForObject(
                "select old_values::text || new_values::text from audit_logs where id = ?", String.class, insertedId("REDACT"));
        assertThat(dump).doesNotContain("p1", "t1", "$2a$hash", "raw-refresh")
                .contains("ok", "9876543210", "\"passwordHash\": \"***\"");
    }

    @Test
    void jsonbRoundtripKeepsMoneyAsStringAndNullsAbsent() {
        audit.logAs(null, "CREATE", "ROUNDTRIP", 7L, null, Map.of("amount", new BigDecimal("1234.50"), "qty", 3));

        Long id = insertedId("ROUNDTRIP");
        assertThat(jdbc.queryForObject("select new_values->>'amount' from audit_logs where id = ?", String.class, id))
                .isEqualTo("1234.50");
        assertThat(jdbc.queryForObject("select (new_values->>'qty')::int from audit_logs where id = ?", Integer.class, id))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("select old_values is null from audit_logs where id = ?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("select user_id is null and host(ip_address) = '127.0.0.1' from audit_logs where id = ?",
                Boolean.class, id)).isTrue(); // MockMvc request context supplies the client IP
    }

    @Test
    void updateAndDeleteAreRejectedByTheDatabase() {
        audit.logAs(null, "CREATE", "IMMUTABLE", 1L, null, null);
        Long id = insertedId("IMMUTABLE");

        assertThatThrownBy(() -> jdbc.update("update audit_logs set action = 'X' where id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from audit_logs where id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }
}
