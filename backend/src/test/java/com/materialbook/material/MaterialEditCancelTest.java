package com.materialbook.material;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

class MaterialEditCancelTest extends AbstractMaterialTest {

    @Test
    void editInQuantityUpAndDownSupersedesOldItemsAndKeepsInvariant() throws Exception {
        long product = newProduct();
        long supplier = newParty("SUPPLIER");
        JsonNode tx = created("material-in", body(supplier, item(product, "10", "5")));
        long id = tx.get("id").asLong();

        MvcResult up = edit("material-in", admin, id, body(supplier, item(product, "25", "6")), 0);
        assertThat(status(up)).as(up.getResponse().getContentAsString()).isEqualTo(200);
        JsonNode after = tree(up);
        assertThat(after.get("version").asLong()).isEqualTo(1);
        assertThat(after.get("items")).hasSize(1);
        assertThat(after.get("items").get(0).get("quantity").asText()).isEqualTo("25.000");
        assertThat(after.get("totalAmount").asText()).isEqualTo("150.00");
        assertStock(product, "25");
        assertThat(itemRows(id, true)).isEqualTo(1);
        assertThat(itemRows(id, false)).isEqualTo(1);

        MvcResult down = edit("material-in", admin, id, body(supplier, item(product, "4", "6")), 1);
        assertThat(status(down)).isEqualTo(200);
        assertStock(product, "4");
        assertThat(itemRows(id, true)).isEqualTo(2);
        assertThat(tree(call(HttpMethod.GET, "/api/v1/material-in/" + id, admin, null)).get("items")).hasSize(1);
        assertThat(auditCount("UPDATE", "MATERIAL_IN", id)).isEqualTo(2);
        JsonNode audit = tree(call(HttpMethod.GET, "/api/v1/audit-logs?module=MATERIAL_IN&recordId=" + id + "&action=UPDATE", admin, null))
                .get("content").get(0);
        assertThat(audit.get("oldValues").get("items").get(0).get("quantity").asText()).isEqualTo("25.000");
        assertThat(audit.get("newValues").get("items").get(0).get("quantity").asText()).isEqualTo("4.000");
    }

    /** Found by the end-to-end run: edit used reverse-then-post, so raising an IN after a partial dispatch was wrongly refused. */
    @Test
    void editInAfterPartialDispatchIsCheckedOnNetEffect() throws Exception {
        long product = newProduct();
        long supplier = newParty("SUPPLIER");
        long client = newParty("CLIENT");
        long in = created("material-in", body(supplier, item(product, "12.5", "1"))).get("id").asLong();
        created("material-out", body(client, item(product, "5", "1")));
        assertStock(product, "7.5");

        // 12.5 -> 20 is a net +7.5: allowed although only 7.5 is on hand
        MvcResult up = edit("material-in", admin, in, body(supplier, item(product, "20", "1")), 0);
        assertThat(status(up)).as(up.getResponse().getContentAsString()).isEqualTo(200);
        assertStock(product, "15");

        // 20 -> 4 would leave 4 - 5 = -1: rejected, nothing changes
        MvcResult tooLow = edit("material-in", admin, in, body(supplier, item(product, "4", "1")), 1);
        assertThat(status(tooLow)).isEqualTo(409);
        assertStock(product, "15");
        assertThat(balance(product)).isEqualByComparingTo(sumDelta(product));

        // 20 -> 5 equals exactly what was dispatched: allowed, stock hits zero
        assertThat(status(edit("material-in", admin, in, body(supplier, item(product, "5", "1")), 1))).isEqualTo(200);
        assertStock(product, "0");
        assertThat(balance(product)).isEqualByComparingTo(sumDelta(product));
    }

    @Test
    void editWithProductSwapMovesStockBetweenProducts() throws Exception {
        long a = newProduct();
        long b = newProduct();
        long supplier = newParty("SUPPLIER");
        long id = created("material-in", body(supplier, item(a, "10", "1"))).get("id").asLong();

        assertThat(status(edit("material-in", admin, id, body(supplier, item(b, "7", "1"), item(a, "1", "1")), 0))).isEqualTo(200);

        assertStock(a, "1");
        assertStock(b, "7");
    }

    @Test
    void editChangesHeaderFieldsAndOutEditWithinStock() throws Exception {
        long product = newProduct();
        created("material-in", body(newParty("SUPPLIER"), item(product, "10", "1")));
        long client = newParty("CLIENT");
        long id = created("material-out", body(client, item(product, "4", "1"))).get("id").asLong();

        Map<String, Object> changed = body(client, item(product, "10", "1"));
        changed.put("transactionDate", "2026-04-01");
        changed.put("vehicleNumber", "MH12AB1234");
        MvcResult r = edit("material-out", admin, id, changed, 0);

        assertThat(status(r)).isEqualTo(200);
        assertThat(tree(r).get("vehicleNumber").asText()).isEqualTo("MH12AB1234");
        assertThat(tree(r).get("transactionDate").asText()).isEqualTo("2026-04-01");
        assertStock(product, "0");
    }

