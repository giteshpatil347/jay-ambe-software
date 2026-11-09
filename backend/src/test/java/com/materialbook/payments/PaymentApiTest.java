package com.materialbook.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.materialbook.products.AbstractCatalogApiTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

class PaymentApiTest extends AbstractCatalogApiTest {
    private static final AtomicInteger SEQ = new AtomicInteger();

    private String admin;
    private long partyId;

    @BeforeEach
    void setUp() throws Exception {
        admin = superToken();
        partyId = createParty("Pay Party " + SEQ.incrementAndGet());
    }

    private long createParty(String name) throws Exception {
        MvcResult r = call(HttpMethod.POST, "/api/v1/parties", admin, Map.of("name", name, "type", "BOTH"));
        assertThat(status(r)).isEqualTo(201);
        return tree(r).get("id").asLong();
    }

    private Map<String, Object> body(long party, String direction, String amount) {
        Map<String, Object> m = new HashMap<>();
        m.put("partyId", party);
        m.put("paymentDate", "2026-03-10");
        m.put("direction", direction);
        m.put("amount", amount);
        m.put("paymentMode", "UPI");
        m.put("referenceNumber", "UTR-" + SEQ.incrementAndGet());
        return m;
    }

    private JsonNode create(Map<String, Object> body) throws Exception {
        MvcResult r = call(HttpMethod.POST, "/api/v1/payments", admin, body);
        assertThat(status(r)).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return tree(r);
    }

    @Test
    void createGetAndAudit() throws Exception {
        JsonNode p = create(body(partyId, "PAID", "1500.50"));
        long id = p.get("id").asLong();
        assertThat(p.get("paymentNumber").asText()).matches("PAY-\\d{4}-\\d{5}");
        assertThat(p.get("amount").asText()).isEqualTo("1500.50");
        assertThat(p.get("status").asText()).isEqualTo("POSTED");
        assertThat(p.get("direction").asText()).isEqualTo("PAID");
        assertThat(p.get("partyName").asText()).startsWith("Pay Party");
        assertThat(auditCount("PAYMENT_CREATE", id)).isEqualTo(1);
        assertThat(tree(call(HttpMethod.GET, "/api/v1/payments/" + id, admin, null)).get("paymentNumber").asText())
                .isEqualTo(p.get("paymentNumber").asText());
        assertThat(status(call(HttpMethod.GET, "/api/v1/payments/999999999", admin, null))).isEqualTo(404);
    }

    @Test
    void validationRules() throws Exception {
        assertThat(status(call(HttpMethod.POST, "/api/v1/payments", admin, body(partyId, "PAID", "0")))).isEqualTo(422);
        assertThat(status(call(HttpMethod.POST, "/api/v1/payments", admin, body(partyId, "PAID", "-5.00")))).isEqualTo(422);
        assertThat(status(call(HttpMethod.POST, "/api/v1/payments", admin, body(partyId, "PAID", "1.234")))).isEqualTo(422);
        Map<String, Object> noDirection = body(partyId, "PAID", "10.00");
        noDirection.remove("direction");
        assertThat(status(call(HttpMethod.POST, "/api/v1/payments", admin, noDirection))).isEqualTo(422);
        assertThat(status(call(HttpMethod.POST, "/api/v1/payments", admin, body(999_999_999L, "PAID", "10.00")))).isEqualTo(404);

        call(HttpMethod.POST, "/api/v1/parties/" + partyId + "/deactivate", admin, null);
        assertThat(status(call(HttpMethod.POST, "/api/v1/payments", admin, body(partyId, "PAID", "10.00")))).isEqualTo(422);
    }

