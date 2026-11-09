package com.materialbook.material;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

class MaterialCreateTest extends AbstractMaterialTest {

    @Test
    void inIncreasesStockAndComputesTotalsOnTheServer() throws Exception {
        long product = newProduct("TON");
        long supplier = newParty("SUPPLIER");

        JsonNode tx = created("material-in", body(supplier, item(product, "12.5", "15000")));

        assertThat(tx.get("transactionNumber").asText()).startsWith("MIN-" + Year.now(ZoneOffset.UTC).getValue() + "-");
        assertThat(tx.get("transactionType").asText()).isEqualTo("IN");
        assertThat(tx.get("status").asText()).isEqualTo("POSTED");
        assertThat(tx.get("totalAmount").asText()).isEqualTo("187500.00");
        JsonNode line = tx.get("items").get(0);
        assertThat(line.get("amount").asText()).isEqualTo("187500.00");
        assertThat(line.get("quantity").asText()).isEqualTo("12.500");
        assertThat(line.get("unit").asText()).isEqualTo("TON");
        assertStock(product, "12.5");
        long id = tx.get("id").asLong();
        assertThat(auditCount("CREATE", "MATERIAL_IN", id)).isEqualTo(1);
        assertThat(tree(call(HttpMethod.GET, "/api/v1/material-in/" + id, admin, null)).get("createdByName").asText()).isNotBlank();
    }

    @Test
    void outDecreasesStockAndRateDefaultsToZero() throws Exception {
        long product = newProduct();
        created("material-in", body(newParty("SUPPLIER"), item(product, "50", "10")));

        JsonNode out = created("material-out", body(newParty("CLIENT"), item(product, "20", null)));

        assertThat(out.get("transactionNumber").asText()).startsWith("MOUT-");
        assertThat(out.get("totalAmount").asText()).isEqualTo("0.00");
        assertStock(product, "30");
    }

    @Test
    void listRowsCarryTheQuantityPerUnit() throws Exception {
        long ton = newProduct("TON");
        long kg = newProduct("KG");
        long supplier = newParty("SUPPLIER");
        long id = created("material-in", body(supplier, item(ton, "2.5", "10"), item(kg, "40", "1"), item(ton, "1", "10"))).get("id").asLong();

        JsonNode row = null;
        for (JsonNode r : tree(call(HttpMethod.GET, "/api/v1/material-in?partyId=" + supplier, admin, null)).get("content")) {
            if (r.get("id").asLong() == id) {
                row = r;
            }
        }
        assertThat(row).isNotNull();
        // units are never added together; the same unit is summed (2.5 + 1 TON), listed by unit name
        assertThat(row.get("quantities")).hasSize(2);
        assertThat(row.get("quantities").get(0).get("unit").asText()).isEqualTo("KG");
        assertThat(row.get("quantities").get(0).get("quantity").asText()).isEqualTo("40.000");
        assertThat(row.get("quantities").get(1).get("unit").asText()).isEqualTo("TON");
        assertThat(row.get("quantities").get(1).get("quantity").asText()).isEqualTo("3.500");
    }

    @Test
    void multiItemMixedProductsAndSameProductTwice() throws Exception {
        long a = newProduct("TON");
        long b = newProduct("PIECE");
        long supplier = newParty("BOTH");

        JsonNode tx = created("material-in", body(supplier, item(a, "1.5", "100.10"), item(b, "7", "2.50"), item(a, "0.5", "0")));

        assertThat(tx.get("items")).hasSize(3);
        assertThat(tx.get("totalAmount").asText()).isEqualTo("167.65");
        assertStock(a, "2");
        assertStock(b, "7");
    }

