package com.materialbook.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

class ElectricityAndSalaryExpenseTest extends AbstractExpenseApiTest {

    private Map<String, Object> electricity(Object quantity) {
        Map<String, Object> e = new HashMap<>(Map.of("connectionNumber", "2_NO", "usageType", "HOURLY"));
        if (quantity != null) {
            e.put("usageQuantity", quantity);
        }
        return e;
    }

    @Test
    void electricityRequiresPositiveUsageQuantity() throws Exception {
        for (Object bad : new Object[] {null, "0", "-1", "1.234", "123456789"}) {
            Map<String, Object> body = expenseBody(electricityCategory, "100.00");
            body.put("electricity", electricity(bad));
            assertThat(status(call(HttpMethod.POST, "/api/v1/expenses", admin, body))).as("quantity %s", bad).isEqualTo(422);
        }
        Map<String, Object> ok = expenseBody(electricityCategory, "100.00");
        ok.put("electricity", electricity("8"));
        JsonNode created = createExpense(ok);
        assertThat(created.get("usageType").asText()).isEqualTo("HOURLY");
        assertThat(created.get("usageQuantity").asText()).isEqualTo("8.00");
        assertThat(created.get("electricity").get("usageQuantity").asText()).isEqualTo("8.00");
        assertThat(created.get("electricity").get("connectionNumber").asText()).isEqualTo("2_NO");
    }

    @Test
    void electricityStoresNoPaidToPaymentModeAndIgnoresOldFields() throws Exception {
        Map<String, Object> body = expenseBody(electricityCategory, "250.00"); // sends paidTo + paymentMode
        body.put("voucherNumber", "V-77"); // old clients may still send it: ignored, and not part of the API any more
        Map<String, Object> e = electricity("2.5");
        e.putAll(Map.of("usageStart", "2026-03-01T00:00:00Z", "usageEnd", "2026-03-02T00:00:00Z", "unitsConsumed", "9", "unitRate", "8"));
        body.put("electricity", e);
        JsonNode created = createExpense(body);
        assertThat(created.get("paidTo").isNull()).isTrue();
        assertThat(created.get("paymentMode").isNull()).isTrue();
        assertThat(created.has("voucherNumber")).isFalse();
        long id = created.get("id").asLong();
        assertThat(jdbc.queryForObject("select count(*) from electricity_readings where expense_id = ? and usage_start is null and unit_rate is null and units_consumed is null",
                Long.class, id)).isEqualTo(1);

        Map<String, Object> upd = expenseBody(electricityCategory, "260.00");
        upd.put("electricity", electricity(null));
        upd.put("version", created.get("version").asLong());
        assertThat(status(call(HttpMethod.PUT, "/api/v1/expenses/" + id, admin, upd))).isEqualTo(422); // required on edit too
        upd.put("electricity", electricity("3"));
        MvcResult r = call(HttpMethod.PUT, "/api/v1/expenses/" + id, admin, upd);
        assertThat(status(r)).isEqualTo(200);
        assertThat(tree(r).get("usageQuantity").asText()).isEqualTo("3.00");
    }

    @Test
    void listRowsCarryUsageForElectricityOnly() throws Exception {
        Map<String, Object> hourly = expenseBody(electricityCategory, "10.00");
        hourly.put("expenseDate", "2031-05-01");
        hourly.put("electricity", electricity("8"));
        Map<String, Object> daily = expenseBody(electricityCategory, "20.00");
        daily.put("expenseDate", "2031-05-02");
        daily.put("electricity", Map.of("connectionNumber", "1_NO", "usageType", "DAILY", "usageQuantity", "3"));
        createExpense(hourly);
        createExpense(daily);
        Map<String, Object> general = expenseBody(generalCategory, "30.00");
        general.put("expenseDate", "2031-05-03");
        createExpense(general);

        JsonNode rows = tree(call(HttpMethod.GET, "/api/v1/expenses?from=2031-05-01&to=2031-05-03", admin, null)).get("content");
        assertThat(rows).hasSize(3); // newest first: general, daily, hourly
        assertThat(rows.get(0).get("usageType").isNull()).isTrue();
        assertThat(rows.get(0).get("usageQuantity").isNull()).isTrue();
        assertThat(rows.get(1).get("usageType").asText()).isEqualTo("DAILY");
        assertThat(rows.get(1).get("usageQuantity").asText()).isEqualTo("3.00");
        assertThat(rows.get(2).get("usageType").asText()).isEqualTo("HOURLY");
        assertThat(rows.get(2).get("usageQuantity").asText()).isEqualTo("8.00");
        assertThat(rows.get(2).get("electricity").isNull()).isTrue(); // detail object stays out of lists
        assertThat(rows.get(2).get("amount").asText()).isEqualTo("10.00");
    }

