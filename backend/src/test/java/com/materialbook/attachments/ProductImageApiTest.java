package com.materialbook.attachments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

class ProductImageApiTest extends AbstractAttachmentTest {
    private long newProduct() {
        return jdbc.queryForObject("insert into products(product_code, product_name, unit) values (?, 'Img product', 'KG') returning id",
                Long.class, "IMG-" + System.nanoTime());
    }

    private MvcResult putImage(String token, long productId, String name, String contentType, byte[] content) throws Exception {
        return mvc.perform(multipart(HttpMethod.PUT, "/api/v1/products/" + productId + "/image")
                .file(new MockMultipartFile("file", name, contentType, content))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn();
    }

    @Test
    void setsImageReplacesAndCleansUpOldFileAndServesBytesInline() throws Exception {
        long product = newProduct();
        String admin = superToken();
        assertThat(tree(call(HttpMethod.GET, "/api/v1/products/" + product, admin, null)).get("imageKey").isNull()).isTrue();
        assertThat(status(call(HttpMethod.GET, "/api/v1/products/" + product + "/image", admin, null))).isEqualTo(404);

        MvcResult first = putImage(admin, product, "p.png", "image/png", FileValidatorTest.png());
        assertThat(status(first)).isEqualTo(200);
        String key1 = tree(first).get("imageKey").asText();
        assertThat(key1).matches("\\d{4}/\\d{2}/[0-9a-f-]{36}");
        assertThat(objectExists(key1)).isTrue();
        assertThat(tree(call(HttpMethod.GET, "/api/v1/products/" + product, admin, null)).get("imageKey").asText()).isEqualTo(key1);

        String key2 = tree(putImage(admin, product, "p.webp", "image/webp", FileValidatorTest.webp())).get("imageKey").asText();
        assertThat(key2).isNotEqualTo(key1);
        assertThat(objectExists(key1)).isFalse();
        assertThat(objectExists(key2)).isTrue();
        assertThat(auditCount("PRODUCT_IMAGE_SET", product)).isEqualTo(2);

        var res = call(HttpMethod.GET, "/api/v1/products/" + product + "/image", admin, null).getResponse();
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentAsByteArray()).isEqualTo(FileValidatorTest.webp());
        assertThat(res.getHeader(HttpHeaders.CONTENT_TYPE)).isEqualTo("image/webp");
        assertThat(res.getHeader(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("inline");
        assertThat(res.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(res.getHeader("Content-Security-Policy")).isEqualTo("default-src 'none'; sandbox");
        assertThat(res.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("private, max-age=300");
    }

    @Test
    void rejectsPdfsSpoofsAndFilesOverFiveMegabytes() throws Exception {
        long product = newProduct();
        String admin = superToken();
        assertThat(status(putImage(admin, product, "p.pdf", "application/pdf", FileValidatorTest.pdf()))).isEqualTo(422);
        assertThat(status(putImage(admin, product, "p.png", "image/jpeg", FileValidatorTest.png()))).isEqualTo(422);
        assertThat(status(putImage(admin, product, "p.png", "image/png", Arrays.copyOf(FileValidatorTest.png(), 5 * 1024 * 1024 + 1)))).isEqualTo(422);
        assertThat(jdbc.queryForObject("select image_key from products where id = ?", String.class, product)).isNull();
    }

    @Test
    void requiresProductUpdateToSetAndViewToRead() throws Exception {
        long product = newProduct();
        String viewer = tokenFor("VIEWER");
        assertThat(status(putImage(viewer, product, "p.png", "image/png", FileValidatorTest.png()))).isEqualTo(403);
        assertThat(status(putImage(superToken(), 999_999_999L, "p.png", "image/png", FileValidatorTest.png()))).isEqualTo(404);
        putImage(superToken(), product, "p.png", "image/png", FileValidatorTest.png());
        assertThat(status(call(HttpMethod.GET, "/api/v1/products/" + product + "/image", viewer, null))).isEqualTo(200);
        assertThat(status(mvc.perform(get("/api/v1/products/" + product + "/image")).andReturn())).isEqualTo(401);
        assertThat(status(call(HttpMethod.GET, "/api/v1/products/999999999/image", viewer, null))).isEqualTo(404);
        assertThat(status(call(HttpMethod.GET, "/api/v1/products/" + product + "/image", tokenFor("PURCHASE_STAFF"), null))).isEqualTo(200);
    }
}
