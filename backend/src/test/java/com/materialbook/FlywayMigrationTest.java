package com.materialbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class FlywayMigrationTest extends AbstractIntegrationTest {

    @Test
    void migrationsApplyAndSeedData() {
        assertThat(jdbc.queryForObject("select count(*) from permissions", Integer.class)).isEqualTo(48);
        assertThat(jdbc.queryForObject("select count(*) from roles", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class)).isGreaterThanOrEqualTo(3);
    }

    @Test
    void negativeStockIsRejectedByDatabase() {
        jdbc.update("insert into products(product_code, product_name, unit) values ('T-1', 't', 'KG')");
        assertThatThrownBy(() -> jdbc.update("update stock_balances set quantity = -1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
