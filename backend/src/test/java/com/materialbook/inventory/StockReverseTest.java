package com.materialbook.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.materialbook.common.ApiException.InsufficientStockException;
import com.materialbook.common.ApiException.ValidationException;
import com.materialbook.inventory.StockService.Line;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StockReverseTest extends AbstractStockTest {

    @Test
    void reversingOutRestoresStockKeepsOriginalsAndIsIdempotent() {
        long p = newProduct("0");
        postNew(MovementType.IN, p, "50");
        long out = postNew(MovementType.OUT, p, "20");
        assertQty(balance(p), "30");

        reverse(out);
        assertQty(balance(p), "50");
        assertThat(jdbc.queryForObject("select quantity_delta from stock_movements where transaction_id = ? and movement_type = 'OUT'",
                BigDecimal.class, out)).isEqualByComparingTo("-20");
        assertThat(jdbc.queryForObject("select quantity_delta from stock_movements where transaction_id = ? and movement_type = 'REVERSAL_IN'",
                BigDecimal.class, out)).isEqualByComparingTo("20");

        reverse(out);
        assertQty(balance(p), "50");
        assertThat(movementCount(p)).isEqualTo(3);
    }

    @Test
    void reversingInSubtractsStockWithReversalOut() {
        long p = newProduct("0");
        long in = postNew(MovementType.IN, p, "50");
        reverse(in);
        assertQty(balance(p), "0");
        assertThat(jdbc.queryForObject("select quantity_delta from stock_movements where movement_type = 'REVERSAL_OUT' and product_id = ?",
                BigDecimal.class, p)).isEqualByComparingTo("-50");
    }

    @Test
    void reversingInWithInsufficientStockIsBlockedAndChangesNothing() {
        long p = newProduct("0");
        long in = postNew(MovementType.IN, p, "50");
        postNew(MovementType.OUT, p, "30");

        assertThatThrownBy(() -> reverse(in)).isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("available 20.000").hasMessageContaining("required 50.000");
        assertQty(balance(p), "20");
        assertThat(movementCount(p)).isEqualTo(2);
    }

    @Test
    void insufficientOutReportsEveryShortProductAndWritesNothing() {
        long a = newProduct("0");
        long b = newProduct("0");
        postNew(MovementType.IN, a, "5", b, "100");

        assertThatThrownBy(() -> postNew(MovementType.OUT, a, "6", b, "10"))
                .isInstanceOfSatisfying(InsufficientStockException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("INSUFFICIENT_STOCK");
                    assertThat(e.getFieldErrors()).hasSize(1);
                    assertThat(e.getFieldErrors().get(0).message()).contains("available 5.000").contains("required 6.000");
                });
        assertQty(balance(a), "5");
        assertQty(balance(b), "100");
    }

    @Test
    void duplicateProductLinesAreAggregatedForTheCheck() {
        long p = newProduct("0");
        postNew(MovementType.IN, p, "15");
        assertThatThrownBy(() -> postNew(MovementType.OUT, p, "10", p, "10")).isInstanceOf(InsufficientStockException.class);
        postNew(MovementType.IN, p, "5");
        postNew(MovementType.OUT, p, "10", p, "10");
        assertQty(balance(p), "0");
    }

    @Test
    void editIsReverseThenPostInOneTransaction() {
        long p = newProduct("0");
        long txId = postNew(MovementType.IN, p, "10");
        actAsAdmin();
        tx.executeWithoutResult(s -> {
            stock.reverse(txId, LocalDate.now());
            long item = newItem(txId, p, "15");
            stock.post(MovementType.IN, txId, LocalDate.now(), List.of(new Line(p, item, new BigDecimal("15"))));
        });
        assertQty(balance(p), "15");
        assertQty(sumDelta(p), "15");
        reverse(txId);
        assertQty(balance(p), "0");
    }

    @Test
    void rejectsNonPositiveQuantityAndUnknownProduct() {
        long p = newProduct("0");
        actAsAdmin();
        assertThatThrownBy(() -> stock.post(MovementType.IN, newTransaction("IN"), LocalDate.now(), List.of(new Line(p, null, BigDecimal.ZERO))))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> stock.post(MovementType.IN, newTransaction("IN"), LocalDate.now(), List.of(new Line(-1L, null, BigDecimal.ONE))))
                .isInstanceOf(ValidationException.class);
    }

    private void reverse(long transactionId) {
        actAsAdmin();
        tx.executeWithoutResult(s -> stock.reverse(transactionId, LocalDate.now()));
    }
}
