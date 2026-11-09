package com.materialbook.attachments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

class AttachmentApiTest extends AbstractAttachmentTest {
    private JsonNode uploadOk(String token, String ownerType, long ownerId, String name, String type, byte[] content) throws Exception {
        MvcResult r = upload(token, ownerType, ownerId, name, type, content);
        assertThat(status(r)).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return tree(r);
    }

    private String fileKey(long attachmentId) {
        return jdbc.queryForObject("select file_key from attachments where id = ?", String.class, attachmentId);
    }

    @Test
    void uploadStoresEncryptedObjectUnderGeneratedKeyAndRecordsDetectedType() throws Exception {
        long tx = newMaterialTransaction("IN");
        byte[] pdf = FileValidatorTest.pdf();
        JsonNode a = uploadOk(tokenFor("PURCHASE_STAFF"), "MATERIAL_TRANSACTION", tx, "../../Bill 1.pdf", "application/pdf", pdf);

        assertThat(a.get("fileName").asText()).isEqualTo("Bill 1.pdf");
        assertThat(a.get("contentType").asText()).isEqualTo("application/pdf");
        assertThat(a.get("fileSize").asLong()).isEqualTo(pdf.length);
        assertThat(a.get("ownerType").asText()).isEqualTo("MATERIAL_TRANSACTION");
        assertThat(a.get("ownerId").asLong()).isEqualTo(tx);
        assertThat(a.get("sha256").asText()).hasSize(64);
        assertThat(a.has("fileKey")).isFalse();

        String key = fileKey(a.get("id").asLong());
        assertThat(key).matches("\\d{4}/\\d{2}/[0-9a-f-]{36}").doesNotContain("Bill");
        assertThat(objectExists(key)).isTrue();
        assertThat(permissions(key)).isEqualTo("rw-------");
        assertThat(auditCount("ATTACHMENT_UPLOAD", a.get("id").asLong())).isEqualTo(1);
    }

    @Test
    void rejectsSpoofedOversizedAndUnsupportedUploadsWith422AndStoresNothing() throws Exception {
        long tx = newMaterialTransaction("IN");
        String token = superToken();
        assertThat(status(upload(token, "MATERIAL_TRANSACTION", tx, "a.pdf", "application/pdf", "#!/bin/sh".getBytes(StandardCharsets.US_ASCII)))).isEqualTo(422);
        assertThat(status(upload(token, "MATERIAL_TRANSACTION", tx, "a.jpg", "image/png", FileValidatorTest.png()))).isEqualTo(422);
        assertThat(status(upload(token, "MATERIAL_TRANSACTION", tx, "a.png", "application/pdf", FileValidatorTest.png()))).isEqualTo(422);
        assertThat(status(upload(token, "MATERIAL_TRANSACTION", tx, "a.png", "image/png", new byte[10 * 1024 * 1024 + 1]))).isEqualTo(422);
        byte[] huge = new byte[11 * 1024 * 1024];
        System.arraycopy(FileValidatorTest.png(), 0, huge, 0, 8);
        assertThat(status(upload(token, "MATERIAL_TRANSACTION", tx, "a.png", "image/png", huge))).isEqualTo(422);
        assertThat(jdbc.queryForObject("select count(*) from attachments where transaction_id = ?", Integer.class, tx)).isZero();
    }

    @Test
    void permissionsAreDerivedFromTheOwnerModule() throws Exception {
        long in = newMaterialTransaction("IN");
        long out = newMaterialTransaction("OUT");
        long expense = newExpense();
        long payment = newPartyPayment();
        byte[] png = FileValidatorTest.png();
        String purchase = tokenFor("PURCHASE_STAFF");
        String dispatch = tokenFor("DISPATCH_STAFF");
        String accountant = tokenFor("ACCOUNTANT");
        String viewer = tokenFor("VIEWER");

        assertThat(status(upload(purchase, "MATERIAL_TRANSACTION", in, "a.png", "image/png", png))).isEqualTo(201);
        assertThat(status(upload(purchase, "MATERIAL_TRANSACTION", out, "a.png", "image/png", png))).isEqualTo(403);
        assertThat(status(upload(dispatch, "MATERIAL_TRANSACTION", out, "a.png", "image/png", png))).isEqualTo(201);
        assertThat(status(upload(dispatch, "MATERIAL_TRANSACTION", in, "a.png", "image/png", png))).isEqualTo(403);
        assertThat(status(upload(purchase, "EXPENSE", expense, "a.png", "image/png", png))).isEqualTo(403);
        assertThat(status(upload(purchase, "PARTY_PAYMENT", payment, "a.png", "image/png", png))).isEqualTo(403);
        assertThat(status(upload(accountant, "EXPENSE", expense, "a.png", "image/png", png))).isEqualTo(201);
        assertThat(status(upload(accountant, "PARTY_PAYMENT", payment, "a.png", "image/png", png))).isEqualTo(201);
        assertThat(status(upload(viewer, "EXPENSE", expense, "a.png", "image/png", png))).isEqualTo(403);

        String list = "/api/v1/attachments?ownerType=MATERIAL_TRANSACTION&ownerId=";
        assertThat(status(call(HttpMethod.GET, list + out, viewer, null))).isEqualTo(200);
        assertThat(status(call(HttpMethod.GET, list + out, purchase, null))).isEqualTo(403);
        assertThat(status(call(HttpMethod.GET, list + in, purchase, null))).isEqualTo(200);
        MvcResult mine = call(HttpMethod.GET, list + in, purchase, null);
        assertThat(tree(mine)).hasSize(1);
    }

