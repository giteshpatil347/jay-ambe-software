package com.materialbook.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

class EmployeeSalaryRevisionTest extends AbstractExpenseApiTest {
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    private long create(String salary, String joining) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("employeeName", "Rev " + System.nanoTime(), "monthlySalary", salary));
        if (joining != null) {
            body.put("joiningDate", joining);
        }
        MvcResult r = call(HttpMethod.POST, "/api/v1/employees", admin, body);
        assertThat(status(r)).isEqualTo(201);
        return tree(r).get("id").asLong();
    }

    private MvcResult revise(long id, String salary, String effectiveFrom, String remarks) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("employeeName", "Rev", "status", "ACTIVE", "monthlySalary", salary));
        if (effectiveFrom != null) {
            body.put("effectiveFrom", effectiveFrom);
        }
        if (remarks != null) {
            body.put("salaryRemarks", remarks);
        }
        return call(HttpMethod.PUT, "/api/v1/employees/" + id, admin, body);
    }

    private String asOf(long id, String date) throws Exception {
        MvcResult r = call(HttpMethod.GET, "/api/v1/employees/" + id + "/salary-as-of?date=" + date, admin, null);
        assertThat(status(r)).isEqualTo(200);
        JsonNode v = tree(r).get("monthlySalary");
        return v.isNull() ? null : v.asText();
    }

    private JsonNode revisions(long id) throws Exception {
        return tree(call(HttpMethod.GET, "/api/v1/employees/" + id + "/salary-revisions", admin, null));
    }

    @Test
    void createWritesInitialRevisionDatedByJoiningDate() throws Exception {
        long id = create("30000.00", "2024-04-01");
        JsonNode revs = revisions(id);
        assertThat(revs).hasSize(1);
        assertThat(revs.get(0).get("effectiveFrom").asText()).isEqualTo("2024-04-01");
        assertThat(revs.get(0).get("year").asInt()).isEqualTo(2024);
        assertThat(revs.get(0).get("monthlySalary").asText()).isEqualTo("30000.00");
        assertThat(revs.get(0).get("createdByName").asText()).isNotBlank();
        assertThat(auditCount("SALARY_REVISION_ADD", "EMPLOYEE", id)).isEqualTo(1);

        long noJoin = create("100.00", null);
        assertThat(revisions(noJoin).get(0).get("effectiveFrom").asText()).isEqualTo(TODAY.toString());
        assertThat(tree(call(HttpMethod.GET, "/api/v1/employees/" + noJoin, admin, null)).get("monthlySalary").asText()).isEqualTo("100.00");
    }

    @Test
    void incrementAppendsAndAsOfResolvesAcrossYears() throws Exception {
        long id = create("20000.00", "2023-06-01");
        assertThat(status(revise(id, "22000.00", "2024-04-01", "Annual increment"))).isEqualTo(200);
        MvcResult r = revise(id, "25000.00", "2025-04-01", null);
        assertThat(tree(r).get("monthlySalary").asText()).isEqualTo("25000.00");

        JsonNode revs = revisions(id);
        assertThat(revs).hasSize(3);
        assertThat(revs.get(0).get("year").asInt()).isEqualTo(2025); // newest first
        assertThat(revs.get(1).get("remarks").asText()).isEqualTo("Annual increment");
        assertThat(revs.get(2).get("monthlySalary").asText()).isEqualTo("20000.00");

        assertThat(asOf(id, "2023-05-31")).isNull();
        assertThat(asOf(id, "2023-06-01")).isEqualTo("20000.00");
        assertThat(asOf(id, "2024-03-31")).isEqualTo("20000.00");
        assertThat(asOf(id, "2024-04-01")).isEqualTo("22000.00");
        assertThat(asOf(id, "2025-12-31")).isEqualTo("25000.00");
        assertThat(auditCount("SALARY_REVISION_ADD", "EMPLOYEE", id)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select monthly_salary from employees where id = ?", java.math.BigDecimal.class, id))
                .isEqualByComparingTo("25000.00");
    }

    @Test
    void sameDateRevisionReplacesAndUnchangedSalaryAddsNothing() throws Exception {
        long id = create("1000.00", "2024-01-01");
        revise(id, "1200.00", "2025-01-01", "first");
        revise(id, "1300.00", "2025-01-01", "corrected");
        JsonNode revs = revisions(id);
        assertThat(revs).hasSize(2);
        assertThat(revs.get(0).get("monthlySalary").asText()).isEqualTo("1300.00");
        assertThat(revs.get(0).get("remarks").asText()).isEqualTo("corrected");
        assertThat(auditCount("SALARY_REVISION_UPDATE", "EMPLOYEE", id)).isEqualTo(1);

        revise(id, "1300.00", "2025-06-01", null); // same as in force on that date -> no new revision
        assertThat(revisions(id)).hasSize(2);
    }

    @Test
    void futureDatedRevisionIsNotCurrentUntilItsDate() throws Exception {
        long id = create("5000.00", "2024-01-01");
        String future = TODAY.plusDays(30).toString();
        MvcResult r = revise(id, "6000.00", future, "Next month");
        assertThat(tree(r).get("monthlySalary").asText()).isEqualTo("5000.00");
        assertThat(tree(call(HttpMethod.GET, "/api/v1/employees/" + id, admin, null)).get("monthlySalary").asText()).isEqualTo("5000.00");
        assertThat(asOf(id, TODAY.toString())).isEqualTo("5000.00");
        assertThat(asOf(id, future)).isEqualTo("6000.00");
        assertThat(jdbc.queryForObject("select monthly_salary from employees where id = ?", java.math.BigDecimal.class, id))
                .isEqualByComparingTo("5000.00");
        assertThat(revisions(id)).hasSize(2);

        // the date "arrives": today's resolution now picks the revision (no scheduler involved)
        jdbc.update("update employee_salary_revisions set effective_from = ? where employee_id = ? and monthly_salary = 6000", TODAY.minusDays(1), id);
        assertThat(tree(call(HttpMethod.GET, "/api/v1/employees/" + id, admin, null)).get("monthlySalary").asText()).isEqualTo("6000.00");
        JsonNode listed = tree(call(HttpMethod.GET, "/api/v1/employees?size=200", admin, null)).get("content");
        for (JsonNode e : listed) {
            if (e.get("id").asLong() == id) {
                assertThat(e.get("monthlySalary").asText()).isEqualTo("6000.00");
            }
        }
    }

    @Test
    void plainEditDoesNotTouchSalaryAndValidationAndPermissions() throws Exception {
        long id = create("7000.00", "2024-01-01");
        MvcResult r = call(HttpMethod.PUT, "/api/v1/employees/" + id, admin, Map.of("employeeName", "Renamed", "status", "ACTIVE"));
        assertThat(status(r)).isEqualTo(200);
        assertThat(tree(r).get("monthlySalary").asText()).isEqualTo("7000.00");
        assertThat(revisions(id)).hasSize(1);
        assertThat(status(revise(id, "-1", null, null))).isEqualTo(422);

        String viewer = tokenWithRole("VIEWER");
        assertThat(status(call(HttpMethod.GET, "/api/v1/employees/" + id + "/salary-revisions", viewer, null))).isEqualTo(403);
        assertThat(status(call(HttpMethod.GET, "/api/v1/employees/" + id + "/salary-as-of?date=2025-01-01", viewer, null))).isEqualTo(403);
        assertThat(status(call(HttpMethod.GET, "/api/v1/employees/999999999/salary-revisions", admin, null))).isEqualTo(404);
        assertThat(status(call(HttpMethod.GET, "/api/v1/employees/999999999/salary-as-of?date=2025-01-01", admin, null))).isEqualTo(404);
        assertThat(status(call(HttpMethod.GET, "/api/v1/employees/" + id + "/salary-as-of", admin, null))).isEqualTo(400);
    }
}
