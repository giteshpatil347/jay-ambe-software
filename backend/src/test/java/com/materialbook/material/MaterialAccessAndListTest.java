package com.materialbook.material;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class MaterialAccessAndListTest extends AbstractMaterialTest {

    @Test
    void permissionMatrix() throws Exception {
        long product = newProduct();
        long supplier = newParty("SUPPLIER");
        long id = created("material-in", body(supplier, item(product, "10", "1"))).get("id").asLong();
        String purchase = tokenFor("PURCHASE_STAFF");
        String viewer = tokenFor("VIEWER");

        assertThat(status(create("material-in", purchase, body(supplier, item(product, "1", "1"))))).isEqualTo(201);
        assertThat(status(edit("material-in", purchase, id, body(supplier, item(product, "2", "1")), 0))).isEqualTo(403);
        assertThat(status(cancel("material-in", purchase, id))).isEqualTo(403);
        assertThat(status(create("material-out", purchase, body(null, item(product, "1", "1"))))).isEqualTo(403);
        assertThat(status(call(HttpMethod.GET, "/api/v1/material-out", purchase, null))).isEqualTo(403);
        assertThat(status(call(HttpMethod.GET, "/api/v1/material-in/" + id, purchase, null))).isEqualTo(200);

        assertThat(status(create("material-in", viewer, body(supplier, item(product, "1", "1"))))).isEqualTo(403);
        assertThat(status(create("material-out", viewer, body(null, item(product, "1", "1"))))).isEqualTo(403);
        assertThat(status(call(HttpMethod.GET, "/api/v1/material-in", viewer, null))).isEqualTo(200);
        assertThat(status(call(HttpMethod.GET, "/api/v1/material-out", viewer, null))).isEqualTo(200);
        assertThat(status(call(HttpMethod.GET, "/api/v1/material-in", "bad-token", null))).isEqualTo(401);
    }

    @Test
    void listFiltersAndTotalsExcludeCancelled() throws Exception {
        long p1 = newProduct();
        long p2 = newProduct();
        long supplier = newParty("SUPPLIER");
        long other = newParty("SUPPLIER");
        java.util.Map<String, Object> first = body(supplier, item(p1, "10", "100"), item(p2, "5", "10"), item(p1, "1", "0"));
        first.put("challanNumber", "CH-ALPHA-7");
        first.put("vehicleNumber", "MH14XY0001");
        first.put("transactionDate", "2026-02-01");
        long firstId = created("material-in", first).get("id").asLong();
        java.util.Map<String, Object> second = body(supplier, item(p1, "2", "50"));
        second.put("transactionDate", "2026-03-01");
        created("material-in", second);
        long cancelledId = created("material-in", body(supplier, item(p2, "1", "999"))).get("id").asLong();
        created("material-in", body(other, item(p1, "1", "1")));
        assertThat(status(cancel("material-in", admin, cancelledId))).isEqualTo(200);

        JsonNode all = list("material-in", "partyId=" + supplier);
        assertThat(all.get("totalElements").asLong()).isEqualTo(3);
        assertThat(all.get("totalAmount").asText()).isEqualTo("1150.00");
        assertThat(all.get("content").get(0).get("transactionDate").asText()).isEqualTo("2026-03-10");
        JsonNode row = all.get("content").get(2);
        assertThat(row.get("id").asLong()).isEqualTo(firstId);
        assertThat(row.get("itemCount").asInt()).isEqualTo(3);
        assertThat(row.get("productNames")).hasSize(2);
        assertThat(row.get("partyName").asText()).startsWith("Material party");
        assertThat(row.get("createdByName").asText()).isNotBlank();
        assertThat(row.get("totalAmount").asText()).isEqualTo("1050.00");

        JsonNode posted = list("material-in", "partyId=" + supplier + "&status=POSTED");
        assertThat(posted.get("totalElements").asLong()).isEqualTo(2);
        JsonNode cancelledOnly = list("material-in", "partyId=" + supplier + "&status=CANCELLED");
        assertThat(cancelledOnly.get("totalElements").asLong()).isEqualTo(1);
        assertThat(cancelledOnly.get("totalAmount").asText()).isEqualTo("0");
        assertThat(list("material-in", "partyId=" + supplier + "&from=2026-02-15&to=2026-03-01").get("totalElements").asLong()).isEqualTo(1);
        assertThat(list("material-in", "partyId=" + supplier + "&productId=" + p2).get("totalElements").asLong()).isEqualTo(2);
        assertThat(list("material-in", "q=ch-alpha").get("totalElements").asLong()).isEqualTo(1);
        assertThat(list("material-in", "q=mh14xy").get("totalElements").asLong()).isEqualTo(1);
        assertThat(list("material-in", "partyId=" + supplier + "&size=1&page=1").get("content")).hasSize(1);
        assertThat(list("material-out", "partyId=" + supplier).get("totalElements").asLong()).isZero();
    }

    private JsonNode list(String kind, String query) throws Exception {
        return tree(call(HttpMethod.GET, "/api/v1/" + kind + "?" + query, admin, null));
    }
}
