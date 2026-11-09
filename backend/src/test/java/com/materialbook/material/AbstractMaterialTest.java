package com.materialbook.material;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.materialbook.products.AbstractCatalogApiTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

/** Helpers for the material IN/OUT tests: catalog rows are inserted by SQL, everything else goes through HTTP. */
abstract class AbstractMaterialTest extends AbstractCatalogApiTest {
    private static final AtomicInteger SEQ = new AtomicInteger();

    protected String admin;

    @BeforeEach
    void setUpAdmin() throws Exception {
        admin = superToken();
    }

    protected long newProduct(String unit) {
        int n = SEQ.incrementAndGet();
        return jdbc.queryForObject("insert into products(product_code, product_name, unit) values (?, ?, ?) returning id",
                Long.class, "MT-" + n, "Material product " + n, unit);
    }

    protected long newProduct() {
        return newProduct("KG");
    }

    protected long newParty(String type) {
        int n = SEQ.incrementAndGet();
        return jdbc.queryForObject("insert into parties(party_code, party_name, party_type) values (?, ?, ?) returning id",
                Long.class, "MP-" + n, "Material party " + n, type);
    }

    protected static Map<String, Object> item(long productId, String quantity, String rate) {
        Map<String, Object> m = new HashMap<>();
        m.put("productId", productId);
        m.put("quantity", quantity);
        if (rate != null) {
            m.put("rate", rate);
        }
        return m;
    }

    @SafeVarargs
    protected static Map<String, Object> body(Long partyId, Map<String, Object>... items) {
        Map<String, Object> m = new HashMap<>();
        m.put("transactionDate", "2026-03-10");
        m.put("partyId", partyId);
        m.put("items", new ArrayList<>(List.of(items)));
        return m;
    }

    protected MvcResult create(String kind, String token, Map<String, Object> body) throws Exception {
        return call(HttpMethod.POST, "/api/v1/" + kind, token, body);
    }

    /** Creates successfully as admin and returns the response. */
    protected JsonNode created(String kind, Map<String, Object> body) throws Exception {
        MvcResult r = create(kind, admin, body);
        assertThat(status(r)).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return tree(r);
    }

    protected MvcResult edit(String kind, String token, long id, Map<String, Object> body, long version) throws Exception {
        Map<String, Object> b = new HashMap<>(body);
        b.put("version", version);
        return call(HttpMethod.PUT, "/api/v1/" + kind + "/" + id, token, b);
    }

    protected MvcResult cancel(String kind, String token, long id) throws Exception {
        return call(HttpMethod.POST, "/api/v1/" + kind + "/" + id + "/cancel", token, Map.of("reason", "entered by mistake"));
    }

    protected BigDecimal balance(long productId) {
        return jdbc.queryForObject("select quantity from stock_balances where product_id = ?", BigDecimal.class, productId);
    }

    protected BigDecimal sumDelta(long productId) {
        return jdbc.queryForObject("select coalesce(sum(quantity_delta), 0) from stock_movements where product_id = ?",
                BigDecimal.class, productId);
    }

    protected int movementCount(long productId) {
        return jdbc.queryForObject("select count(*) from stock_movements where product_id = ?", Integer.class, productId);
    }

    /** Asserts stock is {@code expected} and equals the sum of its movements. */
    protected void assertStock(long productId, String expected) {
        assertThat(balance(productId)).isEqualByComparingTo(expected);
        assertThat(sumDelta(productId)).isEqualByComparingTo(expected);
    }

    protected int itemRows(long transactionId, boolean superseded) {
        return jdbc.queryForObject("select count(*) from material_transaction_items where transaction_id = ? and (superseded_at is not null) = ?",
                Integer.class, transactionId, superseded);
    }

    protected long auditCount(String action, String module, long recordId) {
        return jdbc.queryForObject("select count(*) from audit_logs where action = ? and module = ? and record_id = ?",
                Long.class, action, module, recordId);
    }
}
