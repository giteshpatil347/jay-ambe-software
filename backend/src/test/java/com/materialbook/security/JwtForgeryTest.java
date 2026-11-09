package com.materialbook.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.materialbook.AbstractIntegrationTest;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/** Access-token validation: only our HS256 tokens, unexpired, with our issuer, signed with our key, are accepted. */
@AutoConfigureMockMvc
class JwtForgeryTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Value("${app.jwt.secret}") String secret;

    private Long adminId() {
        return jdbc.queryForObject("select id from users where mobile = '9000000001'", Long.class);
    }

    private JWTClaimsSet.Builder claims(String issuer, Instant exp) {
        return new JWTClaimsSet.Builder().issuer(issuer).subject(String.valueOf(adminId()))
                .issueTime(new Date()).expirationTime(Date.from(exp)).claim("perms", List.of("USER.VIEW"));
    }

    private String signed(JWSAlgorithm alg, byte[] key, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(alg), claims);
        jwt.sign(new MACSigner(key));
        return jwt.serialize();
    }

    private int statusOf(String token) throws Exception {
        return mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn().getResponse().getStatus();
    }

    @Test
    void genuineTokenIsAccepted() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(adminId(), List.of())))
                .andExpect(status().isOk());
    }

    @Test
    void forgedExpiredOrForeignTokensAreRejectedWith401() throws Exception {
        byte[] realKey = secret.getBytes(StandardCharsets.UTF_8);
        byte[] otherKey = "another-secret-another-secret-another-secret-9876543210".getBytes(StandardCharsets.UTF_8);
        Instant future = Instant.now().plusSeconds(600);

        // alg=none (unsigned)
        assertThat401(new PlainJWT(claims("materialbook", future).build()).serialize());
        // right algorithm, wrong key
        assertThat401(signed(JWSAlgorithm.HS256, otherKey, claims("materialbook", future).build()));
        // valid key but a different HMAC algorithm than the pinned HS256 (key is long enough for HS384)
        byte[] longKey = new byte[64];
        System.arraycopy(realKey, 0, longKey, 0, realKey.length);
        assertThat401(signed(JWSAlgorithm.HS384, longKey, claims("materialbook", future).build()));
        // correctly signed but expired well beyond the clock skew
        assertThat401(signed(JWSAlgorithm.HS256, realKey, claims("materialbook", Instant.now().minusSeconds(3600)).build()));
        // correctly signed but issued by someone else
        assertThat401(signed(JWSAlgorithm.HS256, realKey, claims("someone-else", future).build()));
        // garbage
        assertThat401("not.a.jwt");
    }

    private void assertThat401(String token) throws Exception {
        org.assertj.core.api.Assertions.assertThat(statusOf(token)).as("token %s", token.substring(0, Math.min(20, token.length()))).isEqualTo(401);
    }
}
