package com.materialbook.expenses;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.materialbook.AbstractIntegrationTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** HTTP-level helpers for the expenses/employees tests; everything starts from the bootstrap SUPER_ADMIN. */
@AutoConfigureMockMvc
abstract class AbstractExpenseApiTest extends AbstractIntegrationTest {
    private static final AtomicInteger SEQ = new AtomicInteger(20_000_000);

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;

    protected String admin;
    protected long generalCategory;
    protected long salaryCategory;
    protected long electricityCategory;

    @BeforeEach
    void setUpTokens() throws Exception {
        admin = login("9000000001", "Bootstrap-Pass-1");
        salaryCategory = categoryId("STAFF_SALARY");
        electricityCategory = categoryId("ELECTRICITY");
        generalCategory = createCategory("GEN_" + SEQ.incrementAndGet(), "General " + SEQ.get(), "GENERAL");
    }

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

    /** Token of a freshly created user holding only the given role. */
    protected String tokenWithRole(String role) throws Exception {
        String mobile = "96" + SEQ.incrementAndGet();
        MvcResult r = call(HttpMethod.POST, "/api/v1/users", admin, Map.of("fullName", "U " + mobile, "mobile", mobile,
                "password", "Temporary-Pass-1", "roleCodes", List.of(role)));
        if (status(r) != 201) {
            throw new AssertionError(r.getResponse().getContentAsString());
        }
        return login(mobile, "Temporary-Pass-1");
    }

    protected long categoryId(String code) throws Exception {
        for (JsonNode c : tree(call(HttpMethod.GET, "/api/v1/expense-categories", admin, null))) {
            if (c.get("categoryCode").asText().equals(code)) {
                return c.get("id").asLong();
            }
        }
        throw new AssertionError("no category " + code);
    }

    protected long createCategory(String code, String name, String type) throws Exception {
        MvcResult r = call(HttpMethod.POST, "/api/v1/expense-categories", admin,
                Map.of("categoryCode", code, "categoryName", name, "categoryType", type));
        if (status(r) != 201) {
            throw new AssertionError(r.getResponse().getContentAsString());
        }
        return tree(r).get("id").asLong();
    }

    protected long createEmployee(String name) throws Exception {
        MvcResult r = call(HttpMethod.POST, "/api/v1/employees", admin, Map.of("employeeName", name, "monthlySalary", "5000.00"));
        if (status(r) != 201) {
            throw new AssertionError(r.getResponse().getContentAsString());
        }
        return tree(r).get("id").asLong();
    }

    protected Map<String, Object> expenseBody(long categoryId, String amount) {
        Map<String, Object> m = new HashMap<>();
        m.put("expenseDate", "2026-03-10");
        m.put("categoryId", categoryId);
        m.put("amount", amount);
        m.put("paidTo", "Vendor");
        m.put("paymentMode", "CASH");
        return m;
    }

    protected Map<String, Object> salaryBody(long employeeId, String amount) {
        Map<String, Object> m = expenseBody(salaryCategory, amount);
        m.put("salary", Map.of("employeeId", employeeId, "paymentType", "MONTHLY", "grossSalary", "5000.00",
                "advanceAdjustment", "500.00"));
        return m;
    }

    protected Map<String, Object> electricityBody(String amount) {
        Map<String, Object> m = expenseBody(electricityCategory, amount);
        m.put("electricity", Map.of("connectionNumber", "1_NO", "usageType", "DAILY", "usageQuantity", "3"));
        return m;
    }

    protected JsonNode createExpense(Map<String, Object> body) throws Exception {
        MvcResult r = call(HttpMethod.POST, "/api/v1/expenses", admin, body);
        if (status(r) != 201) {
            throw new AssertionError(r.getResponse().getContentAsString());
        }
        return tree(r);
    }

    protected long auditCount(String action, String module, long recordId) {
        return jdbc.queryForObject("select count(*) from audit_logs where action = ? and module = ? and record_id = ?",
                Long.class, action, module, recordId);
    }
}
