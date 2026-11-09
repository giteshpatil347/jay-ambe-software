package com.materialbook.users;

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

/** Shared helpers: everything goes through the HTTP API, starting from the bootstrap SUPER_ADMIN. */
@AutoConfigureMockMvc
abstract class AbstractUserApiTest extends AbstractIntegrationTest {
    static final String PASSWORD = "Temporary-Pass-1";
    private static final AtomicInteger SEQ = new AtomicInteger(10_000_000);

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;

    protected static String newMobile() { return "97" + SEQ.incrementAndGet(); }

    protected MvcResult call(HttpMethod method, String url, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder b = request(method, url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(b).andReturn();
    }

    protected JsonNode tree(MvcResult r) throws Exception { return json.readTree(r.getResponse().getContentAsString()); }

    protected String login(String mobile, String password) throws Exception {
        skipForcedPasswordChange(mobile);
        MvcResult r = mvc.perform(request(HttpMethod.POST, "/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("mobile", mobile, "password", password)))).andReturn();
        if (r.getResponse().getStatus() != 200) {
            throw new AssertionError("login failed for " + mobile + ": " + r.getResponse().getStatus());
        }
        return tree(r).get("accessToken").asText();
    }

    protected String superToken() throws Exception { return login("9000000001", "Bootstrap-Pass-1"); }

    protected Map<String, Object> userBody(String mobile, String... roleCodes) {
        return Map.of("fullName", "User " + mobile, "mobile", mobile, "password", PASSWORD, "roleCodes", List.of(roleCodes));
    }

    /** Creates a user through the API as the given actor token and returns its id. */
    protected long createUser(String actorToken, String mobile, String... roleCodes) throws Exception {
        MvcResult r = call(HttpMethod.POST, "/api/v1/users", actorToken, userBody(mobile, roleCodes));
        if (r.getResponse().getStatus() != 201) {
            throw new AssertionError("create user failed: " + r.getResponse().getContentAsString());
        }
        return tree(r).get("id").asLong();
    }

    protected long auditCount(String action, long recordId) {
        return jdbc.queryForObject("select count(*) from audit_logs where action = ? and record_id = ?", Long.class, action, recordId);
    }

    protected List<String> strings(JsonNode array) {
        return json.convertValue(array, json.getTypeFactory().constructCollectionType(List.class, String.class));
    }
}