    @Test
    void outBeyondStockIs409AndNothingIsPersisted() throws Exception {
        long product = newProduct();
        created("material-in", body(newParty("SUPPLIER"), item(product, "5", "1")));
        long lastNumber = lastNumber("MOUT");
        int movements = movementCount(product);
        int headers = jdbc.queryForObject("select count(*) from material_transactions", Integer.class);

        java.util.Map<String, Object> body = body(null, item(product, "5.001", "1"));
        body.put("challanNumber", "ROLLBACK-1");
        MvcResult r = create("material-out", admin, body);

        assertThat(status(r)).isEqualTo(409);
        assertThat(tree(r).get("code").asText()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(jdbc.queryForObject("select count(*) from material_transactions", Integer.class)).isEqualTo(headers);
        assertThat(jdbc.queryForObject("select count(*) from material_transactions where challan_number = 'ROLLBACK-1'", Integer.class)).isZero();
        assertThat(movementCount(product)).isEqualTo(movements);
        assertThat(lastNumber("MOUT")).as("document number rolled back").isEqualTo(lastNumber);
        assertStock(product, "5");
    }

    @Test
    void validationRules() throws Exception {
        long product = newProduct();
        long inactive = newProduct();
        jdbc.update("update products set status = 'INACTIVE' where id = ?", inactive);

        assertThat(status(create("material-in", admin, body(newParty("SUPPLIER"))))).as("no items").isEqualTo(422);
        assertThat(status(create("material-in", admin, body(newParty("SUPPLIER"), item(product, "0", "1"))))).as("zero qty").isEqualTo(422);
        assertThat(status(create("material-in", admin, body(newParty("SUPPLIER"), item(product, "1", "-1"))))).as("negative rate").isEqualTo(422);
        assertThat(status(create("material-in", admin, body(newParty("SUPPLIER"), item(inactive, "1", "1"))))).as("inactive product").isEqualTo(422);
        assertThat(status(create("material-in", admin, body(newParty("SUPPLIER"), item(999_999_999L, "1", "1"))))).as("unknown product").isEqualTo(422);
        assertThat(status(create("material-in", admin, body(null, item(product, "1", "1"))))).as("IN needs party").isEqualTo(422);
        assertThat(status(create("material-in", admin, body(newParty("SUPPLIER"), item(product, "99999999999", "999999999999"))))).as("too large").isEqualTo(422);
        List<Object> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i < 101; i++) {
            tooMany.add(item(product, "1", "1"));
        }
        java.util.Map<String, Object> body = body(newParty("SUPPLIER"));
        body.put("items", tooMany);
        assertThat(status(create("material-in", admin, body))).as("101 items").isEqualTo(422);
    }

    @Test
    void partyTypeRules() throws Exception {
        long product = newProduct();
        created("material-in", body(newParty("SUPPLIER"), item(product, "100", "1")));
        long inactiveSupplier = newParty("SUPPLIER");
        jdbc.update("update parties set status = 'INACTIVE' where id = ?", inactiveSupplier);

        assertThat(status(create("material-in", admin, body(newParty("CLIENT"), item(product, "1", "1"))))).as("IN from client").isEqualTo(422);
        assertThat(status(create("material-in", admin, body(inactiveSupplier, item(product, "1", "1"))))).as("inactive party").isEqualTo(422);
        assertThat(status(create("material-in", admin, body(newParty("BOTH"), item(product, "1", "1"))))).isEqualTo(201);
        assertThat(status(create("material-out", admin, body(newParty("SUPPLIER"), item(product, "1", "1"))))).as("OUT to supplier").isEqualTo(422);
        assertThat(status(create("material-out", admin, body(newParty("BOTH"), item(product, "1", "1"))))).isEqualTo(201);
        assertThat(status(create("material-out", admin, body(null, item(product, "1", "1"))))).as("OUT party optional").isEqualTo(201);
    }

    @Test
    void parallelCreatesGetUniqueNumbers() throws Exception {
        long product = newProduct();
        long supplier = newParty("SUPPLIER");
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(20);
        try {
            List<java.util.concurrent.Future<String>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 20; i++) {
                futures.add(pool.submit(() -> {
                    MvcResult r = create("material-in", admin, body(supplier, item(product, "1", "1")));
                    assertThat(status(r)).isEqualTo(201);
                    return tree(r).get("transactionNumber").asText();
                }));
            }
            java.util.Set<String> numbers = new java.util.HashSet<>();
            for (java.util.concurrent.Future<String> f : futures) {
                numbers.add(f.get());
            }
            assertThat(numbers).hasSize(20);
        } finally {
            pool.shutdownNow();
        }
        assertStock(product, "20");
    }

    private long lastNumber(String type) {
        return jdbc.queryForObject("select coalesce(max(last_number), 0) from document_sequences where document_type = ?", Long.class, type);
    }
}
