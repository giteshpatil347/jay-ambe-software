package com.materialbook.products;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

class ProductApiTest extends AbstractCatalogApiTest {
    private long categoryId(String token, String name) throws Exception {
        MvcResult r = call(HttpMethod.POST, "/api/v1/product-categories", token, Map.of("name", name));
        assertThat(status(r)).isEqualTo(201);
        return tree(r).get("id").asLong();
    }

    private JsonNode createProduct(String token, String name, Long categoryId, String unit, String code) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("name", name, "unit", unit, "minimumStock", "5.500"));
        if (categoryId != null) body.put("categoryId", categoryId);
        if (code != null) body.put("productCode", code);
        MvcResult r = call(HttpMethod.POST, "/api/v1/products", token, body);
        assertThat(status(r)).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return tree(r);
    }

    @Test
    void createGeneratesCodeReadsBackAndAudits() throws Exception {
        String token = superToken();
        long cat = categoryId(token, "Steel-" + System.nanoTime());
        JsonNode p = createProduct(token, "TMT Bar 12mm", cat, "TON", null);
        assertThat(p.get("code").asText()).matches("PRD-\\d{4,}");
        assertThat(p.get("availableStock").asText()).isEqualTo("0.000");
        assertThat(p.get("minimumStock").asText()).isEqualTo("5.500");
        assertThat(p.get("status").asText()).isEqualTo("ACTIVE");

        long id = p.get("id").asLong();
        JsonNode got = tree(call(HttpMethod.GET, "/api/v1/products/" + id, token, null));
        assertThat(got.get("categoryId").asLong()).isEqualTo(cat);
        assertThat(got.get("categoryName").asText()).startsWith("Steel-");
        assertThat(auditCount("PRODUCT_CREATE", id)).isEqualTo(1);

        MvcResult upd = call(HttpMethod.PUT, "/api/v1/products/" + id, token,
                Map.of("name", "TMT Bar 16mm", "unit", "TON", "categoryId", cat));
        assertThat(status(upd)).isEqualTo(200);
        assertThat(tree(upd).get("name").asText()).isEqualTo("TMT Bar 16mm");
        assertThat(auditCount("PRODUCT_UPDATE", id)).isEqualTo(1);
    }

    @Test
    void duplicateCodeAndCategoryAreConflicts() throws Exception {
        String token = superToken();
        createProduct(token, "Cement", null, "KG", "CEM-DUP");
        MvcResult dup = call(HttpMethod.POST, "/api/v1/products", token, Map.of("name", "Other", "unit", "KG", "productCode", "cem-dup"));
        assertThat(status(dup)).isEqualTo(409);

        String name = "Cat-" + System.nanoTime();
        categoryId(token, name);
        assertThat(status(call(HttpMethod.POST, "/api/v1/product-categories", token, Map.of("name", name.toUpperCase())))).isEqualTo(409);
    }

    @Test
    void validationErrorsAre422() throws Exception {
        String token = superToken();
        assertThat(status(call(HttpMethod.POST, "/api/v1/products", token, Map.of("name", " ", "unit", "TON")))).isEqualTo(422);
        assertThat(status(call(HttpMethod.POST, "/api/v1/products", token, Map.of("name", "X", "unit", "TON", "minimumStock", "-1")))).isEqualTo(422);
        assertThat(status(call(HttpMethod.POST, "/api/v1/products", token, Map.of("name", "X", "unit", "TON", "categoryId", 999999)))).isEqualTo(422);
    }

    @Test
    void searchFilterAndPagination() throws Exception {
        String token = superToken();
        String tag = "Zq" + System.nanoTime();
        long cat = categoryId(token, "Cat-" + tag);
        for (int i = 1; i <= 3; i++) createProduct(token, tag + " item " + i, cat, "PIECE", null);
        long other = createProduct(token, tag + " lone", null, "PIECE", null).get("id").asLong();
        call(HttpMethod.POST, "/api/v1/products/" + other + "/deactivate", token, null);

        JsonNode page = tree(call(HttpMethod.GET, "/api/v1/products?q=" + tag.toLowerCase() + "&size=2", token, null));
        assertThat(page.get("totalElements").asInt()).isEqualTo(4);
        assertThat(page.get("content")).hasSize(2);

        assertThat(tree(call(HttpMethod.GET, "/api/v1/products?q=" + tag + "&categoryId=" + cat, token, null)).get("totalElements").asInt()).isEqualTo(3);
        assertThat(tree(call(HttpMethod.GET, "/api/v1/products?q=" + tag + "&status=INACTIVE", token, null)).get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void deactivateAndActivateAreSoftAndAudited() throws Exception {
        String token = superToken();
        long id = createProduct(token, "Soft delete", null, "TON", null).get("id").asLong();
        assertThat(tree(call(HttpMethod.POST, "/api/v1/products/" + id + "/deactivate", token, null)).get("status").asText()).isEqualTo("INACTIVE");
        assertThat(tree(call(HttpMethod.POST, "/api/v1/products/" + id + "/activate", token, null)).get("status").asText()).isEqualTo("ACTIVE");
        assertThat(auditCount("PRODUCT_DEACTIVATE", id)).isEqualTo(1);
        assertThat(auditCount("PRODUCT_ACTIVATE", id)).isEqualTo(1);
        assertThat(status(call(HttpMethod.GET, "/api/v1/products/" + id, token, null))).isEqualTo(200);
        assertThat(status(call(HttpMethod.GET, "/api/v1/products/999999", token, null))).isEqualTo(404);
    }

    @Test
    void unitIsImmutableOnceProductHasMovements() throws Exception {
        String token = superToken();
        long id = createProduct(token, "Locked unit", null, "TON", null).get("id").asLong();
        Map<String, Object> toKg = Map.of("name", "Locked unit", "unit", "KG");
        assertThat(status(call(HttpMethod.PUT, "/api/v1/products/" + id, token, toKg))).isEqualTo(200);

        Long userId = jdbc.queryForObject("select id from users limit 1", Long.class);
        jdbc.update("insert into stock_movements(product_id, movement_type, quantity_delta, movement_date, created_by) "
                + "values (?, 'OPENING', 10, current_date, ?)", id, userId);
        MvcResult blocked = call(HttpMethod.PUT, "/api/v1/products/" + id, token, Map.of("name", "Locked unit", "unit", "TON"));
        assertThat(status(blocked)).isEqualTo(409);
        // same unit is still editable
        assertThat(status(call(HttpMethod.PUT, "/api/v1/products/" + id, token, Map.of("name", "Renamed", "unit", "KG")))).isEqualTo(200);
    }

    @Test
    void viewerCanReadButNotWrite() throws Exception {
        String viewer = tokenFor("VIEWER");
        assertThat(status(call(HttpMethod.GET, "/api/v1/products", viewer, null))).isEqualTo(200);
        assertThat(status(call(HttpMethod.GET, "/api/v1/product-categories", viewer, null))).isEqualTo(200);
        assertThat(status(call(HttpMethod.POST, "/api/v1/products", viewer, Map.of("name", "Nope", "unit", "TON")))).isEqualTo(403);
        assertThat(status(call(HttpMethod.POST, "/api/v1/product-categories", viewer, Map.of("name", "Nope")))).isEqualTo(403);
        assertThat(status(call(HttpMethod.POST, "/api/v1/products/1/deactivate", viewer, null))).isEqualTo(403);
    }

    @Test
    void categoryUpdateCanDeactivate() throws Exception {
        String token = superToken();
        long id = categoryId(token, "Upd-" + System.nanoTime());
        MvcResult r = call(HttpMethod.PUT, "/api/v1/product-categories/" + id, token, Map.of("name", "Upd renamed " + id, "status", "INACTIVE"));
        assertThat(status(r)).isEqualTo(200);
        assertThat(tree(r).get("status").asText()).isEqualTo("INACTIVE");
        assertThat(auditCount("PRODUCT_CATEGORY_UPDATE", id)).isEqualTo(1);
    }
}
