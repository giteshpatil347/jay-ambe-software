package com.materialbook.reports;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** {@code asOf} (inclusive) rebuilds quantities from stock_movements; without it the live balance is used. */
public record StockFilter(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
        Long categoryId, Long productId, Boolean lowStockOnly) {
    boolean lowOnly() {
        return Boolean.TRUE.equals(lowStockOnly);
    }
}
