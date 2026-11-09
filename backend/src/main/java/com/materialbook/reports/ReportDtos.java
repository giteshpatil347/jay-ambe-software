package com.materialbook.reports;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Money and quantities are serialized as plain strings (see JacksonConfig). Quantities are never summed across units. */
public final class ReportDtos {
    private ReportDtos() {}

    public record ReportPage<T, S>(List<T> content, int page, int size, long totalElements, S summary) {}

    public record UnitQuantity(String unit, BigDecimal quantity) {}

    /** POSTED documents matching the filters only. */
    public record MaterialSummary(long transactionCount, BigDecimal totalAmount, List<UnitQuantity> quantityByUnit) {}

    /** One row per transaction; {@code amount} covers only the items matching the product/category filter. */
    public record MaterialRow(Long id, String transactionNumber, LocalDate transactionDate, Long partyId, String partyName,
                              String challanNumber, String vehicleNumber, String status, int itemCount, BigDecimal amount,
                              List<UnitQuantity> quantities) {}

    /** {@code key} is the party id / product id / ISO day ("" = no party); one {@code quantities} entry per unit. */
    public record GroupRow(String key, String label, long transactionCount, BigDecimal amount, List<UnitQuantity> quantities) {}

    public record ExpenseRow(Long id, String expenseNumber, LocalDate expenseDate, Long categoryId, String categoryName,
                             BigDecimal amount, String paidTo, String paymentMode, String status) {}

    public record CategoryTotal(Long categoryId, String categoryName, long count, BigDecimal amount) {}

    public record ExpenseSummary(long count, BigDecimal totalAmount, List<CategoryTotal> byCategory) {}

    public record StockReportRow(Long productId, String code, String name, String categoryName, String unit,
                                 BigDecimal quantity, BigDecimal minimumStock, boolean lowStock) {}

    public record StockSummary(long productCount, long lowStockCount) {}

    /**
     * Dashboard figures the caller may not see are null and omitted from the JSON.
     * IN/OUT are quantities in TONS (KG products /1000; items counted in pieces are excluded); expenses are rupees.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Period(BigDecimal inTons, BigDecimal outTons, BigDecimal expenses) {}

    /** balanceTons = closing stock (tons) at the end of that day. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DayPoint(LocalDate date, BigDecimal inTons, BigDecimal outTons, BigDecimal balanceTons, BigDecimal expenses) {}

    /** Current stock of one product in ITS OWN unit (TON, KG or PIECE). */
    public record StockLine(Long productId, String code, String name, String unit, BigDecimal quantity) {}

    public record LowStockItem(Long productId, String code, String name, String unit, BigDecimal quantity, BigDecimal minimumStock) {}

    public record RecentTransaction(Long id, String type, String transactionNumber, LocalDate transactionDate, String partyName,
                                    BigDecimal totalAmount) {}

    /**
     * receivable = sum of positive party balances; payable = magnitude of negative ones (D11 sign convention).
     * Sections the caller lacks permission for are null and omitted from the JSON (see DashboardService).
     * {@code periodTotals} and {@code series} (one point per day) cover the selected {@code from}..{@code to}; stock on hand,
     * receivable/payable and low stock are current figures and do not depend on the period.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Dashboard(LocalDate today, LocalDate from, LocalDate to, Period periodTotals, BigDecimal receivable, BigDecimal payable,
                            BigDecimal stockTons, Long pieceProducts, Long lowStockCount, List<LowStockItem> lowStock,
                            List<StockLine> stockByProduct, List<DayPoint> series, List<RecentTransaction> recent) {}
}
