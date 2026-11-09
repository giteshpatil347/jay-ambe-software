package com.materialbook.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

class ExpenseCategoryApiTest extends AbstractExpenseApiTest {

    @Test
    void createListUpdateAndAudit() throws Exception {
        String code = "CAT_" + System.nanoTime();
        MvcResult r = call(HttpMethod.POST, "/api/v1/expense-categories", admin,
                Map.of("categoryCode", code, "categoryName", "Tea " + code, "categoryType", "GENERAL"));
        assertThat(status(r)).isEqualTo(201);
        long id = tree(r).get("id").asLong();
        assertThat(tree(r).get("fixed").asBoolean()).isFalse();
        assertThat(auditCount("CREATE", "EXPENSE_CATEGORY", id)).isEqualTo(1);

        MvcResult u = call(HttpMethod.PUT, "/api/v1/expense-categories/" + id, admin,
                Map.of("categoryName", "Tea 2 " + code, "status", "INACTIVE"));
        assertThat(status(u)).isEqualTo(200);
        assertThat(tree(u).get("status").asText()).isEqualTo("INACTIVE");
        assertThat(tree(u).get("categoryType").asText()).isEqualTo("GENERAL");
        assertThat(auditCount("UPDATE", "EXPENSE_CATEGORY", id)).isEqualTo(1);

        JsonNode active = tree(call(HttpMethod.GET, "/api/v1/expense-categories?status=ACTIVE", admin, null));
        for (JsonNode c : active) {
            assertThat(c.get("categoryCode").asText()).isNotEqualTo(code);
        }
        assertThat(status(call(HttpMethod.POST, "/api/v1/expenses", admin, expenseBody(id, "5.00")))).isEqualTo(422);
    }

    @Test
    void duplicateCodeOrNameIsConflict() throws Exception {
        String code = "DUP_" + System.nanoTime();
        createCategory(code, "Dup " + code, "GENERAL");
        assertThat(status(call(HttpMethod.POST, "/api/v1/expense-categories", admin,
                Map.of("categoryCode", code, "categoryName", "Other " + code, "categoryType", "GENERAL")))).isEqualTo(409);
        assertThat(status(call(HttpMethod.POST, "/api/v1/expense-categories", admin,
                Map.of("categoryCode", code + "X", "categoryName", "dup " + code, "categoryType", "GENERAL")))).isEqualTo(409);
        assertThat(status(call(HttpMethod.POST, "/api/v1/expense-categories", admin,
                Map.of("categoryCode", "bad code", "categoryName", "N", "categoryType", "GENERAL")))).isEqualTo(422);
    }

    @Test
    void fixedCategoriesAreProtected() throws Exception {
        assertThat(status(call(HttpMethod.PUT, "/api/v1/expense-categories/" + salaryCategory, admin,
                Map.of("categoryName", "Renamed", "status", "ACTIVE")))).isEqualTo(422);
        assertThat(status(call(HttpMethod.PUT, "/api/v1/expense-categories/" + electricityCategory, admin,
                Map.of("categoryName", "Electricity", "status", "INACTIVE")))).isEqualTo(422);
        assertThat(status(call(HttpMethod.PUT, "/api/v1/expense-categories/" + electricityCategory, admin,
                Map.of("categoryName", "Electricity", "status", "ACTIVE")))).isEqualTo(200);
    }

    @Test
    void permissionsAreEnforced() throws Exception {
        String viewer = tokenWithRole("VIEWER");
        String accountant = tokenWithRole("ACCOUNTANT");
        assertThat(status(call(HttpMethod.GET, "/api/v1/expense-categories", viewer, null))).isEqualTo(403); // VIEWER is limited to material + stock
        assertThat(status(call(HttpMethod.POST, "/api/v1/expense-categories", viewer,
                Map.of("categoryCode", "VIEW_X", "categoryName", "X", "categoryType", "GENERAL")))).isEqualTo(403);
        assertThat(status(call(HttpMethod.PUT, "/api/v1/expense-categories/" + generalCategory, viewer,
                Map.of("categoryName", "X", "status", "ACTIVE")))).isEqualTo(403);
        assertThat(status(call(HttpMethod.PUT, "/api/v1/expense-categories/" + generalCategory, accountant,
                Map.of("categoryName", "Acc " + System.nanoTime(), "status", "ACTIVE")))).isEqualTo(200);
    }
}