    @Test
    void editThatWouldMakeStockNegativeIs409AndChangesNothing() throws Exception {
        long product = newProduct();
        long supplier = newParty("SUPPLIER");
        long in = created("material-in", body(supplier, item(product, "10", "1"))).get("id").asLong();
        long out = created("material-out", body(null, item(product, "8", "1"))).get("id").asLong();

        MvcResult shrinkIn = edit("material-in", admin, in, body(supplier, item(product, "5", "1")), 0);
        assertThat(status(shrinkIn)).isEqualTo(409);
        assertThat(tree(shrinkIn).get("code").asText()).isEqualTo("INSUFFICIENT_STOCK");
        MvcResult growOut = edit("material-out", admin, out, body(null, item(product, "13", "1")), 0);
        assertThat(status(growOut)).isEqualTo(409);

        assertStock(product, "2");
        assertThat(itemRows(in, true)).isZero();
        assertThat(itemRows(out, true)).isZero();
        assertThat(tree(call(HttpMethod.GET, "/api/v1/material-in/" + in, admin, null)).get("version").asLong()).isZero();
    }

    @Test
    void staleVersionAndCancelledEditAre409() throws Exception {
        long product = newProduct();
        long supplier = newParty("SUPPLIER");
        long id = created("material-in", body(supplier, item(product, "3", "1"))).get("id").asLong();
        assertThat(status(edit("material-in", admin, id, body(supplier, item(product, "4", "1")), 0))).isEqualTo(200);

        assertThat(status(edit("material-in", admin, id, body(supplier, item(product, "5", "1")), 0))).as("stale").isEqualTo(409);
        assertThat(status(call(HttpMethod.PUT, "/api/v1/material-in/" + id, admin, body(supplier, item(product, "5", "1")))))
                .as("version required").isEqualTo(422);
        assertStock(product, "4");

        assertThat(status(cancel("material-in", admin, id))).isEqualTo(200);
        assertThat(status(edit("material-in", admin, id, body(supplier, item(product, "5", "1")), 2))).as("cancelled").isEqualTo(409);
        assertThat(status(call(HttpMethod.GET, "/api/v1/material-out/" + id, admin, null))).as("wrong type").isEqualTo(404);
    }

    @Test
    void cancelInAndOutCreateReversalsAndDoubleCancelIs409() throws Exception {
        long product = newProduct();
        long in = created("material-in", body(newParty("SUPPLIER"), item(product, "10", "2"))).get("id").asLong();
        long out = created("material-out", body(null, item(product, "6", "2"))).get("id").asLong();

        MvcResult cancelOut = cancel("material-out", admin, out);
        assertThat(status(cancelOut)).isEqualTo(200);
        JsonNode cancelled = tree(cancelOut);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("cancellationReason").asText()).isEqualTo("entered by mistake");
        assertThat(cancelled.get("cancelledByName").asText()).isNotBlank();
        assertThat(cancelled.get("cancelledAt").isNull()).isFalse();
        assertStock(product, "10");
        assertThat(jdbc.queryForObject("select count(*) from stock_movements where transaction_id = ? and movement_type = 'REVERSAL_IN'",
                Integer.class, out)).isEqualTo(1);

        assertThat(status(cancel("material-out", admin, out))).as("double cancel").isEqualTo(409);
        assertStock(product, "10");

        assertThat(status(cancel("material-in", admin, in))).isEqualTo(200);
        assertStock(product, "0");
        assertThat(jdbc.queryForObject("select count(*) from stock_movements where transaction_id = ? and movement_type = 'REVERSAL_OUT'",
                Integer.class, in)).isEqualTo(1);
        assertThat(auditCount("CANCEL", "MATERIAL_IN", in)).isEqualTo(1);
        assertThat(auditCount("CANCEL", "MATERIAL_OUT", out)).isEqualTo(1);
    }

    @Test
    void cancelInAfterDispatchIs409AndReasonIsRequired() throws Exception {
        long product = newProduct();
        long in = created("material-in", body(newParty("SUPPLIER"), item(product, "10", "1"))).get("id").asLong();
        created("material-out", body(null, item(product, "7", "1")));

        MvcResult r = cancel("material-in", admin, in);
        assertThat(status(r)).isEqualTo(409);
        assertThat(tree(r).get("code").asText()).isEqualTo("INSUFFICIENT_STOCK");
        assertStock(product, "3");
        assertThat(tree(call(HttpMethod.GET, "/api/v1/material-in/" + in, admin, null)).get("status").asText()).isEqualTo("POSTED");

        assertThat(status(call(HttpMethod.POST, "/api/v1/material-in/" + in + "/cancel", admin, Map.of("reason", " ")))).isEqualTo(422);
    }
}
