package com.materialbook.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.materialbook.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The backend serves the compiled SPA (fixtures in src/test/resources/static) and carries the security headers nginx used to add. */
@AutoConfigureMockMvc
class StaticAppAndHeadersTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;

    private MockHttpServletResponse res(MockHttpServletRequestBuilder b) throws Exception {
        return mvc.perform(b).andReturn().getResponse();
    }

    private static void assertSecurityHeaders(MockHttpServletResponse r) {
        assertThat(r.getHeaders("Content-Security-Policy")).hasSize(1);
        assertThat(r.getHeader("Content-Security-Policy")).isEqualTo(
                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; "
                        + "connect-src 'self'; worker-src 'self'; manifest-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'");
        assertThat(r.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(r.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(r.getHeader("Referrer-Policy")).isEqualTo("same-origin");
        assertThat(r.getHeader("Permissions-Policy")).contains("camera=()", "microphone=()", "geolocation=()", "payment=()", "usb=()");
        assertThat(r.getHeader("Cross-Origin-Opener-Policy")).isEqualTo("same-origin");
    }

    @Test
    void rootAndClientRoutesReturnIndexHtmlWithNoCache() throws Exception {
        for (String path : List.of("/", "/index.html", "/material-in", "/suppliers/1/ledger", "/reports/stock")) {
            MockHttpServletResponse r = res(get(path));
            assertThat(r.getStatus()).as(path).isEqualTo(200);
            assertThat(r.getContentAsString()).as(path).contains("<div id=\"root\">");
            assertThat(r.getHeader(HttpHeaders.CACHE_CONTROL)).as(path).isEqualTo("no-cache");
            assertThat(r.getContentType()).as(path).startsWith("text/html");
            assertSecurityHeaders(r);
        }
        assertThat(res(head("/material-in")).getStatus()).isEqualTo(200);
    }

    @Test
    void hashedAssetsAreImmutableAndOtherFilesAreNoCache() throws Exception {
        MockHttpServletResponse asset = res(get("/assets/app.123abc.js"));
        assertThat(asset.getStatus()).isEqualTo(200);
        assertThat(asset.getContentAsString()).contains("console.log");
        assertThat(asset.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("max-age=31536000, public, immutable");
        assertSecurityHeaders(asset);

        for (String p : List.of("/sw.js", "/manifest.webmanifest")) {
            MockHttpServletResponse r = res(get(p));
            assertThat(r.getStatus()).as(p).isEqualTo(200);
            assertThat(r.getHeader(HttpHeaders.CACHE_CONTROL)).as(p).isEqualTo("no-cache");
        }
        assertThat(res(get("/manifest.webmanifest")).getContentType()).startsWith("application/manifest+json");
        // a missing hashed asset is a 404, never index.html; a missing file with an extension too
        assertThat(res(get("/assets/missing.js")).getStatus()).isEqualTo(404);
        assertThat(res(get("/nope.png")).getStatus()).isEqualTo(404);
    }

    @Test
    void unknownApiPathsAreProblemJsonNeverIndexHtml() throws Exception {
        MockHttpServletResponse anon = res(get("/api/v1/unknown"));
        assertThat(anon.getStatus()).isEqualTo(401);
        assertThat(anon.getContentType()).startsWith("application/problem+json");
        assertThat(anon.getContentAsString()).doesNotContain("<div");
        assertSecurityHeaders(anon);

        MockHttpServletResponse authed = res(get("/api/v1/unknown").with(jwt()));
        assertThat(authed.getStatus()).isEqualTo(404);
        assertThat(authed.getContentType()).startsWith("application/problem+json");
        assertThat(authed.getContentAsString()).doesNotContain("<div");
        assertThat(res(get("/api").with(jwt())).getContentAsString()).doesNotContain("<div");
        assertThat(res(get("/actuator/unknown").with(jwt())).getContentAsString()).doesNotContain("<div");
    }

    @Test
    void writesOnNonApiPathsAreDenied() throws Exception {
        for (MockHttpServletRequestBuilder b : List.of(post("/material-in"), put("/index.html"), delete("/assets/app.123abc.js"), post("/"))) {
            assertThat(res(b).getStatus()).isIn(401, 403);
        }
    }

    @Test
    void healthIsPublicAndCarriesSecurityHeaders() throws Exception {
        MockHttpServletResponse r = res(get("/actuator/health"));
        assertThat(r.getStatus()).isEqualTo(200);
        assertSecurityHeaders(r);
    }

    @Test
    void hstsOnlyOnHttps() throws Exception {
        assertThat(res(get("/").secure(true)).getHeader("Strict-Transport-Security")).startsWith("max-age=31536000");
        assertThat(res(get("/")).getHeader("Strict-Transport-Security")).isNull();
    }

    @Test
    void traversalAttemptsDoNotLeakFiles() throws Exception {
        for (String p : List.of("/..%2f..%2fetc/passwd", "/assets/../application.yml", "/assets/..%2fapplication.yml", "/%2e%2e/application.yml",
                "/assets/%2e%2e/%2e%2e/application.yml", "/application.yml", "/application-prod.yml", "/db/migration/V1__init.sql")) {
            MockHttpServletResponse r = res(get(p));
            assertThat(r.getStatus()).as(p).isGreaterThanOrEqualTo(400);
            assertThat(r.getContentAsString()).as(p).doesNotContain("spring:", "root:", "create table");
        }
    }
}
