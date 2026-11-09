package com.materialbook.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.materialbook.common.ApiException.InsufficientStockException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StockConcurrencyTest extends AbstractStockTest {

    @Test
    void parallelOutsNeverOversell() throws Exception {
        long product = newProduct("0");
        postNew(MovementType.IN, product, "100");

        int threads = 30;
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> jobs = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            jobs.add(() -> {
                start.await();
                try {
                    postNew(MovementType.OUT, product, "10");
                    return true;
                } catch (InsufficientStockException e) {
                    assertThat(e.getFieldErrors()).hasSize(1);
                    return false;
                }
            });
        }
        List<Boolean> results = runConcurrently(jobs, start);

        assertThat(results.stream().filter(r -> r)).hasSize(10);
        assertThat(results.stream().filter(r -> !r)).hasSize(20);
        assertQty(balance(product), "0");
        assertQty(sumDelta(product), "0");
    }

    @Test
    void oppositeLockOrderDoesNotDeadlock() throws Exception {
        long a = newProduct("0");
        long b = newProduct("0");
        postNew(MovementType.IN, a, "1000", b, "1000");

        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> jobs = new ArrayList<>();
        for (boolean forward : new boolean[] {true, false}) {
            jobs.add(() -> {
                start.await();
                for (int i = 0; i < 50; i++) {
                    if (forward) {
                        postNew(MovementType.OUT, a, "1", b, "1");
                    } else {
                        postNew(MovementType.OUT, b, "1", a, "1");
                    }
                }
                return true;
            });
        }
        runConcurrently(jobs, start);

        assertQty(balance(a), "900");
        assertQty(balance(b), "900");
        assertQty(sumDelta(a), "900");
        assertQty(sumDelta(b), "900");
    }

    private static <T> List<T> runConcurrently(List<Callable<T>> jobs, CountDownLatch start) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(jobs.size());
        try {
            List<Future<T>> futures = jobs.stream().map(pool::submit).toList();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
