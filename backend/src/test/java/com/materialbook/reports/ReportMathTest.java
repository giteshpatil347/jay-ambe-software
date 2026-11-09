package com.materialbook.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.materialbook.reports.ReportDtos.Dashboard;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ReportMathTest extends AbstractReportTest {
    @Autowired DashboardService dashboards;

    /** DashboardService reads the caller's authorities from the SecurityContext; this runs it as a user holding every dashboard permission. */
    @BeforeEach
    void actAsFullDashboardUser() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("t", "n", "MATERIAL_IN.VIEW",
                "MATERIAL_OUT.VIEW", "INVENTORY.VIEW", "DASHBOARD.FINANCIAL"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private long supplier;
    private long productA;
    private long productB;

    /** Two posted IN documents, one with a superseded item, plus one cancelled document. Units differ per product. */
    private void inFixture() {
        supplier = party("Math Supplier", "SUPPLIER", "0");
        productA = product("Math TON product", "TON", 0);
        productB = product("Math KG product", "KG", 0);
        long t1 = tx("IN", "2091-01-10", supplier, "POSTED");
        item(t1, productA, "10", "1000", false);
        item(t1, productB, "5", "200", false);
        recalc(t1);
        long t2 = tx("IN", "2091-01-11", supplier, "POSTED");
        item(t2, productA, "2.5", "1000", false);
        item(t2, productA, "99", "1000", true);
        recalc(t2);
        long t3 = tx("IN", "2091-01-12", supplier, "CANCELLED");
        item(t3, productA, "7", "1000", false);
        recalc(t3);
    }

    @Test
    void listExcludesSupersededItemsAndSummaryCountsPostedOnlyPerUnit() throws Exception {
        inFixture();
        JsonNode r = tree(get("/api/v1/reports/material-in?partyId=" + supplier, admin));
        assertThat(r.get("totalElements").asInt()).isEqualTo(3);
        assertThat(r.get("content").get(0).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(r.get("content").get(2).get("amount").asText()).isEqualTo("11000.00");
        assertThat(r.get("content").get(2).get("itemCount").asInt()).isEqualTo(2);
        assertThat(r.get("content").get(1).get("amount").asText()).isEqualTo("2500.00");
        assertThat(r.get("content").get(1).get("itemCount").asInt()).isEqualTo(1);

        JsonNode s = r.get("summary");
        assertThat(s.get("transactionCount").asInt()).isEqualTo(2);
        assertThat(s.get("totalAmount").asText()).isEqualTo("13500.00");
        assertThat(s.get("quantityByUnit")).hasSize(2);
        assertThat(s.get("quantityByUnit").get(0).get("unit").asText()).isEqualTo("KG");
        assertThat(s.get("quantityByUnit").get(0).get("quantity").asText()).isEqualTo("5.000");
        assertThat(s.get("quantityByUnit").get(1).get("unit").asText()).isEqualTo("TON");
        assertThat(s.get("quantityByUnit").get(1).get("quantity").asText()).isEqualTo("12.500");
    }

    @Test
    void filtersByStatusProductAndDateRange() throws Exception {
        inFixture();
        JsonNode cancelled = tree(get("/api/v1/reports/material-in?partyId=" + supplier + "&status=CANCELLED", admin));
        assertThat(cancelled.get("totalElements").asInt()).isEqualTo(1);
        assertThat(cancelled.get("summary").get("totalAmount").asText()).isEqualTo("0.00");

        JsonNode byProduct = tree(get("/api/v1/reports/material-in?productId=" + productB, admin));
        assertThat(byProduct.get("totalElements").asInt()).isEqualTo(1);
        assertThat(byProduct.get("content").get(0).get("amount").asText()).isEqualTo("1000.00");
        assertThat(byProduct.get("summary").get("quantityByUnit")).hasSize(1);

        JsonNode range = tree(get("/api/v1/reports/material-in?partyId=" + supplier + "&from=2091-01-11&to=2091-01-11", admin));
        assertThat(range.get("summary").get("totalAmount").asText()).isEqualTo("2500.00");

        assertThat(status(get("/api/v1/reports/material-in?from=2091-02-01&to=2091-01-01", admin))).isEqualTo(422);
        assertThat(status(get("/api/v1/reports/material-in?size=201", admin))).isEqualTo(422);
        assertThat(status(get("/api/v1/reports/material-in?groupBy=nope", admin))).isEqualTo(422);
    }

    @Test
    void outReportIsSeparateFromIn() throws Exception {
        inFixture();
        long client = party("Math Client", "CLIENT", "0");
        long t = tx("OUT", "2091-01-15", client, "POSTED");
        item(t, productA, "1", "1500", false);
        recalc(t);
        JsonNode out = tree(get("/api/v1/reports/material-out?productId=" + productA, admin));
        assertThat(out.get("totalElements").asInt()).isEqualTo(1);
        assertThat(out.get("summary").get("totalAmount").asText()).isEqualTo("1500.00");
        assertThat(tree(get("/api/v1/reports/material-in?partyId=" + client, admin)).get("totalElements").asInt()).isZero();
    }

    @Test
    void groupByProductPartyAndDayNeverMixUnits() throws Exception {
        inFixture();
        JsonNode byProduct = tree(get("/api/v1/reports/material-in?partyId=" + supplier + "&groupBy=product", admin));
        assertThat(byProduct.get("totalElements").asInt()).isEqualTo(2);
        JsonNode first = byProduct.get("content").get(0);
        assertThat(first.get("label").asText()).isEqualTo("Math KG product");
        assertThat(first.get("amount").asText()).isEqualTo("1000.00");
        assertThat(first.get("quantities")).hasSize(1);
        JsonNode second = byProduct.get("content").get(1);
        assertThat(second.get("amount").asText()).isEqualTo("12500.00");
        assertThat(second.get("transactionCount").asInt()).isEqualTo(2);
        assertThat(second.get("quantities").get(0).get("unit").asText()).isEqualTo("TON");
        assertThat(second.get("quantities").get(0).get("quantity").asText()).isEqualTo("12.500");

        JsonNode byParty = tree(get("/api/v1/reports/material-in?partyId=" + supplier + "&groupBy=party", admin));
        assertThat(byParty.get("totalElements").asInt()).isEqualTo(1);
        JsonNode row = byParty.get("content").get(0);
        assertThat(row.get("amount").asText()).isEqualTo("13500.00");
        assertThat(row.get("transactionCount").asInt()).isEqualTo(2);
        assertThat(row.get("quantities")).hasSize(2);

        JsonNode byDay = tree(get("/api/v1/reports/material-in?partyId=" + supplier + "&groupBy=day", admin));
        assertThat(byDay.get("content").get(0).get("key").asText()).isEqualTo("2091-01-11");
        assertThat(byDay.get("content").get(1).get("amount").asText()).isEqualTo("11000.00");
    }

    @Test
    void expenseReportTotalsPostedByCategory() throws Exception {
        long rent = expenseCategory("Math Rent");
        long tea = expenseCategory("Math Tea");
        expense(rent, "2091-02-01", "100.00", "POSTED");
        expense(rent, "2091-02-02", "250.50", "POSTED");
        expense(rent, "2091-02-03", "999.00", "CANCELLED");
        expense(tea, "2091-02-04", "40.00", "POSTED");

        JsonNode all = tree(get("/api/v1/reports/expenses?from=2091-02-01&to=2091-02-28", admin));
        assertThat(all.get("totalElements").asInt()).isEqualTo(4);
        assertThat(all.get("summary").get("totalAmount").asText()).isEqualTo("390.50");
        assertThat(all.get("summary").get("byCategory").get(0).get("categoryName").asText()).startsWith("Math Rent");
        assertThat(all.get("summary").get("byCategory").get(0).get("amount").asText()).isEqualTo("350.50");
        assertThat(all.get("summary").get("byCategory").get(1).get("count").asInt()).isEqualTo(1);

        JsonNode one = tree(get("/api/v1/reports/expenses?categoryId=" + tea, admin));
        assertThat(one.get("totalElements").asInt()).isEqualTo(1);
        assertThat(one.get("summary").get("totalAmount").asText()).isEqualTo("40.00");
    }

    @Test
    void stockReportCurrentAndAsOfWithLowFlag() throws Exception {
        long p = product("Math Stock", "KG", 22);
        jdbc.update("insert into stock_movements(product_id, movement_type, quantity_delta, movement_date, created_by) values (?, 'OPENING', 20, '2091-03-01', ?)", p, adminId);
        jdbc.update("insert into stock_movements(product_id, movement_type, quantity_delta, movement_date, created_by) values (?, 'OPENING', 5, '2091-03-10', ?)", p, adminId);
        jdbc.update("update stock_balances set quantity = 25 where product_id = ?", p);

        JsonNode now = tree(get("/api/v1/reports/stock?productId=" + p, admin));
        assertThat(now.get("content").get(0).get("quantity").asText()).isEqualTo("25.000");
        assertThat(now.get("content").get(0).get("lowStock").asBoolean()).isFalse();
        assertThat(now.get("summary").get("lowStockCount").asInt()).isZero();

        JsonNode past = tree(get("/api/v1/reports/stock?productId=" + p + "&asOf=2091-03-05", admin));
        assertThat(past.get("content").get(0).get("quantity").asText()).isEqualTo("20.000");
        assertThat(past.get("content").get(0).get("lowStock").asBoolean()).isTrue();
        assertThat(past.get("summary").get("lowStockCount").asInt()).isEqualTo(1);

        JsonNode before = tree(get("/api/v1/reports/stock?productId=" + p + "&asOf=2091-02-01", admin));
        assertThat(before.get("content").get(0).get("quantity").asText()).isEqualTo("0.000");
        assertThat(tree(get("/api/v1/reports/stock?productId=" + p + "&asOf=2091-03-05&lowStockOnly=true", admin)).get("totalElements").asInt()).isEqualTo(1);
        assertThat(tree(get("/api/v1/reports/stock?productId=" + p + "&lowStockOnly=true", admin)).get("totalElements").asInt()).isZero();
    }

    @Test
    void dashboardFigures() throws Exception {
        LocalDate today = LocalDate.of(2092, 6, 15);
        Dashboard before = dashboards.dashboard(today);

        long supplierY = party("Dash Supplier", "SUPPLIER", "-200");
        party("Dash Client", "CLIENT", "300");
        long prod = product("Dash product", "KG", 100);
        long in1 = tx("IN", "2092-06-15", supplierY, "POSTED");
        item(in1, prod, "1", "1000", false);
        recalc(in1);
        long in2 = tx("IN", "2092-06-01", supplierY, "POSTED");
        item(in2, prod, "1", "400", false);
        recalc(in2);
        long in3 = tx("IN", "2092-05-20", supplierY, "POSTED");
        item(in3, prod, "1", "50", false);
        recalc(in3);
        long in4 = tx("IN", "2092-06-15", supplierY, "CANCELLED");
        item(in4, prod, "1", "5000", false);
        recalc(in4);
        long out = tx("OUT", "2092-06-15", null, "POSTED");
        item(out, prod, "1", "700", false);
        recalc(out);
        long cat = expenseCategory("Dash Cat");
        expense(cat, "2092-06-15", "30.00", "POSTED");
        expense(cat, "2092-06-02", "20.00", "POSTED");
        expense(cat, "2092-06-15", "777.00", "CANCELLED");

        Dashboard d = dashboards.dashboard(today);
        // default period = 30 days ending today (05-17 .. 06-15): three IN items of 1 KG each (06-15, 06-01, 05-20), 1 KG OUT
        assertThat(d.periodTotals().inTons()).isEqualByComparingTo("0.003"); // 1 KG = 0.001 TON
        assertThat(d.periodTotals().outTons()).isEqualByComparingTo("0.001");
        assertThat(d.periodTotals().expenses()).isEqualByComparingTo("50");
        assertThat(d.series()).hasSize(30);
        assertThat(d.series().get(29).date()).isEqualTo(today);
        assertThat(d.series().get(0).date()).isEqualTo(today.minusDays(29));
        assertThat(d.series().stream().filter(p -> p.date().equals(LocalDate.of(2092, 5, 20))).findFirst().orElseThrow().inTons())
                .isEqualByComparingTo("0.001");
        assertThat(d.series().get(29).expenses()).isEqualByComparingTo("30");

        // a selected period narrows totals and series: 1 day = today only; 06-01..06-15 leaves out the 05-20 entry
        Dashboard oneDay = dashboards.dashboard(today, today, today);
        assertThat(oneDay.series()).hasSize(1);
        assertThat(oneDay.periodTotals().inTons()).isEqualByComparingTo("0.001");
        assertThat(oneDay.periodTotals().expenses()).isEqualByComparingTo("30");
        Dashboard june = dashboards.dashboard(today, LocalDate.of(2092, 6, 1), today);
        assertThat(june.periodTotals().inTons()).isEqualByComparingTo("0.002");
        assertThat(june.series()).hasSize(15);

        assertThat(d.receivable().subtract(before.receivable())).isEqualByComparingTo("300");
        assertThat(d.payable().subtract(before.payable())).isEqualByComparingTo(new BigDecimal("1650"));
        assertThat(d.lowStockCount() - before.lowStockCount()).isEqualTo(1);
        assertThat(d.lowStock().size()).isLessThanOrEqualTo(5);
        assertThat(d.recent().size()).isBetween(1, 10);
        assertThat(d.recent().get(0).transactionDate()).isEqualTo(today);
        assertThat(d.recent()).noneMatch(t -> t.totalAmount().compareTo(new BigDecimal("5000")) == 0);
    }

    @Test
    void dashboardEndpointShape() throws Exception {
        JsonNode r = tree(get("/api/v1/reports/dashboard", admin));
        assertThat(r.fieldNames()).toIterable().contains("today", "from", "to", "periodTotals", "receivable", "payable", "lowStockCount",
                "lowStock", "series", "recent");
        assertThat(r.get("series")).hasSize(30);
    }

    @Test
    void materialReportRowsCarryQuantityPerUnit() throws Exception {
        long supplier = party("Qty Supplier", "SUPPLIER", "0");
        long ton = product("Qty ton", "TON", 0);
        long kg = product("Qty kg", "KG", 0);
        long t = tx("IN", "2090-01-01", supplier, "POSTED"); // earlier than every other fixture: the dashboard test expects ITS entry to be the newest
        item(t, ton, "2.5", "100", false);
        item(t, kg, "40", "5", false);
        recalc(t);
        JsonNode row = tree(get("/api/v1/reports/material-in?partyId=" + supplier, admin)).get("content").get(0);
        assertThat(row.get("itemCount").asInt()).isEqualTo(2);
        assertThat(row.get("quantities")).hasSize(2);
        assertThat(row.get("quantities").get(0).get("unit").asText()).isEqualTo("KG");
        assertThat(row.get("quantities").get(0).get("quantity").asText()).isEqualTo("40.000");
        assertThat(row.get("quantities").get(1).get("unit").asText()).isEqualTo("TON");
        assertThat(row.get("quantities").get(1).get("quantity").asText()).isEqualTo("2.500");
    }
}
