package com.materialbook.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class ReportExportTest extends AbstractReportTest {
    private long supplier;
    private long productA;
    private long inTx;
    private long cancelledTx;
    private long outTx;
    private long expenseCat;

    @BeforeEach
    void fixtures() {
        supplier = party("Export Supplier Pvt Ltd", "BOTH", "0");
        productA = product("Export Steel", "TON", 0);
        inTx = tx("IN", "2093-01-10", supplier, "POSTED");
        item(inTx, productA, "12.5", "15000", false);
        item(inTx, productA, "99", "15000", true);
        recalc(inTx);
        cancelledTx = tx("IN", "2093-01-11", supplier, "CANCELLED");
        item(cancelledTx, productA, "1", "100", false);
        recalc(cancelledTx);
        outTx = tx("OUT", "2093-01-12", supplier, "POSTED");
        item(outTx, productA, "2", "20000", false);
        recalc(outTx);
        expenseCat = expenseCategory("Export Rent");
        expense(expenseCat, "2093-02-01", "1250.75", "POSTED");
        jdbc.update("insert into party_payments(payment_number, party_id, payment_date, direction, amount, created_by) values (?, ?, '2093-01-20', 'PAID', 87500, ?)",
                "RPAY-" + supplier, supplier, adminId);
    }

    private static String pdfText(MvcResult r) throws Exception {
        try (PDDocument doc = Loader.loadPDF(bytes(r))) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private static String csv(MvcResult r) throws Exception {
        return new String(bytes(r), StandardCharsets.UTF_8);
    }

    private void assertAttachment(MvcResult r, String type, String ext) {
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(r.getResponse().getContentType()).startsWith(type);
        assertThat(r.getResponse().getHeader("Content-Disposition")).startsWith("attachment; filename=\"").contains("." + ext + "\"");
        assertThat(r.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(r.getResponse().getContentAsByteArray()).isNotEmpty();
    }

    @Test
    void materialInPdfAndCsv() throws Exception {
        String url = "/api/v1/reports/material-in/export?partyId=" + supplier + "&format=";
        MvcResult pdf = get(url + "pdf", admin);
        assertAttachment(pdf, "application/pdf", "pdf");
        assertThat(pdf.getResponse().getHeader("Content-Disposition")).contains("material-in-report-");
        String text = pdfText(pdf);
        assertThat(text).contains("Material IN Report", "MaterialBook", "₹", "Export Supplier Pvt Ltd", "₹1,87,500.00", "Total (POSTED)",
                "TON 12.500", "CANCELLED").doesNotContain("99.000");

        MvcResult csv = get(url + "csv", admin);
        assertAttachment(csv, "text/csv", "csv");
        String body = csv(csv);
        assertThat(body).startsWith("﻿Number,Date,Party,Challan,Vehicle,Status,Material,Unit,Quantity,Rate,Amount\r\n");
        assertThat(body).contains(",Export Steel,TON,12.500,15000.00,187500.00\r\n").contains(",CANCELLED,Export Steel,TON,1.000,100.00,100.00");
        assertThat(body).endsWith("Total (POSTED),,,,,,,,,,187500.00\r\n");
    }

    @Test
    void materialOutExpensesStockPdfAndCsv() throws Exception {
        String out = pdfText(get("/api/v1/reports/material-out/export?partyId=" + supplier + "&format=pdf", admin));
        assertThat(out).contains("Material OUT Report", "₹40,000.00");

        MvcResult expensesPdf = get("/api/v1/reports/expenses/export?categoryId=" + expenseCat + "&format=pdf", admin);
        assertAttachment(expensesPdf, "application/pdf", "pdf");
        assertThat(pdfText(expensesPdf)).contains("Expense Report", "₹1,250.75", "Export Rent");
        assertThat(csv(get("/api/v1/reports/expenses/export?categoryId=" + expenseCat + "&format=csv", admin))).contains(",1250.75\r\n");

        MvcResult stockPdf = get("/api/v1/reports/stock/export?productId=" + productA + "&format=pdf", admin);
        assertAttachment(stockPdf, "application/pdf", "pdf");
        assertThat(pdfText(stockPdf)).contains("Stock Report", "Export Steel", "Current stock");
        assertThat(csv(get("/api/v1/reports/stock/export?productId=" + productA + "&format=csv&asOf=2093-12-31", admin))).startsWith("﻿Code,Material");
    }

    @Test
    void ledgerExportKeepsSignsAndReceivablePayableWording() throws Exception {
        String base = "/api/v1/reports/parties/" + supplier + "/ledger/export?format=";
        MvcResult pdf = get(base + "pdf", admin);
        assertAttachment(pdf, "application/pdf", "pdf");
        String text = pdfText(pdf);
        // IN 187500 lowers the balance (we owe), OUT 40000 raises it, PAID 87500 raises it: -187500 + 40000 + 87500 = -60000
        assertThat(text).contains("Party Ledger - Export Supplier Pvt Ltd", "Receivable", "Payable", "-₹60,000.00");
        String body = csv(get(base + "csv", admin));
        assertThat(body).contains("Payment paid").contains(",-60000.00,Payable\r\n");
        assertThat(csv(get(base + "csv&from=2093-01-15&to=2093-01-31", admin))).contains("Opening balance,,,,-147500.00,Payable");
        assertThat(status(get("/api/v1/reports/parties/999999999/ledger/export?format=csv", admin))).isEqualTo(404);
    }

    @Test
    void transactionChallanShowsItemsAndCancelledMarker() throws Exception {
        MvcResult in = get("/api/v1/reports/transactions/" + inTx + "/pdf", admin);
        assertAttachment(in, "application/pdf", "pdf");
        String text = pdfText(in);
        assertThat(text).contains("Material IN Voucher RIN-", "Export Supplier Pvt Ltd", "MH12AB1234", "Ramesh", "Export Steel", "TON", "12.500",
                "₹1,87,500.00").doesNotContain("CANCELLED").doesNotContain("99.000");

        String cancelled = pdfText(get("/api/v1/reports/transactions/" + cancelledTx + "/pdf", admin));
        assertThat(cancelled).contains("CANCELLED", "Entered twice");

        assertThat(pdfText(get("/api/v1/reports/transactions/" + outTx + "/pdf", admin))).contains("Material OUT Challan", "₹40,000.00");
        assertThat(status(get("/api/v1/reports/transactions/999999999/pdf", admin))).isEqualTo(404);
    }

    @Test
    void invalidFormatIs422AndExportsAreAudited() throws Exception {
        assertThat(status(get("/api/v1/reports/material-in/export?format=xlsx", admin))).isEqualTo(422);
        assertThat(status(get("/api/v1/reports/material-in/export", admin))).isEqualTo(400);

        long before = jdbc.queryForObject("select count(*) from audit_logs where action = 'EXPORT' and module = 'REPORT'", Long.class);
        get("/api/v1/reports/material-in/export?partyId=" + supplier + "&format=csv", admin);
        get("/api/v1/reports/transactions/" + inTx + "/pdf", admin);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'EXPORT' and module = 'REPORT'", Long.class)).isEqualTo(before + 2);
        List<String> filters = jdbc.queryForList("select new_values::text from audit_logs where action = 'EXPORT' and module = 'REPORT' and record_id is null"
                + " order by id desc limit 1", String.class);
        assertThat(filters.get(0)).contains("\"partyId\": " + supplier).contains("\"format\": \"csv\"").contains("\"rows\": 2");
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'EXPORT' and record_id = ?", Long.class, inTx)).isEqualTo(1);
    }

    @Test
    void exportAboveTwentyThousandRowsIsRejected() throws Exception {
        long big = product("Export Bulk", "PIECE", 0);
        long t = tx("IN", "2093-05-05", supplier, "POSTED");
        jdbc.update("insert into material_transaction_items(transaction_id, product_id, quantity, rate) select ?, ?, 1, 0 from generate_series(1, 20001)", t, big);
        String url = "/api/v1/reports/material-in/export?productId=" + big + "&format=";
        try {
            for (String format : List.of("csv", "pdf")) {
                MvcResult r = get(url + format, admin);
                assertThat(status(r)).isEqualTo(422);
                assertThat(r.getResponse().getContentAsString()).contains("narrow the filters");
            }
            jdbc.update("delete from material_transaction_items where id = (select max(id) from material_transaction_items where transaction_id = ?)", t);
            MvcResult atCap = get(url + "csv", admin);
            assertThat(status(atCap)).isEqualTo(200);
            assertThat(csv(atCap).split("\r\n")).hasSize(20_002); // header + 20,000 lines + totals
        } finally {
            jdbc.update("delete from material_transaction_items where transaction_id = ?", t); // keep later unfiltered exports under the cap
            jdbc.update("delete from material_transactions where id = ?", t);
        }
    }

    @Test
    void permissionMatrix() throws Exception {
        String viewer = tokenFor("VIEWER");
        String accountant = tokenFor("ACCOUNTANT");
        String purchase = tokenFor("PURCHASE_STAFF");

        // VIEWER: dashboard only (no REPORT.VIEW since V8), never export
        assertThat(status(get("/api/v1/reports/dashboard", viewer))).isEqualTo(200);
        for (String url : List.of("/api/v1/reports/material-in", "/api/v1/reports/material-out", "/api/v1/reports/expenses",
                "/api/v1/reports/stock", "/api/v1/expenses", "/api/v1/payments", "/api/v1/parties", "/api/v1/audit-logs")) {
            assertThat(status(get(url, viewer))).as(url).isEqualTo(403);
        }
        for (String url : List.of("/api/v1/reports/material-in/export?partyId=" + supplier + "&format=csv", "/api/v1/reports/material-out/export?partyId=" + supplier + "&format=csv",
                "/api/v1/reports/expenses/export?categoryId=" + expenseCat + "&format=csv", "/api/v1/reports/stock/export?productId=" + productA + "&format=csv",
                "/api/v1/reports/parties/" + supplier + "/ledger/export?format=csv", "/api/v1/reports/transactions/" + inTx + "/pdf")) {
            assertThat(status(get(url, viewer))).as(url).isEqualTo(403);
        }

        // ACCOUNTANT (V3): REPORT.* + module exports + PARTY.VIEW
        for (String url : List.of("/api/v1/reports/material-in/export?partyId=" + supplier + "&format=csv", "/api/v1/reports/material-out/export?partyId=" + supplier + "&format=csv",
                "/api/v1/reports/expenses/export?categoryId=" + expenseCat + "&format=csv", "/api/v1/reports/stock/export?productId=" + productA + "&format=pdf",
                "/api/v1/reports/parties/" + supplier + "/ledger/export?format=csv", "/api/v1/reports/transactions/" + inTx + "/pdf",
                "/api/v1/reports/transactions/" + outTx + "/pdf")) {
            assertThat(status(get(url, accountant))).as(url).isEqualTo(200);
        }

        // PURCHASE_STAFF: dashboard yes, reports and exports no
        assertThat(status(get("/api/v1/reports/dashboard", purchase))).isEqualTo(200);
        assertThat(status(get("/api/v1/reports/material-in", purchase))).isEqualTo(403);
        assertThat(status(get("/api/v1/reports/material-in/export?format=csv", purchase))).isEqualTo(403);
        assertThat(status(get("/api/v1/reports/transactions/" + inTx + "/pdf", purchase))).isEqualTo(403);
    }

    @Test
    void exportNeedsBothReportAndModuleExportPermission() throws Exception {
        long role = jdbc.queryForObject("insert into roles(role_code, role_name) values ('T13_OUT_ONLY', 'T13 out only') on conflict (role_code) do update"
                + " set role_name = excluded.role_name returning id", Long.class);
        jdbc.update("insert into role_permissions(role_id, permission_id) select ?, id from permissions where permission_code in"
                + " ('REPORT.EXPORT', 'MATERIAL_OUT.EXPORT') on conflict do nothing", role);
        String token = tokenFor("T13_OUT_ONLY");
        assertThat(status(get("/api/v1/reports/material-out/export?format=csv", token))).isEqualTo(200);
        assertThat(status(get("/api/v1/reports/material-in/export?format=csv", token))).isEqualTo(403);
        assertThat(status(get("/api/v1/reports/transactions/" + outTx + "/pdf", token))).isEqualTo(200);
        assertThat(status(get("/api/v1/reports/transactions/" + inTx + "/pdf", token))).isEqualTo(403);
        assertThat(status(get("/api/v1/reports/parties/" + supplier + "/ledger/export?format=csv", token))).isEqualTo(403);
    }

    @Test
    void columnsParamSelectsColumnsForEveryExport() throws Exception {
        String base = "/api/v1/reports/material-in/export?partyId=" + supplier;
        String csv = csv(get(base + "&format=csv&columns=Amount&columns=Number&columns=Date", admin));
        assertThat(csv).startsWith("﻿Number,Date,Amount\r\n").doesNotContain("Party");
        assertThat(csv).endsWith("Total (POSTED),,187500.00\r\n");
        String pdf = pdfText(get(base + "&format=pdf&columns=Date&columns=Amount", admin));
        assertThat(pdf).contains("Date", "Amount", "Total (POSTED)", "₹1,87,500.00").doesNotContain("Challan", "Vehicle");

        assertThat(csv(get("/api/v1/reports/expenses/export?categoryId=" + expenseCat + "&format=csv&columns=Category&columns=Amount", admin)))
                .startsWith("﻿Category,Amount\r\n").contains("Export Rent").contains(",1250.75\r\n");
        assertThat(csv(get("/api/v1/reports/stock/export?productId=" + productA + "&format=csv&columns=Material&columns=Status", admin)))
                .startsWith("﻿Material,Status\r\n");
        assertThat(csv(get("/api/v1/reports/material-out/export?format=csv&columns=Material", admin))).startsWith("﻿Material\r\n");
        String ledger = csv(get("/api/v1/reports/parties/" + supplier + "/ledger/export?format=csv&columns=Date&columns=Balance", admin));
        assertThat(ledger).startsWith("﻿Date,Balance\r\n").contains(",-60000.00\r\n").doesNotContain("Position");
        assertThat(ledger).endsWith("Totals / closing,-60000.00\r\n");
        String challan = pdfText(get("/api/v1/reports/transactions/" + inTx + "/pdf?columns=Material&columns=Amount", admin));
        assertThat(challan).contains("Material IN Voucher RIN-", "Export Steel", "₹1,87,500.00", "Total").doesNotContain("Unit  ", "Rate");
    }

    @Test
    void unknownColumnIs422AndOldCallsUnchanged() throws Exception {
        for (String url : List.of("/api/v1/reports/material-in/export?format=csv&columns=Nope", "/api/v1/reports/stock/export?format=pdf&columns=Date",
                "/api/v1/reports/transactions/" + inTx + "/pdf?columns=Bogus")) {
            MvcResult r = get(url, admin);
            assertThat(status(r)).isEqualTo(422);
            assertThat(r.getResponse().getContentAsString()).contains("Valid columns");
        }
        assertThat(csv(get("/api/v1/reports/material-in/export?format=csv&columns=", admin)))
                .startsWith("﻿Number,Date,Party,Challan,Vehicle,Status,Material,Unit,Quantity,Rate,Amount\r\n");
        long before = jdbc.queryForObject("select count(*) from audit_logs where action = 'EXPORT' and module = 'REPORT'", Long.class);
        get("/api/v1/reports/expenses/export?format=csv&columns=Number&columns=Amount", admin);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'EXPORT' and module = 'REPORT'", Long.class)).isEqualTo(before + 1);
        assertThat(jdbc.queryForList("select new_values::text from audit_logs where action = 'EXPORT' and module = 'REPORT' order by id desc limit 1", String.class).get(0))
                .contains("\"columns\": [\"Number\", \"Amount\"]");
    }

    @Test
    void exportColumnsEndpointListsLabels() throws Exception {
        MvcResult r = get("/api/v1/reports/export-columns?kind=ledger", admin);
        assertThat(status(r)).isEqualTo(200);
        assertThat(r.getResponse().getContentAsString()).isEqualTo("[\"Date\",\"Type\",\"Reference\",\"Debit\",\"Credit\",\"Balance\",\"Position\"]");
        assertThat(get("/api/v1/reports/export-columns?kind=challan", admin).getResponse().getContentAsString()).startsWith("[\"#\"");
        assertThat(status(get("/api/v1/reports/export-columns?kind=x", admin))).isEqualTo(422);
    }
}
