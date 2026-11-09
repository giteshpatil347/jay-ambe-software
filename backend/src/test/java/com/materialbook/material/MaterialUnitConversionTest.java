package com.materialbook.material;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Entry unit (TON/KG/PIECE) is converted to the product unit; the rate stays per product unit. */
class MaterialUnitConversionTest extends AbstractMaterialTest {

    private static Map<String, Object> item(long product, String qty, String rate, String unit) {
        Map<String, Object> m = item(product, qty, rate);
        if (unit != null) {
            m.put("unit", unit);
        }
        return m;
    }

    @Test
    void kgIntoTonProductStoresTonAndAmountUsesConvertedQuantity() throws Exception {
        long ton = newProduct("TON");
        JsonNode tx = created("material-in", body(newParty("SUPPLIER"), item(ton, "500", "15000", "KG")));
        JsonNode line = tx.get("items").get(0);
        assertThat(line.get("quantity").asText()).isEqualTo("0.500");
        assertThat(line.get("unit").asText()).isEqualTo("TON");
        assertThat(line.get("rate").asText()).isEqualTo("15000.00");
        assertThat(line.get("amount").asText()).isEqualTo("7500.00");
        assertThat(tx.get("totalAmount").asText()).isEqualTo("7500.00");
        assertStock(ton, "0.5");
    }

    @Test
    void tonIntoKgProductStoresKg() throws Exception {
        long kg = newProduct("KG");
        JsonNode tx = created("material-in", body(newParty("SUPPLIER"), item(kg, "2", "12.50", "TON")));
        assertThat(tx.get("items").get(0).get("quantity").asText()).isEqualTo("2000.000");
        assertThat(tx.get("totalAmount").asText()).isEqualTo("25000.00");
        assertStock(kg, "2000");
    }

    @Test
    void omittedOrSameUnitBehavesAsBefore() throws Exception {
        long ton = newProduct("TON");
        long supplier = newParty("SUPPLIER");
        created("material-in", body(supplier, item(ton, "3", "10")));
        created("material-in", body(supplier, item(ton, "2", "10", "TON")));
        assertStock(ton, "5");
    }

    @Test
    void outWithConvertedQuantityRespectsStock() throws Exception {
        long ton = newProduct("TON");
        created("material-in", body(newParty("SUPPLIER"), item(ton, "1", "100")));
        long client = newParty("CLIENT");
        MvcResult tooMuch = create("material-out", admin, body(client, item(ton, "1001", null, "KG")));
        assertThat(status(tooMuch)).isEqualTo(409);
        assertStock(ton, "1");
        JsonNode ok = created("material-out", body(client, item(ton, "400", null, "KG")));
        assertThat(ok.get("items").get(0).get("quantity").asText()).isEqualTo("0.400");
        assertStock(ton, "0.6");
    }

    @Test
    void editWithUnitReplacesStockUsingConvertedQuantity() throws Exception {
        long ton = newProduct("TON");
        long supplier = newParty("SUPPLIER");
        JsonNode tx = created("material-in", body(supplier, item(ton, "1", "100")));
        MvcResult r = edit("material-in", admin, tx.get("id").asLong(), body(supplier, item(ton, "250", "100", "KG")), tx.get("version").asLong());
        assertThat(status(r)).isEqualTo(200);
        assertThat(tree(r).get("items").get(0).get("quantity").asText()).isEqualTo("0.250");
        assertThat(tree(r).get("totalAmount").asText()).isEqualTo("25.00");
        assertStock(ton, "0.25");
    }

    @Test
    void invalidUnitAndImpossibleMixesAre422WithItemFieldErrors() throws Exception {
        long ton = newProduct("TON");
        long piece = newProduct("PIECE");
        long supplier = newParty("SUPPLIER");

        MvcResult bad = create("material-in", admin, body(supplier, item(ton, "1", "1", "LITRE")));
        assertThat(status(bad)).isEqualTo(422);
        assertThat(tree(bad).get("fieldErrors").get(0).get("field").asText()).isEqualTo("items[0].unit");

        assertThat(status(create("material-in", admin, body(supplier, item(piece, "1", "1", "KG"))))).isEqualTo(422);
        assertThat(status(create("material-in", admin, body(supplier, item(ton, "1", "1", "PIECE"))))).isEqualTo(422);
        assertThat(status(create("material-in", admin, body(supplier, item(piece, "4", "1", "PIECE"))))).isEqualTo(201);
    }

    @Test
    void tooSmallAfterConversionIs422OnThatItem() throws Exception {
        long ton = newProduct("TON");
        MvcResult r = create("material-in", admin, body(newParty("SUPPLIER"), item(ton, "1", "1"), item(ton, "0.4", "1", "KG")));
        assertThat(status(r)).isEqualTo(422);
        JsonNode err = tree(r).get("fieldErrors").get(0);
        assertThat(err.get("field").asText()).isEqualTo("items[1].quantity");
        assertThat(err.get("message").asText()).contains("too small");
        assertThat(movementCount(ton)).isZero();
    }

    @Test
    void conversionOverflowingTheColumnIs422() throws Exception {
        long kg = newProduct("KG");
        MvcResult r = create("material-in", admin, body(newParty("SUPPLIER"), item(kg, "99999999999", "0", "TON")));
        assertThat(status(r)).isEqualTo(422);
    }
}
