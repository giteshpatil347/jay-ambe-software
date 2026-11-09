package com.materialbook.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.materialbook.products.AbstractCatalogApiTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

class BrandingApiTest extends AbstractCatalogApiTest {
    private static final String URL = "/api/v1/settings/branding";

    @AfterEach
    void reset() {
        jdbc.update("delete from business_settings where setting_key in ('BUSINESS_NAME', 'BUSINESS_ICON')");
    }

    private static byte[] image(String format, int size) throws Exception {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                img.setRGB(x, y, (x * 7 + y * 13) & 0xFFFFFF);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private MvcResult put(String token, String name, MockMultipartFile icon) throws Exception {
        MockMultipartHttpServletRequestBuilder b = multipart(HttpMethod.PUT, URL);
        if (name != null) {
            b.param("name", name);
        }
        if (icon != null) {
            b.file(icon);
        }
        if (token != null) {
            b.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(b).andReturn();
    }

    private static MockMultipartFile icon(String type, byte[] bytes) {
        return new MockMultipartFile("icon", "logo", type, bytes);
    }

    private String fieldError(MvcResult r, String field) throws Exception {
        for (var e : tree(r).get("fieldErrors")) {
            if (e.get("field").asText().equals(field)) {
                return e.get("message").asText();
            }
        }
        return null;
    }

    @Test
    void publicGetDefaultsToMaterialBookWithoutToken() throws Exception {
        MvcResult r = mvc.perform(get(URL)).andReturn();
        assertThat(status(r)).isEqualTo(200);
        assertThat(tree(r).get("name").asText()).isEqualTo("MaterialBook");
        assertThat(tree(r).get("hasIcon").asBoolean()).isFalse();
        assertThat(tree(r).get("iconVersion").asLong()).isZero();
        assertThat(status(mvc.perform(get(URL + "/icon")).andReturn())).isEqualTo(404);
    }

    @Test
    void putNeedsSettingsManage() throws Exception {
        assertThat(status(put(null, "X Co", null))).isEqualTo(401);
        assertThat(status(put(tokenFor("ADMIN"), "X Co", null))).isEqualTo(403); // ADMIN lacks SETTINGS.MANAGE (V3); SUPER_ADMIN only
        assertThat(status(call(HttpMethod.DELETE, URL + "/icon", tokenFor("ADMIN"), null))).isEqualTo(403);
        assertThat(status(call(HttpMethod.DELETE, URL + "/icon", null, null))).isEqualTo(401);
        assertThat(tree(mvc.perform(get(URL)).andReturn()).get("name").asText()).isEqualTo("MaterialBook");
    }

    @Test
    void nameIsTrimmedAndPublic() throws Exception {
        MvcResult r = put(superToken(), "  Shree Steel Traders  ", null);
        assertThat(status(r)).isEqualTo(200);
        assertThat(tree(r).get("name").asText()).isEqualTo("Shree Steel Traders");
        assertThat(tree(mvc.perform(get(URL)).andReturn()).get("name").asText()).isEqualTo("Shree Steel Traders");
    }

    @Test
    void nameValidation() throws Exception {
        String t = superToken();
        for (String bad : new String[] {"", "   ", "x".repeat(61), "Bad\u0000Name", "Line\nBreak", "Tab\tName"}) {
            MvcResult r = put(t, bad, null);
            assertThat(status(r)).as("name '%s'", bad.length() > 10 ? "len" + bad.length() : bad).isEqualTo(422);
            assertThat(fieldError(r, "name")).isNotNull();
        }
        assertThat(status(put(t, null, null))).isEqualTo(422);
        assertThat(status(put(t, "x".repeat(60), null))).isEqualTo(200);
    }

    @Test
    void iconRejections() throws Exception {
        String t = superToken();
        byte[] png = image("png", 16);
        String svg = "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>";
        MvcResult r = put(t, "Co", icon("image/svg+xml", svg.getBytes(StandardCharsets.UTF_8)));
        assertThat(status(r)).isEqualTo(422);
        assertThat(fieldError(r, "icon")).contains("PNG, JPEG or WEBP");
        assertThat(status(put(t, "Co", icon("image/png", svg.getBytes(StandardCharsets.UTF_8))))).isEqualTo(422); // SVG posing as PNG
        assertThat(status(put(t, "Co", icon("image/png", "GIF89a....".getBytes(StandardCharsets.UTF_8))))).isEqualTo(422);
        assertThat(status(put(t, "Co", icon("image/png", new byte[] {1, 2, 3})))).isEqualTo(422);
        assertThat(status(put(t, "Co", icon("image/jpeg", png)))).isEqualTo(422); // declared type disagrees with magic bytes
        assertThat(status(put(t, "Co", icon("application/octet-stream", png)))).isEqualTo(422);
        byte[] big = new byte[512 * 1024 + 1];
        System.arraycopy(png, 0, big, 0, 8);
        r = put(t, "Co", icon("image/png", big));
        assertThat(status(r)).isEqualTo(422);
        assertThat(fieldError(r, "icon")).contains("512 KB");
        assertThat(tree(mvc.perform(get(URL)).andReturn()).get("hasIcon").asBoolean()).isFalse();
        assertThat(tree(mvc.perform(get(URL)).andReturn()).get("name").asText()).isEqualTo("MaterialBook"); // rejected PUT changed nothing
    }

    @Test
    void iconRoundTripHeadersAndFormats() throws Exception {
        String t = superToken();
        byte[] png = image("png", 24);
        MvcResult r = put(t, "Icon Co", icon("image/png", png));
        assertThat(status(r)).isEqualTo(200);
        assertThat(tree(r).get("hasIcon").asBoolean()).isTrue();
        long v1 = tree(r).get("iconVersion").asLong();
        assertThat(v1).isPositive();

        MvcResult icon = mvc.perform(get(URL + "/icon")).andReturn();
        assertThat(status(icon)).isEqualTo(200);
        assertThat(icon.getResponse().getContentAsByteArray()).isEqualTo(png);
        assertThat(icon.getResponse().getContentType()).isEqualTo("image/png");
        assertThat(icon.getResponse().getHeader("Cache-Control")).contains("public").contains("max-age=3600");
        assertThat(icon.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(icon.getResponse().getHeader("Content-Security-Policy")).isEqualTo("default-src 'none'; sandbox");

        byte[] jpg = image("jpg", 24);
        r = put(t, "Icon Co", icon("image/jpeg", jpg));
        assertThat(status(r)).isEqualTo(200);
        assertThat(tree(r).get("iconVersion").asLong()).isGreaterThan(v1);
        assertThat(mvc.perform(get(URL + "/icon")).andReturn().getResponse().getContentAsByteArray()).isEqualTo(jpg);

        // a PUT with only a name keeps the icon
        assertThat(tree(put(t, "Renamed Co", null)).get("hasIcon").asBoolean()).isTrue();

        // minimal WEBP container header is accepted by magic bytes
        byte[] webp = "RIFF\u0010\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(status(put(t, "Renamed Co", icon("image/webp", webp)))).isEqualTo(200);
        assertThat(mvc.perform(get(URL + "/icon")).andReturn().getResponse().getContentType()).isEqualTo("image/webp");
    }

    @Test
    void deleteIconAndAuditHasNoBytes() throws Exception {
        String t = superToken();
        long since = jdbc.queryForObject("select coalesce(max(id), 0) from audit_logs", Long.class); // audit rows are append-only and shared
        byte[] png = image("png", 32);
        assertThat(status(put(t, "Audited Co", icon("image/png", png)))).isEqualTo(200);
        assertThat(status(call(HttpMethod.DELETE, URL + "/icon", t, null))).isEqualTo(204);
        assertThat(status(mvc.perform(get(URL + "/icon")).andReturn())).isEqualTo(404);
        assertThat(tree(mvc.perform(get(URL)).andReturn()).get("hasIcon").asBoolean()).isFalse();
        assertThat(status(call(HttpMethod.DELETE, URL + "/icon", t, null))).isEqualTo(204); // idempotent

        List<String> rows = jdbc.queryForList("select coalesce(old_values::text,'') || ' ' || coalesce(new_values::text,'') from audit_logs"
                + " where module = 'SETTINGS' and id > ? order by id", String.class, since);
        assertThat(rows).hasSize(2); // update + one removal (the second delete found nothing)
        String all = String.join("\n", rows);
        assertThat(all).contains("Audited Co").contains("iconBytes").contains(String.valueOf(png.length));
        assertThat(all).doesNotContain(java.util.Base64.getEncoder().encodeToString(png).substring(0, 40)).doesNotContain("\"data\"");
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where module = 'SETTINGS' and id > ? and user_id is not null", Long.class, since)).isEqualTo(2);
    }

    @Test
    void exportedPdfHeaderUsesTheConfiguredName() throws Exception {
        String t = superToken();
        assertThat(status(put(t, "Zenith Metals LLP", null))).isEqualTo(200);
        MvcResult pdf = call(HttpMethod.GET, "/api/v1/reports/expenses/export?format=pdf", t, null);
        assertThat(status(pdf)).isEqualTo(200);
        try (PDDocument doc = Loader.loadPDF(pdf.getResponse().getContentAsByteArray())) {
            assertThat(new PDFTextStripper().getText(doc)).contains("Zenith Metals LLP");
        }
    }
}
