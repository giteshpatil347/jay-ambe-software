package com.materialbook.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The dashboard API omits (and does not even query) every section the caller has no permission for. */
class DashboardPermissionTest extends AbstractReportTest {
    private static final String PASSWORD = "Temporary-Pass-1";
    private static final List<String> VIEWER_PERMISSIONS =
            List.of("DASHBOARD.VIEW", "INVENTORY.VIEW", "MATERIAL_IN.VIEW", "MATERIAL_OUT.VIEW", "PRODUCT.VIEW");

    private JsonNode dashboard(String token) throws Exception {
        var r = get("/api/v1/reports/dashboard", token);
        assertThat(status(r)).isEqualTo(200);
        return tree(r);
    }

    private static void assertNoFinancial(JsonNode d) {
        assertThat(d.has("expenses")).isFalse();
        assertThat(d.get("periodTotals").has("expenses")).isFalse();
        assertThat(d.has("receivable")).isFalse();
        assertThat(d.has("payable")).isFalse();
        assertThat(d.has("recent")).isFalse();
        if (d.has("series")) {
            d.get("series").forEach(p -> assertThat(p.has("expenses")).isFalse());
        }
    }

    @Test
    void adminSeesEverySection() throws Exception {
        long prod = product("Perm product", "KG", 100);
        long in = tx("IN", java.time.LocalDate.now(ReportTable.IST).toString(), party("Perm supplier", "SUPPLIER", "0"), "POSTED");
        item(in, prod, "1", "10", false);
        recalc(in);
        JsonNode d = dashboard(admin);
        assertThat(d.fieldNames()).toIterable().contains("today", "from", "to", "periodTotals", "receivable", "payable",
                "lowStockCount", "lowStock", "series", "recent");
        assertThat(d.get("periodTotals").fieldNames()).toIterable().containsExactlyInAnyOrder("inTons", "outTons", "expenses");
        assertThat(d.get("series").get(0).fieldNames()).toIterable().containsExactlyInAnyOrder("date", "inTons", "outTons", "balanceTons", "expenses");
        assertThat(d.get("recent")).isNotEmpty();
    }

    @Test
    void viewerSeesOnlyMaterialAndStock() throws Exception {
        JsonNode d = dashboard(tokenFor("VIEWER"));
        assertThat(d.get("periodTotals").fieldNames()).toIterable().containsExactlyInAnyOrder("inTons", "outTons");
        assertThat(d.get("series")).hasSize(30);
        assertThat(d.get("series").get(0).fieldNames()).toIterable().containsExactlyInAnyOrder("date", "inTons", "outTons", "balanceTons");
        assertThat(d.has("lowStockCount")).isTrue();
        assertThat(d.has("lowStock")).isTrue();
        assertNoFinancial(d);
    }

    @Test
    void expenseViewPermissionAloneDoesNotRevealExpenses() throws Exception {
        long role = jdbc.queryForObject("insert into roles(role_code, role_name) values ('DASH_NOFIN', 'No financial') returning id", Long.class);
        jdbc.update("insert into role_permissions(role_id, permission_id) select ?, id from permissions where permission_code in ('DASHBOARD.VIEW', 'EXPENSE.VIEW')", role);
        JsonNode d = dashboard(tokenFor("DASH_NOFIN"));
        assertThat(d.fieldNames()).toIterable().containsExactlyInAnyOrder("today", "from", "to", "periodTotals");
        assertThat(d.get("periodTotals").size()).isZero();
        assertThat(d.has("series")).isFalse();
        assertNoFinancial(d);
    }

    @Test
    void userLevelAllowOverrideOfFinancialRevealsTheFinancialSections() throws Exception {
        tokenFor("VIEWER");
        String mobile = jdbc.queryForObject("select mobile from users order by id desc limit 1", String.class);
        jdbc.update("insert into user_permission_overrides(user_id, permission_id, effect) select u.id, p.id, 'ALLOW' from users u, permissions p"
                + " where u.mobile = ? and p.permission_code = 'DASHBOARD.FINANCIAL'", mobile);
        JsonNode d = dashboard(login(mobile, PASSWORD));
        assertThat(d.fieldNames()).toIterable().contains("receivable", "payable", "recent", "series");
        assertThat(d.get("periodTotals").has("expenses")).isTrue();
        assertThat(d.get("series").get(0).has("expenses")).isTrue();
    }

    @Test
    void purchaseStaffSeesInButNotOut() throws Exception {
        JsonNode d = dashboard(tokenFor("PURCHASE_STAFF"));
        assertThat(d.get("periodTotals").fieldNames()).toIterable().containsExactly("inTons");
        assertThat(d.get("series").get(0).fieldNames()).toIterable().containsExactlyInAnyOrder("date", "inTons", "balanceTons");
        assertNoFinancial(d);
    }

    @Test
    void viewerRoleIsExactlyTheReducedSet() {
        List<String> codes = jdbc.queryForList("""
                select p.permission_code from role_permissions rp join roles r on r.id = rp.role_id
                join permissions p on p.id = rp.permission_id where r.role_code = 'VIEWER' order by 1""", String.class);
        assertThat(codes).containsExactlyElementsOf(VIEWER_PERMISSIONS);
        List<String> financial = jdbc.queryForList("""
                select r.role_code from role_permissions rp join roles r on r.id = rp.role_id
                join permissions p on p.id = rp.permission_id where p.permission_code = 'DASHBOARD.FINANCIAL' order by 1""", String.class);
        assertThat(financial).containsExactly("ACCOUNTANT", "ADMIN", "SUPER_ADMIN");
    }

    @Test
    void periodSelectionControlsTheSeriesAndIsValidated() throws Exception {
        java.time.LocalDate today = java.time.LocalDate.now(ReportTable.IST);
        var one = tree(get("/api/v1/reports/dashboard?from=" + today + "&to=" + today, admin));
        assertThat(one.get("series")).hasSize(1);
        assertThat(one.get("from").asText()).isEqualTo(today.toString());
        assertThat(tree(get("/api/v1/reports/dashboard?from=" + today.minusDays(6) + "&to=" + today, admin)).get("series")).hasSize(7);
        // a future 'to' is clamped to today; a missing 'from' means 30 days ending at 'to'
        var clamped = tree(get("/api/v1/reports/dashboard?to=2999-01-01", admin));
        assertThat(clamped.get("to").asText()).isEqualTo(today.toString());
        assertThat(clamped.get("series")).hasSize(30);
        assertThat(status(get("/api/v1/reports/dashboard?from=" + today + "&to=" + today.minusDays(1), admin))).isEqualTo(422);
        assertThat(status(get("/api/v1/reports/dashboard?from=" + today.minusDays(400) + "&to=" + today, admin))).isEqualTo(422);
        assertThat(status(get("/api/v1/reports/dashboard?from=not-a-date", admin))).isEqualTo(400);
    }
}
