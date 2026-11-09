package com.materialbook.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.materialbook.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** application-prod.yml: API docs and Swagger UI are not served, actuator exposes health only. */
@ActiveProfiles("prod")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-test-secret-test-secret-0123456789",
        "app.cors.allowed-origin=https://materials.example.com"
})
class ProdProfileTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void apiDocsAndSwaggerUiAreNotServed() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
        mvc.perform(get("/v3/api-docs").with(jwt())).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui/index.html").with(jwt())).andExpect(status().isNotFound());
    }

    @Test
    void onlyHealthIsExposedByActuator() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env").with(jwt())).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/beans").with(jwt())).andExpect(status().isNotFound());
    }
}
