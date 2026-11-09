package com.materialbook.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class OpeningStockTest extends AbstractStockTest {
    private static final String URL = "/api/v1/inventory/opening-stock";

    @Test
    void createsOpeningMovementsWithoutTransactionAndAudits() throws Exception {
        long a = newProduct("0");
        long b = newProduct("0");
        int status = status(call(HttpMethod.POST, URL, superToken(), Map.of("date", "2026-01-01",
                "items", List.of(item(a, "12.5"), item(b, "3")))));

        assertThat(status).isEqualTo(201);
        assertQty(balance(a), "12.5");
        assertQty(balance(b), "3");
        assertThat(jdbc.queryForObject("""
                select count(*) from stock_movements where product_id = ? and movement_type = 'OPENING'
                  and transaction_id is null and movement_date = date '2026-01-01' and quantity_delta = 12.5
                """, Integer.class, a)).isEqualTo(1);
        assertThat(auditCount("OPENING_STOCK", a)).isEqualTo(1);
        assertThat(auditCount("OPENING_STOCK", b)).isEqualTo(1);
    }

    @Test
    void rejectsProductsThatAlreadyHaveMovementsAndIsAtomic() throws Exception {
        long fresh = newProduct("0");
        long used = newProduct("0");
        postNew(MovementType.IN, used, "5");
        String token = superToken();

        var r = call(HttpMethod.POST, URL, token, Map.of("items", List.of(item(fresh, "1"), item(used, "1"))));
        assertThat(status(r)).isEqualTo(409);
        assertThat(movementCount(fresh)).isZero();
        assertThat(movementCount(used)).isEqualTo(1);

        long once = newProduct("0");
        assertThat(status(call(HttpMethod.POST, URL, token, Map.of("items", List.of(item(once, "1")))))).isEqualTo(201);
        assertThat(status(call(HttpMethod.POST, URL, token, Map.of("items", List.of(item(once, "1")))))).isEqualTo(409);
        assertQty(balance(once), "1");
    }

    @Test
    void validatesRequest() throws Exception {
        long p = newProduct("0");
        String token = superToken();
        assertThat(status(call(HttpMethod.POST, URL, token, Map.of("items", List.of())))).isEqualTo(422);
        assertThat(status(call(HttpMethod.POST, URL, token, Map.of("items", List.of(item(p, "0")))))).isEqualTo(422);
        assertThat(status(call(HttpMethod.POST, URL, token, Map.of("items", List.of(item(p, "-2")))))).isEqualTo(422);
        assertThat(status(call(HttpMethod.POST, URL, token, Map.of("items", List.of(item(p, "1"), item(p, "2")))))).isEqualTo(422);
        assertThat(status(call(HttpMethod.POST, URL, token, Map.of("items", List.of(item(999_999L, "1")))))).isEqualTo(422);
        assertThat(movementCount(p)).isZero();
    }

    private static Map<String, Object> item(long productId, String quantity) {
        return Map.of("productId", productId, "quantity", quantity);
    }
}
