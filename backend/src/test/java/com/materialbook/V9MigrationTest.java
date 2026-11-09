package com.materialbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Applies V1..V8, inserts pre-V9 data (WEEKLY salary payment, employees) in a scratch database, then migrates to V9. */
class V9MigrationTest extends AbstractIntegrationTest {

    @Test
    void v9MigratesWeeklyRowsAndBackfillsRevisions() {
        jdbc.execute("create database mig_v9");
        String url = POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/mig_v9$1");
        DriverManagerDataSource ds = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("8").load().migrate();

        JdbcTemplate db = new JdbcTemplate(ds);
        db.update("insert into users(full_name, mobile, password_hash) values ('U', '9111111111', 'x')");
        db.update("insert into expense_categories(category_code, category_name, category_type) values ('S', 'S', 'SALARY')");
        db.update("insert into employees(employee_code, employee_name, weekly_salary, joining_date) values ('E1', 'With join date', 1500.00, '2025-04-01')");
        db.update("insert into employees(employee_code, employee_name, weekly_salary) values ('E2', 'No join date', 0)");
        db.update("""
                insert into expenses(expense_number, expense_date, category_id, amount, created_by)
                select 'EXP-OLD-1', date '2025-05-01', (select id from expense_categories where category_code = 'S'), 100, (select id from users)""");
        db.update("""
                insert into salary_payments(expense_id, employee_id, payment_type, salary_period_start, salary_period_end, gross_salary)
                select (select id from expenses), (select id from employees where employee_code = 'E1'), 'WEEKLY', '2025-04-27', '2025-05-03', 1500""");

        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();

        assertThat(db.queryForObject("select payment_type from salary_payments", String.class)).isEqualTo("MONTHLY");
        assertThatThrownBy(() -> db.update("update salary_payments set payment_type = 'WEEKLY'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        db.update("update salary_payments set payment_type = 'ADVANCE'");

        List<Map<String, Object>> revisions = db.queryForList("""
                select e.employee_code, r.effective_from::text as effective_from, r.monthly_salary, r.created_by
                from employee_salary_revisions r join employees e on e.id = r.employee_id order by e.employee_code""");
        assertThat(revisions).hasSize(2);
        assertThat(revisions.get(0)).containsEntry("effective_from", "2025-04-01").containsEntry("created_by", null);
        assertThat((BigDecimal) revisions.get(0).get("monthly_salary")).isEqualByComparingTo("1500.00"); // renamed, not converted
        assertThat(db.queryForObject("select monthly_salary from employees where employee_code = 'E1'", BigDecimal.class))
                .isEqualByComparingTo("1500.00");
        assertThat(db.queryForObject("select count(*) from information_schema.columns where table_name = 'employees' and column_name = 'weekly_salary'",
                Integer.class)).isZero();

        db.update("insert into electricity_readings(expense_id, connection_number, usage_type) select id, '1_NO', 'HOURLY' from expenses");
        assertThat(db.queryForObject("select usage_quantity from electricity_readings", BigDecimal.class)).isNull(); // pre-V9 rows stay valid
        assertThatThrownBy(() -> db.update("update electricity_readings set usage_quantity = 0"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("insert into employee_salary_revisions(employee_id, effective_from, monthly_salary) select id, '2025-04-01', 1 from employees where employee_code = 'E1'"))
                .isInstanceOf(DataIntegrityViolationException.class); // UNIQUE (employee_id, effective_from)
    }
}
