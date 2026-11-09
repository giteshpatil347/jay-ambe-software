package com.materialbook.material;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class MaterialDtos {
    private MaterialDtos() {}

    /**
     * {@code rate} defaults to 0 and is ALWAYS per product unit. {@code unit} (TON|KG|PIECE) is the unit the quantity was
     * typed in; null = the product's own unit. The server converts to the product unit before storing.
     */
    public record ItemInput(
            @NotNull Long productId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            @Size(max = 10) String unit,
            @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal rate,
            @Size(max = 2000) String remarks) {}

    /**
     * Create and update body. IN requires an active SUPPLIER/BOTH party; for OUT the party is optional but, when given,
     * must be an active CLIENT/BOTH. {@code version} is required on update (optimistic lock) and ignored on create.
     */
    public record TransactionRequest(
            @NotNull LocalDate transactionDate,
            Long partyId,
            @Size(max = 100) String challanNumber,
            @Size(max = 50) String vehicleNumber,
            @Size(max = 150) String driverName,
            @Size(max = 2000) String remarks,
            @NotEmpty @Size(max = 100) @Valid List<ItemInput> items,
            Long version) {}

    public record CancelRequest(@NotBlank @Size(max = 1000) String reason) {}

    /** Only non-superseded items are ever returned. {@code amount} is computed by the database (quantity x rate). */
    public record ItemResponse(Long id, Long productId, String productCode, String productName, String unit,
                               BigDecimal quantity, BigDecimal rate, BigDecimal amount, String remarks) {}

    public record TransactionDetail(Long id, String transactionNumber, String transactionType, LocalDate transactionDate,
                                    Long partyId, String partyName, String challanNumber, String vehicleNumber,
                                    String driverName, String remarks, BigDecimal totalAmount, String status, Long version,
                                    String createdByName, Instant createdAt, Instant updatedAt,
                                    String cancelledByName, Instant cancelledAt, String cancellationReason,
                                    List<ItemResponse> items) {}

    /** Quantity of one unit (in the products' own units; units are never added together). */
    public record QuantityByUnit(String unit, BigDecimal quantity) {}

    /**
     * {@code productNames} holds the first two item product names; {@code itemCount} is the full count;
     * {@code quantities} is the total quantity of the entry per unit (e.g. 12.5 TON + 40 KG).
     */
    public record TransactionRow(Long id, String transactionNumber, LocalDate transactionDate, Long partyId, String partyName,
                                 int itemCount, List<String> productNames, List<QuantityByUnit> quantities, BigDecimal totalAmount,
                                 String status, String createdByName) {}

    /** {@code totalAmount} sums the POSTED rows matching the filters (all pages). */
    public record TransactionPage(List<TransactionRow> content, int page, int size, long totalElements, BigDecimal totalAmount) {}
}
