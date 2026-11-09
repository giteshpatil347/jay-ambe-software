package com.materialbook.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.materialbook.common.ApiException.InsufficientStockException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class StockInvariantTest extends AbstractStockTest {

    @Test
    void balanceEqualsSumOfDeltasAfterRandomOperations() {
        long[] products = {newProduct("0"), newProduct("0"), newProduct("0")};
        Random random = new Random(42);
        List<Long> committed = new ArrayList<>();
        int rejected = 0;

        for (int i = 0; i < 300; i++) {
            try {
                int op = random.nextInt(10);
                if (op < 4) {
                    committed.add(postNew(MovementType.IN, products[random.nextInt(3)], qty(random), products[random.nextInt(3)], qty(random)));
                } else if (op < 7) {
                    committed.add(postNew(MovementType.OUT, products[random.nextInt(3)], qty(random), products[random.nextInt(3)], qty(random)));
                } else if (!committed.isEmpty()) {
                    actAsAdmin();
                    tx.executeWithoutResult(s -> stock.reverse(committed.get(random.nextInt(committed.size())), LocalDate.now()));
                }
            } catch (InsufficientStockException e) {
                rejected++;
            }
            for (long p : products) {
                assertThat(balance(p)).isEqualByComparingTo(sumDelta(p)).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            }
        }
        assertThat(rejected).as("scenario must exercise the rejection path").isPositive();
        assertThat(jdbc.queryForObject("select count(*) from stock_movements where movement_type like 'REVERSAL%'", Integer.class)).isPositive();
    }

    private static String qty(Random random) {
        return (1 + random.nextInt(40)) + ".5";
    }
}