    @Test
    void contentAndDeleteRequirePermissionsOnOwner() throws Exception {
        long in = newMaterialTransaction("IN");
        long id = uploadOk(tokenFor("PURCHASE_STAFF"), "MATERIAL_TRANSACTION", in, "a.png", "image/png", FileValidatorTest.png()).get("id").asLong();
        String dispatch = tokenFor("DISPATCH_STAFF");
        String viewer = tokenFor("VIEWER");

        assertThat(status(call(HttpMethod.GET, "/api/v1/attachments/" + id + "/content", dispatch, null))).isEqualTo(403);
        assertThat(status(call(HttpMethod.GET, "/api/v1/attachments/" + id + "/content", viewer, null))).isEqualTo(200);
        assertThat(status(call(HttpMethod.DELETE, "/api/v1/attachments/" + id, viewer, null))).isEqualTo(403);
        // PURCHASE_STAFF has CREATE but not UPDATE on MATERIAL_IN: cannot delete.
        assertThat(status(call(HttpMethod.DELETE, "/api/v1/attachments/" + id, tokenFor("PURCHASE_STAFF"), null))).isEqualTo(403);
        assertThat(status(mvc.perform(get("/api/v1/attachments/" + id + "/content")).andReturn())).isEqualTo(401);
    }

    @Test
    void cancelledOwnerRejectsUploadAndDeleteWith409ButStillAllowsView() throws Exception {
        long expense = newExpense();
        String admin = superToken();
        long id = uploadOk(admin, "EXPENSE", expense, "a.png", "image/png", FileValidatorTest.png()).get("id").asLong();
        cancel("expenses", expense);

        assertThat(status(upload(admin, "EXPENSE", expense, "b.png", "image/png", FileValidatorTest.png()))).isEqualTo(409);
        assertThat(status(call(HttpMethod.DELETE, "/api/v1/attachments/" + id, admin, null))).isEqualTo(409);
        assertThat(status(call(HttpMethod.GET, "/api/v1/attachments?ownerType=EXPENSE&ownerId=" + expense, admin, null))).isEqualTo(200);
        assertThat(status(call(HttpMethod.GET, "/api/v1/attachments/" + id + "/content", admin, null))).isEqualTo(200);
    }

    @Test
    void unknownOwnerIs404AndMissingOwnerIdIs4xx() throws Exception {
        String admin = superToken();
        assertThat(status(upload(admin, "EXPENSE", 999_999_999L, "a.png", "image/png", FileValidatorTest.png()))).isEqualTo(404);
        assertThat(status(call(HttpMethod.GET, "/api/v1/attachments?ownerType=PARTY_PAYMENT&ownerId=999999999", admin, null))).isEqualTo(404);
        assertThat(status(call(HttpMethod.GET, "/api/v1/attachments/999999999/content", admin, null))).isEqualTo(404);
        assertThat(status(call(HttpMethod.DELETE, "/api/v1/attachments/999999999", admin, null))).isEqualTo(404);
        assertThat(status(call(HttpMethod.GET, "/api/v1/attachments?ownerType=NOPE&ownerId=1", admin, null))).isEqualTo(400);
    }

    @Test
    void allowsAtMostTenAttachmentsPerOwner() throws Exception {
        long expense = newExpense();
        String admin = superToken();
        for (int i = 0; i < 10; i++) {
            uploadOk(admin, "EXPENSE", expense, "f" + i + ".png", "image/png", FileValidatorTest.png());
        }
        assertThat(status(upload(admin, "EXPENSE", expense, "f11.png", "image/png", FileValidatorTest.png()))).isEqualTo(409);
        assertThat(tree(call(HttpMethod.GET, "/api/v1/attachments?ownerType=EXPENSE&ownerId=" + expense, admin, null))).hasSize(10);
    }