    @Test
    void cancelRules() throws Exception {
        long id = create(body(partyId, "RECEIVED", "200.00")).get("id").asLong();
        assertThat(status(call(HttpMethod.POST, "/api/v1/payments/" + id + "/cancel", admin, Map.of()))).isEqualTo(422);
        assertThat(status(call(HttpMethod.POST, "/api/v1/payments/" + id + "/cancel", admin, Map.of("reason", " ")))).isEqualTo(422);

        MvcResult r = call(HttpMethod.POST, "/api/v1/payments/" + id + "/cancel", admin, Map.of("reason", "Wrong party"));
        assertThat(status(r)).isEqualTo(200);
        assertThat(tree(r).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(tree(r).get("cancellationReason").asText()).isEqualTo("Wrong party");
        assertThat(tree(r).get("cancelledAt").isNull()).isFalse();
        assertThat(auditCount("PAYMENT_CANCEL", id)).isEqualTo(1);
        assertThat(status(call(HttpMethod.POST, "/api/v1/payments/" + id + "/cancel", admin, Map.of("reason", "again")))).isEqualTo(409);
        assertThat(status(call(HttpMethod.POST, "/api/v1/payments/999999999/cancel", admin, Map.of("reason", "x")))).isEqualTo(404);
        assertThat(status(call(HttpMethod.PUT, "/api/v1/payments/" + id, admin, body(partyId, "PAID", "1.00")))).isEqualTo(405);
    }

    @Test
    void listFiltersAndPostedTotal() throws Exception {
        long other = createParty("Other Party " + SEQ.incrementAndGet());
        create(body(partyId, "PAID", "100.00"));
        create(body(partyId, "RECEIVED", "40.00"));
        long cancelled = create(body(partyId, "PAID", "7.00")).get("id").asLong();
        call(HttpMethod.POST, "/api/v1/payments/" + cancelled + "/cancel", admin, Map.of("reason", "dup"));
        Map<String, Object> late = body(partyId, "PAID", "1.00");
        late.put("paymentDate", "2027-01-01");
        create(late);
        create(body(other, "PAID", "999.00"));

        JsonNode all = tree(call(HttpMethod.GET, "/api/v1/payments?partyId=" + partyId, admin, null));
        assertThat(all.get("totalElements").asLong()).isEqualTo(4);
        assertThat(all.get("totalAmount").asText()).isEqualTo("141.00");
        assertThat(all.get("content").get(0).get("paymentDate").asText()).isEqualTo("2027-01-01");

        JsonNode paid = tree(call(HttpMethod.GET, "/api/v1/payments?partyId=" + partyId + "&direction=PAID&to=2026-12-31", admin, null));
        assertThat(paid.get("totalElements").asLong()).isEqualTo(2);
        assertThat(paid.get("totalAmount").asText()).isEqualTo("100.00");

        JsonNode cancelledOnly = tree(call(HttpMethod.GET, "/api/v1/payments?partyId=" + partyId + "&status=CANCELLED", admin, null));
        assertThat(cancelledOnly.get("totalElements").asLong()).isEqualTo(1);
        assertThat(cancelledOnly.get("totalAmount").asText()).isEqualTo("0");

        JsonNode paged = tree(call(HttpMethod.GET, "/api/v1/payments?partyId=" + partyId + "&size=2&page=1", admin, null));
        assertThat(paged.get("content")).hasSize(2);
        assertThat(paged.get("totalAmount").asText()).isEqualTo("141.00");

        JsonNode byRef = tree(call(HttpMethod.GET, "/api/v1/payments?q=" + all.get("content").get(0).get("paymentNumber").asText().toLowerCase(), admin, null));
        assertThat(byRef.get("totalElements").asLong()).isEqualTo(1);
        JsonNode byUtr = tree(call(HttpMethod.GET, "/api/v1/payments?q=utr-", admin, null));
        assertThat(byUtr.get("totalElements").asLong()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void permissionsAreEnforced() throws Exception {
        String viewer = tokenFor("VIEWER");
        String purchase = tokenFor("PURCHASE_STAFF");
        long id = create(body(partyId, "PAID", "5.00")).get("id").asLong();
        assertThat(status(call(HttpMethod.GET, "/api/v1/payments", viewer, null))).isEqualTo(403); // VIEWER is limited to material + stock
        assertThat(status(call(HttpMethod.GET, "/api/v1/payments/" + id, viewer, null))).isEqualTo(403);
        assertThat(status(call(HttpMethod.POST, "/api/v1/payments", viewer, body(partyId, "PAID", "5.00")))).isEqualTo(403);
        assertThat(status(call(HttpMethod.POST, "/api/v1/payments/" + id + "/cancel", viewer, Map.of("reason", "x")))).isEqualTo(403);
        assertThat(status(call(HttpMethod.GET, "/api/v1/payments", purchase, null))).isEqualTo(403);
        assertThat(status(call(HttpMethod.GET, "/api/v1/payments", "bad-token", null))).isEqualTo(401);
    }

    @Test
    void parallelCreatesGetUniqueNumbers() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Callable<MvcResult>> jobs = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                jobs.add(() -> call(HttpMethod.POST, "/api/v1/payments", admin, body(partyId, "PAID", "1.00")));
            }
            Set<String> numbers = new HashSet<>();
            for (Future<MvcResult> f : pool.invokeAll(jobs)) {
                MvcResult r = f.get();
                assertThat(status(r)).isEqualTo(201);
                numbers.add(tree(r).get("paymentNumber").asText());
            }
            assertThat(numbers).hasSize(20);
        } finally {
            pool.shutdownNow();
        }
    }
}
