package com.materialbook.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.materialbook.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** The limiter keys on the connection address only; client-supplied forwarding headers must not buy a fresh bucket. */
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-test-secret-test-secret-0123456789",
        "app.auth.rate-limit-per-minute=2"
})
class AuthRateLimitSpoofTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void changingForwardedForOrRealIpHeadersDoesNotResetTheBucket() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).header("X-Forwarded-For", "10.9.9." + i)
                    .content("{\"mobile\":\"1\",\"password\":\"x\"}")).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.77").header("X-Real-IP", "203.0.113.78").header("Forwarded", "for=203.0.113.79")
                        .content("{\"mobile\":\"1\",\"password\":\"x\"}"))
                .andExpect(status().isTooManyRequests());
    }
}
