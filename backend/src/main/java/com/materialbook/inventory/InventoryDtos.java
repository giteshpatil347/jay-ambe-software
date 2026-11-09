package com.materialbook.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class InventoryDtos {
    private InventoryDtos() {}

    /** lowStock = minimumStock > 0 and quantity <= minimumStock. */
    public record StockRow(Long productId, String code, String name, String categoryName, String unit,
                           BigDecimal quantity, BigDecimal minimumStock, boolean lowStock) {}

    public record LedgerRow(LocalDate date, MovementType movementType, BigDecimal delta, BigDecimal balance,
                            Long transactionId, String transactionNumber, String createdBy) {}

    /**
     * One page of a product ledger. {@code openingBalance} is the stock before {@code from} (0 when {@code from} is
     * absent); row {@code balance} values are absolute running balances in (movement_date, id) order.
     */
    public record LedgerResponse(BigDecimal openingBalance, List<LedgerRow> content, int page, int size, long totalElements) {}

    public record OpeningItem(
            @NotNull Long productId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 3) BigDecimal quantity) {}

    /** {@code date} defaults to today. */
    public record OpeningStockRequest(LocalDate date, @NotEmpty @Valid List<OpeningItem> items) {}
}
