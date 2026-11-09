package com.materialbook.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

class ExpenseApiTest extends AbstractExpenseApiTest {

    @Test
    void createGetUpdateGeneralExpenseAndAudit() throws Exception {
        JsonNode created = createExpense(expenseBody(generalCategory, "250.50"));
        long id = created.get("id").asLong();
        assertThat(created.get("expenseNumber").asText()).startsWith("EXP-");
        assertThat(created.get("amount").asText()).isEqualTo("250.50");
        assertThat(created.get("status").asText()).isEqualTo("POSTED");
        assertThat(created.get("categoryName").asText()).startsWith("General");
        assertThat(auditCount("CREATE", "EXPENSE", id)).isEqualTo(1);

        Map<String, Object> upd = expenseBody(generalCategory, "300.00");
        upd.put("version", created.get("version").asLong());
        MvcResult r = call(HttpMethod.PUT, "/api/v1/expenses/" + id, admin, upd);
        assertThat(status(r)).isEqualTo(200);
        assertThat(tree(r).get("amount").asText()).isEqualTo("300.00");
        assertThat(auditCount("UPDATE", "EXPENSE", id)).isEqualTo(1);
        assertThat(tree(call(HttpMethod.GET, "/api/v1/expenses/" + id, admin, null)).get("amount").asText()).isEqualTo("300.00");
        assertThat(status(call(HttpMethod.GET, "/api/v1/expenses/999999999", admin, null))).isEqualTo(404);
    }

    @Test
    void detailMustMatchCategoryType() throws Exception {
        long emp = createEmployee("Detail Check");
        Map<String, Object> generalWithSalary = expenseBody(generalCategory, "10.00");
        generalWithSalary.put("salary", salaryBody(emp, "10.00").get("salary"));
        assertThat(status(call(HttpMethod.POST, "/api/v1/expenses", admin, generalWithSalary))).isEqualTo(422);
        assertThat(status(call(HttpMethod.POST, "/api/v1/expenses", admin, expenseBody(salaryCategory, "10.00")))).isEqualTo(422);
        assertThat(status(call(HttpMethod.POST, "/api/v1/expenses", admin, expenseBody(electricityCategory, "10.00")))).isEqualTo(422);
        Map<String, Object> elecWithSalary = electricityBody("10.00");
        elecWithSalary.put("salary", salaryBody(emp, "10.00").get("salary"));
        assertThat(status(call(HttpMethod.POST, "/api/v1/expenses", admin, elecWithSalary))).isEqualTo(422);

        Map<String, Object> badAdj = salaryBody(emp, "10.00");
        badAdj.put("salary", Map.of("employeeId", emp, "paymentType", "MONTHLY", "grossSalary", "100.00", "advanceAdjustment", "150.00"));
        assertThat(status(call(HttpMethod.POST, "/api/v1/expenses", admin, badAdj))).isEqualTo(422);

        Map<String, Object> badConn = electricityBody("10.00");
        badConn.put("electricity", Map.of("connectionNumber", "3_NO", "usageType", "DAILY", "usageQuantity", "2"));
        assertThat(status(call(HttpMethod.POST, "/api/v1/expenses", admin, badConn))).isEqualTo(422);
    }

    @Test
    void salaryAndElectricityDetailRoundTrip() throws Exception {
        long emp = createEmployee("Salary Person");
        JsonNode sal = createExpense(salaryBody(emp, "4500.00"));
        JsonNode got = tree(call(HttpMethod.GET, "/api/v1/expenses/" + sal.get("id").asLong(), admin, null));
        assertThat(got.get("salary").get("employeeId").asLong()).isEqualTo(emp);
        assertThat(got.get("salary").get("advanceAdjustment").asText()).isEqualTo("500.00");
        assertThat(got.get("electricity").isNull()).isTrue();

        JsonNode el = createExpense(electricityBody("1024.25"));
        JsonNode gotEl = tree(call(HttpMethod.GET, "/api/v1/expenses/" + el.get("id").asLong(), admin, null));
        assertThat(gotEl.get("electricity").get("usageQuantity").asText()).isEqualTo("3.00");
        assertThat(gotEl.get("salary").isNull()).isTrue();

        Map<String, Object> upd = salaryBody(emp, "4000.00");
        upd.put("version", sal.get("version").asLong());
        MvcResult r = call(HttpMethod.PUT, "/api/v1/expenses/" + sal.get("id").asLong(), admin, upd);
        assertThat(status(r)).isEqualTo(200);

        Map<String, Object> toGeneral = expenseBody(generalCategory, "1.00");
        toGeneral.put("version", tree(r).get("version").asLong());
        assertThat(status(call(HttpMethod.PUT, "/api/v1/expenses/" + sal.get("id").asLong(), admin, toGeneral))).isEqualTo(422);

        JsonNode history = tree(call(HttpMethod.GET, "/api/v1/employees/" + emp + "/salary-history", admin, null));
        assertThat(history.get("totalElements").asLong()).isEqualTo(1);
        assertThat(history.get("content").get(0).get("amount").asText()).isEqualTo("4000.00");
        assertThat(status(call(HttpMethod.GET, "/api/v1/employees/999999999/salary-history", admin, null))).isEqualTo(404);
    }

    @Test
    void inactiveEmployeeCannotReceiveSalary() throws Exception {
        long emp = createEmployee("Leaver");
        MvcResult r = call(HttpMethod.PUT, "/api/v1/employees/" + emp, admin,
                Map.of("employeeName", "Leaver", "monthlySalary", "5000.00", "status", "INACTIVE"));
        assertThat(status(r)).isEqualTo(200);
        assertThat(status(call(HttpMethod.POST, "/api/v1/expenses", admin, salaryBody(emp, "100.00")))).isEqualTo(422);
    }