    @Test
    void contentStreamsExactBytesWithSafeHeadersForImages() throws Exception {
        long out = newMaterialTransaction("OUT");
        byte[] png = FileValidatorTest.png();
        long id = uploadOk(tokenFor("DISPATCH_STAFF"), "MATERIAL_TRANSACTION", out, "site photo.png", "image/png", png).get("id").asLong();

        MvcResult r = call(HttpMethod.GET, "/api/v1/attachments/" + id + "/content", tokenFor("VIEWER"), null);
        var res = r.getResponse();
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentAsByteArray()).isEqualTo(png);
        assertThat(res.getHeader(HttpHeaders.CONTENT_TYPE)).isEqualTo("image/png");
        assertThat(res.getHeader(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("inline");
        assertThat(res.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(res.getHeader("Content-Security-Policy")).isEqualTo("default-src 'none'; sandbox");
        assertThat(res.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("private, max-age=300");
        assertThat(res.getHeader(HttpHeaders.CONTENT_LENGTH)).isEqualTo(String.valueOf(png.length));
    }

    @Test
    void contentServesPdfAsAttachmentWithSanitisedName() throws Exception {
        long payment = newPartyPayment();
        String admin = superToken();
        byte[] pdf = FileValidatorTest.pdf();
        long id = uploadOk(admin, "PARTY_PAYMENT", payment, "Receipt ₹500.pdf", "application/pdf", pdf).get("id").asLong();

        var res = call(HttpMethod.GET, "/api/v1/attachments/" + id + "/content", admin, null).getResponse();
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentAsByteArray()).isEqualTo(pdf);
        assertThat(res.getHeader(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/pdf");
        assertThat(res.getHeader(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("attachment; filename*=UTF-8''Receipt%20%E2%82%B9500.pdf");
        assertThat(res.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void contentRequiresViewOnOwnerAndAuthentication() throws Exception {
        long out = newMaterialTransaction("OUT");
        long id = uploadOk(tokenFor("DISPATCH_STAFF"), "MATERIAL_TRANSACTION", out, "a.png", "image/png", FileValidatorTest.png()).get("id").asLong();
        String url = "/api/v1/attachments/" + id + "/content";

        assertThat(status(call(HttpMethod.GET, url, tokenFor("PURCHASE_STAFF"), null))).isEqualTo(403);
        assertThat(status(call(HttpMethod.GET, url, tokenFor("DISPATCH_STAFF"), null))).isEqualTo(200);
        assertThat(status(mvc.perform(get(url)).andReturn())).isEqualTo(401);
        assertThat(status(call(HttpMethod.GET, "/api/v1/attachments/999999999/content", superToken(), null))).isEqualTo(404);
    }

    @Test
    void deleteRemovesRowThenObjectAndAudits() throws Exception {
        long tx = newMaterialTransaction("OUT");
        String admin = superToken();
        long id = uploadOk(admin, "MATERIAL_TRANSACTION", tx, "a.jpg", "image/jpeg", FileValidatorTest.jpeg()).get("id").asLong();
        String key = fileKey(id);
        assertThat(objectExists(key)).isTrue();

        assertThat(status(call(HttpMethod.DELETE, "/api/v1/attachments/" + id, admin, null))).isEqualTo(204);

        assertThat(jdbc.queryForObject("select count(*) from attachments where id = ?", Integer.class, id)).isZero();
        assertThat(objectExists(key)).isFalse();
        assertThat(auditCount("ATTACHMENT_DELETE", id)).isEqualTo(1);
        assertThat(status(call(HttpMethod.DELETE, "/api/v1/attachments/" + id, admin, null))).isEqualTo(404);
    }

    @Test
    void failedDatabaseInsertRemovesTheUploadedObject() throws Exception {
        long expense = newExpense();
        String admin = superToken();
        long before = countObjects();
        // A temporary CHECK constraint makes the INSERT fail after the object has been stored.
        jdbc.update("alter table attachments add constraint ck_test_fail check (file_name <> 'fail.png') not valid");
        try {
            assertThat(status(upload(admin, "EXPENSE", expense, "fail.png", "image/png", FileValidatorTest.png()))).isEqualTo(500);
        } finally {
            jdbc.update("alter table attachments drop constraint ck_test_fail");
        }
        assertThat(countObjects()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from attachments where expense_id = ?", Integer.class, expense)).isZero();
    }

    private long countObjects() {
        return countFiles();
    }
}
