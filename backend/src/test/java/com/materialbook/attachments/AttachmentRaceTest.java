package com.materialbook.attachments;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** The per-owner attachment cap must hold under concurrent uploads (count + insert are serialised on the owner row). */
class AttachmentRaceTest extends AbstractAttachmentTest {

    @Test
    void parallelUploadsCannotExceedTheCapAndLeaveNoOrphanFiles() throws Exception {
        long expense = newExpense();
        String admin = superToken();
        for (int i = 0; i < AttachmentService.MAX_PER_OWNER - 1; i++) {
            jdbc.update("insert into attachments(expense_id, file_name, file_key, content_type, file_size, sha256) values (?, 'seed.png', ?, 'image/png', 1, repeat('0', 64))",
                    expense, "seed/race-" + expense + "-" + i);
        }
        long objectsBefore = countFiles();

        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int n = i;
            results.add(pool.submit(() -> {
                start.await();
                return status(upload(admin, "EXPENSE", expense, "r" + n + ".png", "image/png", FileValidatorTest.png()));
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) {
            statuses.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(statuses.stream().filter(s -> s == 201)).hasSize(1);
        assertThat(statuses.stream().filter(s -> s == 409)).hasSize(threads - 1);
        assertThat(jdbc.queryForObject("select count(*) from attachments where expense_id = ?", Integer.class, expense))
                .isEqualTo(AttachmentService.MAX_PER_OWNER);
        assertThat(countFiles()).isEqualTo(objectsBefore + 1);
    }
}
