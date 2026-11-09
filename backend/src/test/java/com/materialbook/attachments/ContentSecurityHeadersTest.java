package com.materialbook.attachments;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;

/** The global CSP (default-src 'self') must not replace or duplicate the sandbox CSP of user-supplied file content. */
class ContentSecurityHeadersTest extends AbstractAttachmentTest {
    @Test
    void attachmentContentKeepsItsOwnSandboxCspAndStillGetsTheOtherSecurityHeaders() throws Exception {
        String admin = superToken();
        long tx = newMaterialTransaction("IN");
        long id = tree(upload(admin, "MATERIAL_TRANSACTION", tx, "bill.pdf", "application/pdf", FileValidatorTest.pdf())).get("id").asLong();

        MockHttpServletResponse r = call(HttpMethod.GET, "/api/v1/attachments/" + id + "/content", admin, null).getResponse();
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(r.getHeaders("Content-Security-Policy")).containsExactly("default-src 'none'; sandbox");
        assertThat(r.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(r.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(r.getHeader("Cache-Control")).isEqualTo("private, max-age=300");
        assertThat(r.getHeader("Cross-Origin-Opener-Policy")).isEqualTo("same-origin");
    }
}
