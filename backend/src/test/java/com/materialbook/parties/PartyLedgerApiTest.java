package com.materialbook.parties;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.materialbook.products.AbstractCatalogApiTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** Ledger fixtures are inserted with SQL so the test does not depend on the material module. */
class PartyLedgerApiTest extends AbstractCatalogApiTest {
    private static final AtomicInteger SEQ = new AtomicInteger(40_000);

    private String admin;
    private long userId;

    @BeforeEach
    void setUp() throws Exception {
        admin = superToken();
        userId = jdbc.queryForObject("select id from users where mobile = '9000000001'", Long.class);
    }

    private long party(String type, String opening) {
        int n = SEQ.incrementAndGet();
        return jdbc.queryForObject("insert into parties (party_code, party_name, party_type, opening_balance) "
                + "values (?, ?, ?, ?::numeric) returning id", Long.class, "LT-" + n, "Ledger Party " + n, type, opening);
    }

    private void material(long party, String type, String date, String total, String status) {
        int n = SEQ.incrementAndGet();
        boolean cancelled = status.equals("CANCELLED");
        jdbc.update("insert into material_transactions (transaction_number, transaction_type, transaction_date, party_id, total_amount, "
                + "status, created_by, cancelled_by, cancelled_at, cancellation_reason) "
                + "values (?, ?, ?::date, ?, ?::numeric, ?, ?, ?, case when ? then now() end, case when ? then 'x' end)",
                "LT-" + type + "-" + n, type, date, party, total, status, userId, cancelled ? userId : null, cancelled, cancelled);
    }

    private void payment(long party, String direction, String date, String amount, String status) {
        int n = SEQ.incrementAndGet();
        boolean cancelled = status.equals("CANCELLED");
        jdbc.update("insert into party_payments (payment_number, party_id, payment_date, direction, amount, status, created_by, "
                + "cancelled_by, cancelled_at, cancellation_reason) "
                + "values (?, ?, ?::date, ?, ?::numeric, ?, ?, ?, case when ? then now() end, case when ? then 'x' end)",
                "LT-PAY-" + n, party, date, direction, amount, status, userId, cancelled ? userId : null, cancelled, cancelled);
    }

    private JsonNode ledger(long party, String query) throws Exception {
        return tree(call(HttpMethod.GET, "/api/v1/parties/" + party + "/ledger" + query, admin, null));
    }

    private static List<String> balances(JsonNode ledger) {
        List<String> out = new ArrayList<>();
        ledger.get("entries").forEach(e -> out.add(e.get("balance").asText()));
        return out;
    }

    /** Client owes 1000 (OUT), pays 400 (RECEIVED) -> owes 600. Supplier owed 1000 (IN) = -1000, paying 400 (PAID) -> -600. */
    @Test
    void businessMeaningOfSigns() throws Exception {
        long client = party("CLIENT", "0");
        material(client, "OUT", "2026-03-01", "1000.00", "POSTED");
        payment(client, "RECEIVED", "2026-03-02", "400.00", "POSTED");
        assertThat(balances(ledger(client, ""))).containsExactly("1000.00", "600.00");

        long supplier = party("SUPPLIER", "0");
        material(supplier, "IN", "2026-03-01", "1000.00", "POSTED");
        payment(supplier, "PAID", "2026-03-02", "400.00", "POSTED");
        JsonNode l = ledger(supplier, "");
        assertThat(balances(l)).containsExactly("-1000.00", "-600.00");
        assertThat(l.get("closingBalance").asText()).isEqualTo("-600.00");
    }

    @Test
    void fullLedgerMathOrderingAndCancelledExclusion() throws Exception {
        long p = party("BOTH", "250.00");
        material(p, "OUT", "2026-03-05", "1000.00", "POSTED");
        material(p, "IN", "2026-03-03", "300.00", "POSTED");
        payment(p, "RECEIVED", "2026-03-10", "400.00", "POSTED");
        payment(p, "PAID", "2026-03-07", "50.00", "POSTED");
        material(p, "OUT", "2026-03-06", "9999.00", "CANCELLED");
        payment(p, "RECEIVED", "2026-03-08", "8888.00", "CANCELLED");

        JsonNode l = ledger(p, "");
        assertThat(l.get("party").get("type").asText()).isEqualTo("BOTH");
        assertThat(l.get("party").get("code").asText()).startsWith("LT-");
        assertThat(l.get("openingBalance").asText()).isEqualTo("250.00");
        List<String> kinds = new ArrayList<>();
        l.get("entries").forEach(e -> kinds.add(e.get("kind").asText()));
        assertThat(kinds).containsExactly("PURCHASE", "DISPATCH", "PAYMENT_PAID", "PAYMENT_RECEIVED");
        assertThat(l.get("entries").get(1).get("reference").asText()).startsWith("LT-OUT-");
        assertThat(l.get("entries").get(1).get("date").asText()).isEqualTo("2026-03-05");
        assertThat(balances(l)).containsExactly("-50.00", "950.00", "1000.00", "600.00");
        assertThat(l.get("closingBalance").asText()).isEqualTo("600.00");
        assertThat(l.get("totals").get("debit").asText()).isEqualTo("1050.00");
        assertThat(l.get("totals").get("credit").asText()).isEqualTo("700.00");
    }

