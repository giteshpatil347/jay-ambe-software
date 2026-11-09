package com.materialbook.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class InventoryApiTest extends AbstractStockTest {

    @Test
    void stockListFiltersAndFlagsLowStock() throws Exception {
        long category = jdbc.queryForObject("insert into product_categories(name) values ('Cat-' || nextval('product_code_seq')) returning id", Long.class);
        long low = newProduct("10");
        long fine = newProduct("10");
        long other = newProduct("0");
        jdbc.update("update products set category_id = ? where id in (?, ?)", category, low, fine);
        postNew(MovementType.IN, low, "10", fine, "11");

        JsonNode all = tree(call(HttpMethod.GET, "/api/v1/inventory/stock?categoryId=" + category, tokenFor("VIEWER"), null));
        assertThat(all.get("totalElements").asInt()).isEqualTo(2);
        JsonNode row = find(all, low);
        assertThat(row.get("lowStock").asBoolean()).isTrue();
        assertThat(row.get("quantity").asText()).isEqualTo("10.000");
        assertThat(row.get("minimumStock").asText()).isEqualTo("10.000");
        assertThat(row.get("unit").asText()).isEqualTo("KG");
        assertThat(row.get("categoryName").asText()).startsWith("Cat-");
        assertThat(find(all, fine).get("lowStock").asBoolean()).isFalse();

        JsonNode lowOnly = tree(call(HttpMethod.GET, "/api/v1/inventory/stock?lowStockOnly=true&q=stock product", superToken(), null));
        assertThat(ids(lowOnly)).contains(low).doesNotContain(fine, other);

        JsonNode byCode = tree(call(HttpMethod.GET, "/api/v1/inventory/stock?size=1&q=" + codeOf(fine).toLowerCase(), superToken(), null));
        assertThat(ids(byCode)).containsExactly(fine);
        assertThat(byCode.get("size").asInt()).isEqualTo(1);
    }

    @Test
    void ledgerShowsRunningBalanceOpeningBalanceAndOrdering() throws Exception {
        long p = newProduct("0");
        long in1 = postNew(MovementType.IN, LocalDate.of(2026, 1, 10), p, "100");
        long out = postNew(MovementType.OUT, LocalDate.of(2026, 1, 20), p, "30");
        postNew(MovementType.IN, LocalDate.of(2026, 2, 1), p, "5");
        String token = superToken();

        JsonNode full = tree(call(HttpMethod.GET, "/api/v1/inventory/ledger?productId=" + p, token, null));
        assertThat(full.get("openingBalance").asText()).isEqualTo("0");
        assertThat(full.get("totalElements").asInt()).isEqualTo(3);
        JsonNode rows = full.get("content");
        assertThat(rows.get(0).get("movementType").asText()).isEqualTo("IN");
        assertThat(rows.get(0).get("date").asText()).isEqualTo("2026-01-10");
        assertThat(rows.get(0).get("delta").asText()).isEqualTo("100.000");
        assertThat(rows.get(0).get("balance").asText()).isEqualTo("100.000");
        assertThat(rows.get(0).get("transactionNumber").asText()).isEqualTo(
                jdbc.queryForObject("select transaction_number from material_transactions where id = ?", String.class, in1));
        assertThat(rows.get(0).get("createdBy").asText()).isNotBlank();
        assertThat(rows.get(1).get("delta").asText()).isEqualTo("-30.000");
        assertThat(rows.get(1).get("balance").asText()).isEqualTo("70.000");
        assertThat(rows.get(1).get("transactionId").asLong()).isEqualTo(out);
        assertThat(rows.get(2).get("balance").asText()).isEqualTo("75.000");

        JsonNode ranged = tree(call(HttpMethod.GET,
                "/api/v1/inventory/ledger?newestFirst=true&productId=" + p + "&from=2026-01-15&to=2026-02-28", token, null));
        assertThat(ranged.get("openingBalance").asText()).isEqualTo("100.000");
        assertThat(ranged.get("totalElements").asInt()).isEqualTo(2);
        assertThat(ranged.get("content").get(0).get("balance").asText()).isEqualTo("75.000");
        assertThat(ranged.get("content").get(1).get("balance").asText()).isEqualTo("70.000");

        JsonNode paged = tree(call(HttpMethod.GET, "/api/v1/inventory/ledger?productId=" + p + "&size=1&page=2", token, null));
        assertThat(paged.get("content")).hasSize(1);
        assertThat(paged.get("content").get(0).get("balance").asText()).isEqualTo("75.000");
    }

    @Test
    void ledgerValidatesProduct() throws Exception {
        String token = superToken();
        assertThat(status(call(HttpMethod.GET, "/api/v1/inventory/ledger?productId=999999", token, null))).isEqualTo(404);
        assertThat(status(call(HttpMethod.GET, "/api/v1/inventory/ledger", token, null))).isEqualTo(400);
    }

    @Test
    void permissionsAreEnforced() throws Exception {
        long p = newProduct("0");
        Map<String, Object> body = Map.of("items", List.of(Map.of("productId", p, "quantity", "5")));
        assertThat(status(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/inventory/stock")).andReturn())).isEqualTo(401);
        String viewer = tokenFor("VIEWER");
        assertThat(status(call(HttpMethod.GET, "/api/v1/inventory/stock", viewer, null))).isEqualTo(200);
        assertThat(status(call(HttpMethod.POST, "/api/v1/inventory/opening-stock", viewer, body))).isEqualTo(403);
        assertThat(status(call(HttpMethod.POST, "/api/v1/inventory/opening-stock", tokenFor("ACCOUNTANT"), body))).isEqualTo(403);
        assertThat(movementCount(p)).isZero();
        assertThat(status(call(HttpMethod.POST, "/api/v1/inventory/opening-stock", tokenFor("ADMIN"), body))).isEqualTo(201);
    }

    private JsonNode find(JsonNode page, long productId) {
        for (JsonNode n : page.get("content")) {
            if (n.get("productId").asLong() == productId) {
                return n;
            }
        }
        throw new AssertionError("product " + productId + " not in page");
    }

    private List<Long> ids(JsonNode page) {
        List<Long> ids = new java.util.ArrayList<>();
        page.get("content").forEach(n -> ids.add(n.get("productId").asLong()));
        return ids;
    }

    private String codeOf(long productId) {
        return jdbc.queryForObject("select product_code from products where id = ?", String.class, productId);
    }
}
