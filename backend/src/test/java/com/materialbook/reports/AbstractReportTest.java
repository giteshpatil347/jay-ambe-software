package com.materialbook.reports;

import com.materialbook.products.AbstractCatalogApiTest;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

/** Fixtures are inserted with SQL (superseded items, backdated/future documents) and read back through the report API. */
abstract class AbstractReportTest extends AbstractCatalogApiTest {
    private static final AtomicInteger SEQ = new AtomicInteger();

    protected String admin;
    protected long adminId;

    @BeforeEach
    void setUpAdmin() throws Exception {
        admin = superToken();
        adminId = jdbc.queryForObject("select id from users where mobile = '9000000001'", Long.class);
    }

    protected long product(String name, String unit, double minimumStock) {
        int n = SEQ.incrementAndGet();
        return jdbc.queryForObject("insert into products(product_code, product_name, unit, minimum_stock) values (?, ?, ?, ?) returning id",
                Long.class, "RP-" + n, name, unit, minimumStock);
    }

    protected long party(String name, String type, String opening) {
        int n = SEQ.incrementAndGet();
        return jdbc.queryForObject("insert into parties(party_code, party_name, party_type, opening_balance) values (?, ?, ?, ?::numeric) returning id",
                Long.class, "RPT-" + n, name, type, opening);
    }

    /** Inserts a header with total 0; call {@link #item} then {@link #recalc}. */
    protected long tx(String type, String date, Long partyId, String status) {
        int n = SEQ.incrementAndGet();
        String number = (type.equals("IN") ? "RIN-" : "ROUT-") + n;
        if (status.equals("CANCELLED")) {
            return jdbc.queryForObject("""
                    insert into material_transactions(transaction_number, transaction_type, transaction_date, party_id, vehicle_number, driver_name,
                      challan_number, status, created_by, cancelled_by, cancelled_at, cancellation_reason)
                    values (?, ?, ?::date, ?, 'MH12AB1234', 'Ramesh', 'CH-1', 'CANCELLED', ?, ?, now(), 'Entered twice') returning id""",
                    Long.class, number, type, date, partyId, adminId, adminId);
        }
        return jdbc.queryForObject("""
                insert into material_transactions(transaction_number, transaction_type, transaction_date, party_id, vehicle_number, driver_name,
                  challan_number, status, created_by) values (?, ?, ?::date, ?, 'MH12AB1234', 'Ramesh', 'CH-1', 'POSTED', ?) returning id""",
                Long.class, number, type, date, partyId, adminId);
    }

    protected void item(long txId, long productId, String qty, String rate, boolean superseded) {
        jdbc.update("insert into material_transaction_items(transaction_id, product_id, quantity, rate, superseded_at) values (?, ?, ?::numeric, ?::numeric, "
                + (superseded ? "now()" : "null") + ")", txId, productId, qty, rate);
    }

    protected void recalc(long txId) {
        jdbc.update("update material_transactions set total_amount = coalesce((select sum(amount) from material_transaction_items"
                + " where transaction_id = ? and superseded_at is null), 0) where id = ?", txId, txId);
    }

    protected long expense(long categoryId, String date, String amount, String status) {
        int n = SEQ.incrementAndGet();
        if (status.equals("CANCELLED")) {
            return jdbc.queryForObject("""
                    insert into expenses(expense_number, expense_date, category_id, amount, paid_to, status, created_by, cancelled_by, cancelled_at, cancellation_reason)
                    values (?, ?::date, ?, ?::numeric, 'Someone', 'CANCELLED', ?, ?, now(), 'dup') returning id""",
                    Long.class, "REXP-" + n, date, categoryId, amount, adminId, adminId);
        }
        return jdbc.queryForObject("insert into expenses(expense_number, expense_date, category_id, amount, paid_to, created_by) values (?, ?::date, ?, ?::numeric, 'Someone', ?) returning id",
                Long.class, "REXP-" + n, date, categoryId, amount, adminId);
    }

    protected long expenseCategory(String name) {
        int n = SEQ.incrementAndGet();
        return jdbc.queryForObject("insert into expense_categories(category_code, category_name) values (?, ?) returning id", Long.class, "RC-" + n, name + " " + n);
    }

    protected MvcResult get(String url, String token) throws Exception {
        return call(HttpMethod.GET, url, token, null);
    }

    protected static byte[] bytes(MvcResult r) throws Exception {
        return r.getResponse().getContentAsByteArray();
    }
}
