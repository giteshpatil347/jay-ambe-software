package com.materialbook.parties;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.materialbook.products.AbstractCatalogApiTest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

class PartyApiTest extends AbstractCatalogApiTest {
    private static final String GST_A = "27AAPFU0939F1ZV";
    private static final String GST_B = "29ABCDE1234F1Z5";

    private Map<String, Object> body(String name, String type) {
        return new HashMap<>(Map.of("name", name, "type", type));
    }

    private JsonNode create(String token, Map<String, Object> body) throws Exception {
        MvcResult r = call(HttpMethod.POST, "/api/v1/parties", token, body);
        assertThat(status(r)).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return tree(r);
    }

    @Test
    void createUpdateAndAudit() throws Exception {
        String token = superToken();
        Map<String, Object> b = body("Sharma Traders", "SUPPLIER");
        b.put("mobile", "+919876543210");
        b.put("openingBalance", "-1500.50");
        JsonNode p = create(token, b);
        long id = p.get("id").asLong();
        assertThat(p.get("code").asText()).matches("PTY-\\d{4,}");
        assertThat(p.get("openingBalance").asText()).isEqualTo("-1500.50");
        assertThat(auditCount("PARTY_CREATE", id)).isEqualTo(1);

        b.put("name", "Sharma Traders Pvt");
        b.put("address", "Pune");
        MvcResult upd = call(HttpMethod.PUT, "/api/v1/parties/" + id, token, b);
        assertThat(status(upd)).isEqualTo(200);
        assertThat(tree(upd).get("address").asText()).isEqualTo("Pune");
        assertThat(auditCount("PARTY_UPDATE", id)).isEqualTo(1);

        assertThat(tree(call(HttpMethod.POST, "/api/v1/parties/" + id + "/deactivate", token, null)).get("status").asText()).isEqualTo("INACTIVE");
        assertThat(tree(call(HttpMethod.POST, "/api/v1/parties/" + id + "/activate", token, null)).get("status").asText()).isEqualTo("ACTIVE");
        assertThat(auditCount("PARTY_DEACTIVATE", id)).isEqualTo(1);
        assertThat(auditCount("PARTY_ACTIVATE", id)).isEqualTo(1);
        assertThat(status(call(HttpMethod.GET, "/api/v1/parties/999999", token, null))).isEqualTo(404);
    }

    @Test
    void gstAndMobileValidationAnd409OnDuplicateGst() throws Exception {
        String token = superToken();
        Map<String, Object> bad = body("Bad Gst", "CLIENT");
        bad.put("gstNumber", "NOT-A-GST");
        assertThat(status(call(HttpMethod.POST, "/api/v1/parties", token, bad))).isEqualTo(422);

        Map<String, Object> badMobile = body("Bad Mobile", "CLIENT");
        badMobile.put("mobile", "12345");
        assertThat(status(call(HttpMethod.POST, "/api/v1/parties", token, badMobile))).isEqualTo(422);
        assertThat(status(call(HttpMethod.POST, "/api/v1/parties", token, body(" ", "CLIENT")))).isEqualTo(422);

        Map<String, Object> a = body("Gst A", "CLIENT");
        a.put("gstNumber", GST_A);
        create(token, a);
        assertThat(status(call(HttpMethod.POST, "/api/v1/parties", token, a))).isEqualTo(409);

        Map<String, Object> b = body("Gst B", "CLIENT");
        b.put("gstNumber", GST_B);
        long idB = create(token, b).get("id").asLong();
        b.put("gstNumber", GST_A);
        assertThat(status(call(HttpMethod.PUT, "/api/v1/parties/" + idB, token, b))).isEqualTo(409);
    }

    @Test
    void typeFilterIncludesBoth() throws Exception {
        String token = superToken();
        String tag = "Tf" + System.nanoTime();
        create(token, body(tag + " sup", "SUPPLIER"));
        create(token, body(tag + " cli", "CLIENT"));
        create(token, body(tag + " both", "BOTH"));

        assertThat(tree(call(HttpMethod.GET, "/api/v1/parties?q=" + tag + "&type=SUPPLIER", token, null)).get("totalElements").asInt()).isEqualTo(2);
        assertThat(tree(call(HttpMethod.GET, "/api/v1/parties?q=" + tag + "&type=CLIENT", token, null)).get("totalElements").asInt()).isEqualTo(2);
        assertThat(tree(call(HttpMethod.GET, "/api/v1/parties?q=" + tag + "&type=BOTH", token, null)).get("totalElements").asInt()).isEqualTo(1);
        assertThat(tree(call(HttpMethod.GET, "/api/v1/parties?q=" + tag, token, null)).get("totalElements").asInt()).isEqualTo(3);
        JsonNode paged = tree(call(HttpMethod.GET, "/api/v1/parties?q=" + tag + "&size=2&page=1", token, null));
        assertThat(paged.get("content")).hasSize(1);
        assertThat(paged.get("page").asInt()).isEqualTo(1);
    }

    @Test
    void searchByMobileAndStatus() throws Exception {
        String token = superToken();
        Map<String, Object> b = body("Mobile Search", "CLIENT");
        b.put("mobile", "9123456780");
        long id = create(token, b).get("id").asLong();
        call(HttpMethod.POST, "/api/v1/parties/" + id + "/deactivate", token, null);
        assertThat(tree(call(HttpMethod.GET, "/api/v1/parties?q=9123456780", token, null)).get("totalElements").asInt()).isEqualTo(1);
        assertThat(tree(call(HttpMethod.GET, "/api/v1/parties?q=9123456780&status=ACTIVE", token, null)).get("totalElements").asInt()).isZero();
    }

    @Test
    void openingBalanceLockedOnceTransactionsExist() throws Exception {
        String token = superToken();
        Map<String, Object> b = body("Ledger Party", "SUPPLIER");
        b.put("openingBalance", "100.00");
        long id = create(token, b).get("id").asLong();

        b.put("openingBalance", "250.00");
        assertThat(status(call(HttpMethod.PUT, "/api/v1/parties/" + id, token, b))).isEqualTo(200);

        Long userId = jdbc.queryForObject("select id from users limit 1", Long.class);
        jdbc.update("insert into party_payments(payment_number, party_id, payment_date, direction, amount, created_by) "
                + "values (?, ?, current_date, 'PAID', 10, ?)", "PAY-T07-" + id, id, userId);
        b.put("openingBalance", "999.00");
        assertThat(status(call(HttpMethod.PUT, "/api/v1/parties/" + id, token, b))).isEqualTo(409);
        b.put("openingBalance", "250.00");
        b.put("address", "still editable");
        assertThat(status(call(HttpMethod.PUT, "/api/v1/parties/" + id, token, b))).isEqualTo(200);
    }

    @Test
    void viewerCanNeitherReadNorWrite() throws Exception {
        String viewer = tokenFor("VIEWER");
        assertThat(status(call(HttpMethod.GET, "/api/v1/parties", viewer, null))).isEqualTo(403);
        assertThat(status(call(HttpMethod.POST, "/api/v1/parties", viewer, body("Nope", "CLIENT")))).isEqualTo(403);
        assertThat(status(call(HttpMethod.POST, "/api/v1/parties/1/deactivate", viewer, null))).isEqualTo(403);
    }
}
