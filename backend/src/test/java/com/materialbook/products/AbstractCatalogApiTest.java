package com.materialbook.products;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.materialbook.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** HTTP-level helpers for the products and parties API tests. */
@AutoConfigureMockMvc
public abstract class AbstractCatalogApiTest extends AbstractIntegrationTest {
    private static final AtomicInteger SEQ = new AtomicInteger(30_000_000);

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;

    protected MvcResult call(HttpMethod method, String url, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder b = request(method, url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(b).andReturn();
    }

    protected JsonNode tree(MvcResult r) throws Exception { return json.readTree(r.getResponse().getContentAsString()); }

    protected int status(MvcResult r) { return r.getResponse().getStatus(); }

    protected String login(String mobile, String password) throws Exception {
        skipForcedPasswordChange(mobile);
        MvcResult r = mvc.perform(request(HttpMethod.POST, "/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("mobile", mobile, "password", password)))).andReturn();
        return tree(r).get("accessToken").asText();
    }

    protected String superToken() throws Exception { return login("9000000001", "Bootstrap-Pass-1"); }

    /** Creates a user with the given role through the API and returns its access token. */
    protected String tokenFor(String roleCode) throws Exception {
        String mobile = "97" + SEQ.incrementAndGet();
        MvcResult r = call(HttpMethod.POST, "/api/v1/users", superToken(), Map.of("fullName", "Tester " + mobile,
                "mobile", mobile, "password", "Temporary-Pass-1", "roleCodes", List.of(roleCode)));
        if (status(r) != 201) {
            throw new AssertionError("create user failed: " + r.getResponse().getContentAsString());
        }
        return login(mobile, "Temporary-Pass-1");
    }

    protected long auditCount(String action, long recordId) {
        return jdbc.queryForObject("select count(*) from audit_logs where action = ? and record_id = ?", Long.class, action, recordId);
    }
}