    @Test
    void openingCarriesForwardIntoDateRange() throws Exception {
        long p = party("BOTH", "100.00");
        material(p, "OUT", "2026-01-10", "500.00", "POSTED");
        payment(p, "RECEIVED", "2026-01-20", "200.00", "POSTED");
        material(p, "IN", "2026-02-10", "50.00", "POSTED");
        payment(p, "PAID", "2026-03-10", "30.00", "POSTED");
        material(p, "OUT", "2026-03-20", "70.00", "POSTED");

        JsonNode from = ledger(p, "?from=2026-02-01");
        assertThat(from.get("openingBalance").asText()).isEqualTo("400.00");
        assertThat(balances(from)).containsExactly("350.00", "380.00", "450.00");
        assertThat(from.get("closingBalance").asText()).isEqualTo("450.00");

        JsonNode range = ledger(p, "?from=2026-02-01&to=2026-03-15");
        assertThat(range.get("openingBalance").asText()).isEqualTo("400.00");
        assertThat(balances(range)).containsExactly("350.00", "380.00");
        assertThat(range.get("closingBalance").asText()).isEqualTo("380.00");

        JsonNode empty = ledger(p, "?from=2027-01-01");
        assertThat(empty.get("entries")).isEmpty();
        assertThat(empty.get("openingBalance").asText()).isEqualTo("450.00");
        assertThat(empty.get("closingBalance").asText()).isEqualTo("450.00");
        assertThat(empty.get("totals").get("debit").asText()).isEqualTo("0");

        // inclusive bounds on both ends
        assertThat(ledger(p, "?from=2026-03-10&to=2026-03-10").get("entries")).hasSize(1);
    }

    @Test
    void ledgerErrorsAndPermissions() throws Exception {
        assertThat(status(call(HttpMethod.GET, "/api/v1/parties/999999999/ledger", admin, null))).isEqualTo(404);
        long p = party("CLIENT", "0");
        String viewer = tokenFor("VIEWER");
        assertThat(status(call(HttpMethod.GET, "/api/v1/parties/" + p + "/ledger", viewer, null))).isEqualTo(403); // VIEWER is limited to material + stock
        assertThat(status(call(HttpMethod.GET, "/api/v1/parties/" + p + "/ledger", "bad", null))).isEqualTo(401);
        assertThat(status(call(HttpMethod.GET, "/api/v1/parties/balances", "bad", null))).isEqualTo(401);
    }

    @Test
    void ledgerRejectsMoreThanCapEntries() throws Exception {
        long p = party("CLIENT", "0");
        jdbc.update("insert into party_payments (payment_number, party_id, payment_date, direction, amount, created_by) "
                + "select 'LT-BULK-' || " + p + " || '-' || g, ?, date '2026-01-01', 'RECEIVED', 1, ? from generate_series(1, 5001) g", p, userId);
        assertThat(status(call(HttpMethod.GET, "/api/v1/parties/" + p + "/ledger", admin, null))).isEqualTo(422);
        assertThat(status(call(HttpMethod.GET, "/api/v1/parties/" + p + "/ledger?from=2026-01-02", admin, null))).isEqualTo(200);
    }

    @Test
    void balancesEndpoint() throws Exception {
        long supplier = party("SUPPLIER", "-100.00");
        long client = party("CLIENT", "0");
        long both = party("BOTH", "0");
        long settled = party("CLIENT", "75.00");
        long untouched = party("SUPPLIER", "0");
        material(supplier, "IN", "2026-03-01", "900.00", "POSTED");
        payment(supplier, "PAID", "2026-03-02", "400.00", "POSTED");
        payment(supplier, "PAID", "2026-03-03", "5000.00", "CANCELLED");
        material(client, "OUT", "2026-03-01", "1000.00", "POSTED");
        payment(client, "RECEIVED", "2026-03-02", "400.00", "POSTED");
        material(both, "OUT", "2026-03-01", "10.00", "POSTED");
        material(both, "IN", "2026-03-02", "4.00", "POSTED");
        payment(settled, "RECEIVED", "2026-03-02", "75.00", "POSTED");

        JsonNode all = tree(call(HttpMethod.GET, "/api/v1/parties/balances?size=200", admin, null));
        assertThat(balanceOf(all, supplier)).isEqualTo("-600.00");
        assertThat(balanceOf(all, client)).isEqualTo("600.00");
        assertThat(balanceOf(all, both)).isEqualTo("6.00");
        assertThat(balanceOf(all, settled)).isEqualTo("0.00");
        assertThat(balanceOf(all, untouched)).isEqualTo("0.00");

        JsonNode nonZero = tree(call(HttpMethod.GET, "/api/v1/parties/balances?size=200&onlyNonZero=true", admin, null));
        assertThat(balanceOf(nonZero, settled)).isNull();
        assertThat(balanceOf(nonZero, untouched)).isNull();
        assertThat(balanceOf(nonZero, client)).isEqualTo("600.00");

        JsonNode suppliers = tree(call(HttpMethod.GET, "/api/v1/parties/balances?size=200&type=SUPPLIER", admin, null));
        assertThat(balanceOf(suppliers, supplier)).isEqualTo("-600.00");
        assertThat(balanceOf(suppliers, both)).isEqualTo("6.00");
        assertThat(balanceOf(suppliers, client)).isNull();
        JsonNode onlyBoth = tree(call(HttpMethod.GET, "/api/v1/parties/balances?size=200&type=BOTH", admin, null));
        assertThat(balanceOf(onlyBoth, both)).isEqualTo("6.00");
        assertThat(balanceOf(onlyBoth, supplier)).isNull();

        JsonNode paged = tree(call(HttpMethod.GET, "/api/v1/parties/balances?size=2&page=1", admin, null));
        assertThat(paged.get("content")).hasSize(2);
        assertThat(paged.get("totalElements").asLong()).isEqualTo(all.get("totalElements").asLong());
        assertThat(paged.get("content").get(0).has("code")).isTrue();
    }

    private static String balanceOf(JsonNode page, long id) {
        for (JsonNode n : page.get("content")) {
            if (n.get("id").asLong() == id) {
                return n.get("balance").asText();
            }
        }
        return null;
    }
}