    @Test
    void cancelRules() throws Exception {
        JsonNode created = createExpense(expenseBody(generalCategory, "75.00"));
        long id = created.get("id").asLong();
        String url = "/api/v1/expenses/" + id + "/cancel";
        assertThat(status(call(HttpMethod.POST, url, admin, Map.of("reason", " ")))).isEqualTo(422);
        MvcResult ok = call(HttpMethod.POST, url, admin, Map.of("reason", "entered twice"));
        assertThat(status(ok)).isEqualTo(200);
        assertThat(tree(ok).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(tree(ok).get("cancellationReason").asText()).isEqualTo("entered twice");
        assertThat(status(call(HttpMethod.POST, url, admin, Map.of("reason", "again")))).isEqualTo(409);
        assertThat(auditCount("CANCEL", "EXPENSE", id)).isEqualTo(1);

        Map<String, Object> upd = expenseBody(generalCategory, "80.00");
        upd.put("version", tree(ok).get("version").asLong());
        assertThat(status(call(HttpMethod.PUT, "/api/v1/expenses/" + id, admin, upd))).isEqualTo(409);
    }

    @Test
    void staleVersionIsConflictAndVersionRequired() throws Exception {
        JsonNode created = createExpense(expenseBody(generalCategory, "20.00"));
        long id = created.get("id").asLong();
        Map<String, Object> first = expenseBody(generalCategory, "21.00");
        first.put("version", created.get("version").asLong());
        assertThat(status(call(HttpMethod.PUT, "/api/v1/expenses/" + id, admin, first))).isEqualTo(200);
        assertThat(status(call(HttpMethod.PUT, "/api/v1/expenses/" + id, admin, first))).isEqualTo(409);
        assertThat(status(call(HttpMethod.PUT, "/api/v1/expenses/" + id, admin, expenseBody(generalCategory, "22.00")))).isEqualTo(422);
    }

    @Test
    void totalAmountCoversPostedRowsOfAllPagesAndFilters() throws Exception {
        long cat = createCategory("TOT_" + System.nanoTime(), "Totals " + System.nanoTime(), "GENERAL");
        createExpense(expenseBody(cat, "100.00"));
        createExpense(expenseBody(cat, "200.00"));
        JsonNode third = createExpense(expenseBody(cat, "400.00"));
        Map<String, Object> other = expenseBody(cat, "50.00");
        other.put("paidTo", "Special Vendor");
        createExpense(other);
        call(HttpMethod.POST, "/api/v1/expenses/" + third.get("id").asLong() + "/cancel", admin, Map.of("reason", "x"));

        JsonNode page = tree(call(HttpMethod.GET, "/api/v1/expenses?categoryId=" + cat + "&size=1", admin, null));
        assertThat(page.get("content")).hasSize(1);
        assertThat(page.get("totalElements").asLong()).isEqualTo(4);
        assertThat(page.get("totalAmount").asText()).isEqualTo("350.00");
        assertThat(page.get("content").get(0).get("categoryName").asText()).startsWith("Totals");

        JsonNode cancelled = tree(call(HttpMethod.GET, "/api/v1/expenses?categoryId=" + cat + "&status=CANCELLED", admin, null));
        assertThat(cancelled.get("totalElements").asLong()).isEqualTo(1);
        assertThat(cancelled.get("totalAmount").decimalValue().signum()).isZero();

        JsonNode byQ = tree(call(HttpMethod.GET, "/api/v1/expenses?categoryId=" + cat + "&q=special", admin, null));
        assertThat(byQ.get("totalElements").asLong()).isEqualTo(1);
        assertThat(byQ.get("totalAmount").asText()).isEqualTo("50.00");

        JsonNode outside = tree(call(HttpMethod.GET, "/api/v1/expenses?categoryId=" + cat + "&from=2030-01-01", admin, null));
        assertThat(outside.get("totalElements").asLong()).isZero();
        assertThat(outside.get("totalAmount").decimalValue().signum()).isZero();
    }

    @Test
    void parallelCreatesGetUniqueNumbers() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Callable<MvcResult>> jobs = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                jobs.add(() -> call(HttpMethod.POST, "/api/v1/expenses", admin, expenseBody(generalCategory, "1.00")));
            }
            Set<String> numbers = new HashSet<>();
            for (Future<MvcResult> f : pool.invokeAll(jobs)) {
                MvcResult r = f.get();
                assertThat(status(r)).isEqualTo(201);
                numbers.add(tree(r).get("expenseNumber").asText());
            }
            assertThat(numbers).hasSize(20);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void permissionsAreEnforced() throws Exception {
        String viewer = tokenWithRole("VIEWER");
        String purchase = tokenWithRole("PURCHASE_STAFF");
        assertThat(status(call(HttpMethod.GET, "/api/v1/expenses", viewer, null))).isEqualTo(403); // VIEWER is limited to material + stock
        assertThat(status(call(HttpMethod.GET, "/api/v1/expenses", purchase, null))).isEqualTo(403);
        assertThat(status(call(HttpMethod.POST, "/api/v1/expenses", viewer, expenseBody(generalCategory, "5.00")))).isEqualTo(403);
        long id = createExpense(expenseBody(generalCategory, "5.00")).get("id").asLong();
        Map<String, Object> upd = expenseBody(generalCategory, "6.00");
        upd.put("version", 0);
        assertThat(status(call(HttpMethod.PUT, "/api/v1/expenses/" + id, viewer, upd))).isEqualTo(403);
        assertThat(status(call(HttpMethod.POST, "/api/v1/expenses/" + id + "/cancel", viewer, Map.of("reason", "r")))).isEqualTo(403);
        assertThat(status(call(HttpMethod.GET, "/api/v1/expenses", "garbage", null))).isEqualTo(401);
    }
}
