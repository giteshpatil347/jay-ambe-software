package com.materialbook.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

class EmployeeApiTest extends AbstractExpenseApiTest {

    @Test
    void createAutoCodeUpdateDeactivateAndAudit() throws Exception {
        MvcResult r = call(HttpMethod.POST, "/api/v1/employees", admin,
                Map.of("employeeName", "Ravi", "mobile", "9876543210", "monthlySalary", "4200.00", "joiningDate", "2026-01-05"));
        assertThat(status(r)).isEqualTo(201);
        JsonNode e = tree(r);
        long id = e.get("id").asLong();
        assertThat(e.get("employeeCode").asText()).matches("EMP-\\d{4,}");
        assertThat(e.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(auditCount("CREATE", "EMPLOYEE", id)).isEqualTo(1);

        MvcResult u = call(HttpMethod.PUT, "/api/v1/employees/" + id, admin,
                Map.of("employeeName", "Ravi K", "monthlySalary", "4500.00", "status", "INACTIVE"));
        assertThat(status(u)).isEqualTo(200);
        assertThat(tree(u).get("status").asText()).isEqualTo("INACTIVE");
        assertThat(tree(u).get("employeeCode").asText()).isEqualTo(e.get("employeeCode").asText());
        assertThat(auditCount("UPDATE", "EMPLOYEE", id)).isEqualTo(1);

        JsonNode inactive = tree(call(HttpMethod.GET, "/api/v1/employees?status=INACTIVE&q=ravi", admin, null));
        assertThat(inactive.get("totalElements").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(status(call(HttpMethod.GET, "/api/v1/employees/" + id, admin, null))).isEqualTo(200);
        assertThat(status(call(HttpMethod.GET, "/api/v1/employees/999999999", admin, null))).isEqualTo(404);
    }

    @Test
    void explicitCodeMustBeUniqueAndInputValidated() throws Exception {
        String code = "X" + System.nanoTime();
        Map<String, Object> body = Map.of("employeeCode", code, "employeeName", "A", "monthlySalary", "1.00");
        assertThat(status(call(HttpMethod.POST, "/api/v1/employees", admin, body))).isEqualTo(201);
        assertThat(status(call(HttpMethod.POST, "/api/v1/employees", admin, body))).isEqualTo(409);
        assertThat(status(call(HttpMethod.POST, "/api/v1/employees", admin, Map.of("employeeName", "B", "monthlySalary", "-1")))).isEqualTo(422);
        assertThat(status(call(HttpMethod.POST, "/api/v1/employees", admin, Map.of("monthlySalary", "1")))).isEqualTo(422);
    }

    @Test
    void permissionsAreEnforced() throws Exception {
        String viewer = tokenWithRole("VIEWER");
        long id = createEmployee("Perm Check");
        assertThat(status(call(HttpMethod.GET, "/api/v1/employees", viewer, null))).isEqualTo(403); // VIEWER is limited to material + stock
        assertThat(status(call(HttpMethod.GET, "/api/v1/employees/" + id + "/salary-history", viewer, null))).isEqualTo(403);
        assertThat(status(call(HttpMethod.POST, "/api/v1/employees", viewer, Map.of("employeeName", "N", "monthlySalary", "1")))).isEqualTo(403);
        assertThat(status(call(HttpMethod.PUT, "/api/v1/employees/" + id, viewer,
                Map.of("employeeName", "N", "monthlySalary", "1", "status", "ACTIVE")))).isEqualTo(403);
    }
}