    @Test
    void salaryStoresNoPeriodOrPaymentModeAndAcceptsMonthly() throws Exception {
        long emp = createEmployee("Monthly Mia");
        Map<String, Object> body = salaryBody(emp, "4500.00"); // paymentMode CASH + paidTo from the base body
        body.put("voucherNumber", "V-1");
        Map<String, Object> salary = new HashMap<>((Map<String, Object>) body.get("salary"));
        salary.putAll(Map.of("periodStart", "2026-03-01", "periodEnd", "2026-03-31"));
        body.put("salary", salary);
        JsonNode created = createExpense(body);
        assertThat(created.get("paymentMode").isNull()).isTrue();
        assertThat(created.has("voucherNumber")).isFalse();
        assertThat(created.get("salary").get("paymentType").asText()).isEqualTo("MONTHLY");
        assertThat(created.get("salary").has("periodStart")).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from salary_payments where expense_id = ? and salary_period_start is null and salary_period_end is null",
                Long.class, created.get("id").asLong())).isEqualTo(1);
    }

    @Test
    void weeklyPaymentTypeIsRejectedAndAdjustmentStillCapped() throws Exception {
        long emp = createEmployee("Weekly Wes");
        Map<String, Object> body = salaryBody(emp, "100.00");
        body.put("salary", Map.of("employeeId", emp, "paymentType", "WEEKLY", "grossSalary", "100.00"));
        MvcResult r = call(HttpMethod.POST, "/api/v1/expenses", admin, body);
        assertThat(status(r)).isEqualTo(422);
        assertThat(tree(r).get("fieldErrors").get(0).get("field").asText()).isEqualTo("salary.paymentType");
        for (String type : new String[] {"ADHOC", "ADVANCE", "SETTLEMENT"}) {
            body.put("salary", Map.of("employeeId", emp, "paymentType", type, "grossSalary", "100.00"));
            assertThat(status(call(HttpMethod.POST, "/api/v1/expenses", admin, body))).as(type).isEqualTo(201);
        }
        body.put("salary", Map.of("employeeId", emp, "paymentType", "MONTHLY", "grossSalary", "100.00", "advanceAdjustment", "100.01"));
        assertThat(status(call(HttpMethod.POST, "/api/v1/expenses", admin, body))).isEqualTo(422);
    }

    @Test
    void electricityPriceIsOptionalButEveryOtherCategoryNeedsAnAmount() throws Exception {
        Map<String, Object> noPrice = expenseBody(electricityCategory, "1.00");
        noPrice.remove("amount");
        noPrice.put("electricity", electricity("6"));
        JsonNode created = createExpense(noPrice);
        assertThat(created.get("amount").decimalValue()).isEqualByComparingTo("0");
        assertThat(created.get("usageQuantity").asText()).isEqualTo("6.00");

        Map<String, Object> general = expenseBody(generalCategory, "1.00");
        general.remove("amount");
        assertThat(status(call(HttpMethod.POST, "/api/v1/expenses", admin, general))).as("general without amount").isEqualTo(422);
        general.put("amount", "0.00");
        assertThat(status(call(HttpMethod.POST, "/api/v1/expenses", admin, general))).as("general with zero").isEqualTo(422);

        Map<String, Object> salary = salaryBody(createEmployee("No Amount Nia"), "1.00");
        salary.remove("amount");
        assertThat(status(call(HttpMethod.POST, "/api/v1/expenses", admin, salary))).as("salary without amount").isEqualTo(422);
    }

    @Test
    void listRowsDescribeSalaryAndElectricityRows() throws Exception {
        createExpense(salaryBody(createEmployee("Listed Lena"), "700.00"));
        Map<String, Object> el = expenseBody(electricityCategory, "50.00");
        el.put("electricity", electricity("3"));
        createExpense(el);

        JsonNode page = tree(call(HttpMethod.GET, "/api/v1/expenses?size=100", admin, null));
        boolean salaryRow = false;
        boolean electricityRow = false;
        for (JsonNode row : page.get("content")) {
            if ("SALARY".equals(row.get("categoryType").asText()) && "Listed Lena".equals(row.path("employeeName").asText())) {
                salaryRow = !row.path("paymentType").asText().isEmpty();
            }
            if ("ELECTRICITY".equals(row.get("categoryType").asText()) && "2_NO".equals(row.path("connectionNumber").asText())
                    && "3.00".equals(row.path("usageQuantity").asText())) {
                electricityRow = true;
            }
        }
        assertThat(salaryRow).as("salary row carries employee name and payment type").isTrue();
        assertThat(electricityRow).as("electricity row carries connection and usage").isTrue();
    }
}
