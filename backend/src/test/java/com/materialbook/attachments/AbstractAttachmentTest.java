package com.materialbook.attachments;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.materialbook.products.AbstractCatalogApiTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/** Files go to a temp directory (one per JVM; the Spring context is shared, so tests compare counts before/after). */
abstract class AbstractAttachmentTest extends AbstractCatalogApiTest {
    private static final AtomicInteger SEQ = new AtomicInteger();
    static final Path STORAGE_DIR;

    static {
        try {
            STORAGE_DIR = Files.createTempDirectory("mb-uploads-");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteTree(STORAGE_DIR)));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.storage.dir", STORAGE_DIR::toString);
    }

    private static void deleteTree(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // best effort
        }
    }

    protected MvcResult upload(String token, String ownerType, long ownerId, String name, String contentType, byte[] content) throws Exception {
        var b = multipart("/api/v1/attachments")
                .file(new MockMultipartFile("file", name, contentType, content))
                .param("ownerType", ownerType).param("ownerId", String.valueOf(ownerId));
        return mvc.perform(b.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn();
    }

    protected boolean objectExists(String key) {
        return Files.isRegularFile(STORAGE_DIR.resolve(key));
    }

    protected String permissions(String key) throws IOException {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(STORAGE_DIR.resolve(key)));
    }

    /** Stored files, temp files included (so a leaked temp file would show up as an extra file). */
    protected long countFiles() {
        try (Stream<Path> walk = Files.walk(STORAGE_DIR)) {
            return walk.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected long newMaterialTransaction(String type) {
        int n = SEQ.incrementAndGet();
        Long creator = jdbc.queryForObject("select id from users where mobile = '9000000001'", Long.class);
        Long party = "IN".equals(type) ? jdbc.queryForObject(
                "insert into parties(party_code, party_name, party_type) values (?, 'Supplier', 'SUPPLIER') returning id", Long.class, "ATT-P" + n) : null;
        return jdbc.queryForObject("""
                insert into material_transactions(transaction_number, transaction_type, transaction_date, party_id, created_by)
                values (?, ?, current_date, ?, ?) returning id
                """, Long.class, "ATT-TX-" + n, type, party, creator);
    }

    protected long newExpense() {
        Long creator = jdbc.queryForObject("select id from users where mobile = '9000000001'", Long.class);
        return jdbc.queryForObject("""
                insert into expenses(expense_number, expense_date, category_id, amount, created_by)
                values (?, current_date, (select min(id) from expense_categories), 100, ?) returning id
                """, Long.class, "ATT-EXP-" + SEQ.incrementAndGet(), creator);
    }

    protected long newPartyPayment() {
        int n = SEQ.incrementAndGet();
        Long creator = jdbc.queryForObject("select id from users where mobile = '9000000001'", Long.class);
        Long party = jdbc.queryForObject(
                "insert into parties(party_code, party_name, party_type) values (?, 'Payee', 'SUPPLIER') returning id", Long.class, "ATT-PP" + n);
        return jdbc.queryForObject("""
                insert into party_payments(payment_number, party_id, payment_date, direction, amount, created_by)
                values (?, ?, current_date, 'PAID', 100, ?) returning id
                """, Long.class, "ATT-PAY-" + n, party, creator);
    }

    protected void cancel(String table, long id) {
        Long creator = jdbc.queryForObject("select id from users where mobile = '9000000001'", Long.class);
        jdbc.update("update " + table + " set status = 'CANCELLED', cancelled_by = ?, cancelled_at = now(), cancellation_reason = 'test' where id = ?", creator, id);
    }
}
