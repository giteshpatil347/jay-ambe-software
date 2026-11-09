package com.materialbook.expenses;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ExpenseDtos {
    private ExpenseDtos() {}

    // ---- categories ----

    public record CategoryResponse(Long id, String categoryCode, String categoryName, CategoryType categoryType,
                                   boolean fixed, CategoryStatus status) {
        static CategoryResponse of(ExpenseCategory c) {
            return new CategoryResponse(c.getId(), c.getCategoryCode(), c.getCategoryName(), c.getCategoryType(),
                    c.isFixed(), c.getStatus());
        }
    }

    public record CreateCategoryRequest(
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$", message = "must be UPPER_SNAKE_CASE") String categoryCode,
            @NotBlank @Size(max = 100) String categoryName,
            @NotNull CategoryType categoryType) {}

    /** Code and type are immutable. Fixed categories cannot be renamed or deactivated. */
    public record UpdateCategoryRequest(
            @NotBlank @Size(max = 100) String categoryName,
            @NotNull CategoryStatus status) {}

    // ---- expenses ----

    /**
     * Period and payment mode are not part of a salary payment; clients that still send them are ignored.
     * {@code grossSalary} is the MONTHLY salary the payment relates to.
     */
    public record SalaryInput(
            @NotNull Long employeeId,
            @NotNull @Pattern(regexp = "^(MONTHLY|ADHOC|ADVANCE|SETTLEMENT)$", message = "must be MONTHLY, ADHOC, ADVANCE or SETTLEMENT") String paymentType,
            @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal grossSalary,
            @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal advanceAdjustment) {}

    /** {@code usageQuantity} is the number of hours (HOURLY) or days (DAILY); the rupee amount is the expense amount. */
    public record ElectricityInput(
            @NotNull @Pattern(regexp = "^[12]_NO$", message = "must be 1_NO or 2_NO") String connectionNumber,
            @NotNull UsageType usageType,
            @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal usageQuantity) {}

    /**
     * Create and update body. {@code version} is required on update (optimistic lock) and ignored on create.
     * {@code salary} is required for SALARY categories, {@code electricity} for ELECTRICITY, neither for GENERAL.
     */
    public record ExpenseRequest(
            @NotNull LocalDate expenseDate,
            @NotNull Long categoryId,
            @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal amount, // required (> 0) except for ELECTRICITY, where the price is optional
            @Size(max = 150) String paidTo,
            PaymentMode paymentMode,
            @Size(max = 2000) String remarks,
            @Valid SalaryInput salary,
            @Valid ElectricityInput electricity,
            Long version) {}

    public record CancelRequest(@NotBlank @Size(max = 1000) String reason) {}

    public record SalaryDetail(Long employeeId, SalaryPaymentType paymentType, BigDecimal grossSalary, BigDecimal advanceAdjustment) {
        static SalaryDetail of(SalaryPayment s) {
            return new SalaryDetail(s.getEmployeeId(), s.getPaymentType(), s.getGrossSalary(), s.getAdvanceAdjustment());
        }
    }

    public record ElectricityDetail(String connectionNumber, UsageType usageType, BigDecimal usageQuantity) {
        static ElectricityDetail of(ElectricityReading r) {
            return new ElectricityDetail(r.getConnectionNumber(), r.getUsageType(), r.getUsageQuantity());
        }
    }

    /**
     * {@code salary}/{@code electricity} are only populated by the single-record endpoints (null in lists).
     * {@code usageType}/{@code usageQuantity}/{@code connectionNumber} are filled for ELECTRICITY rows everywhere, including lists,
     * and {@code employeeName}/{@code paymentType} for SALARY rows in lists (null otherwise): the list needs them to describe each row.
     */
    public record ExpenseResponse(Long id, String expenseNumber, LocalDate expenseDate, Long categoryId, String categoryName,
                                  CategoryType categoryType, BigDecimal amount, String paidTo, PaymentMode paymentMode,
                                  String remarks, ExpenseStatus status, Long version,
                                  Instant cancelledAt, String cancellationReason, Instant createdAt,
                                  UsageType usageType, BigDecimal usageQuantity, String connectionNumber,
                                  String employeeName, SalaryPaymentType paymentType,
                                  SalaryDetail salary, ElectricityDetail electricity) {
        static ExpenseResponse of(Expense e, SalaryDetail salary, ElectricityDetail electricity) {
            return of(e, salary, electricity, electricity == null ? null : electricity.usageType(),
                    electricity == null ? null : electricity.usageQuantity(),
                    electricity == null ? null : electricity.connectionNumber(), null, salary == null ? null : salary.paymentType());
        }

        static ExpenseResponse of(Expense e, SalaryDetail salary, ElectricityDetail electricity, UsageType usageType,
                                  BigDecimal usageQuantity, String connectionNumber, String employeeName, SalaryPaymentType paymentType) {
            ExpenseCategory c = e.getCategory();
            return new ExpenseResponse(e.getId(), e.getExpenseNumber(), e.getExpenseDate(), c.getId(), c.getCategoryName(),
                    c.getCategoryType(), e.getAmount(), e.getPaidTo(), e.getPaymentMode(),
                    e.getRemarks(), e.getStatus(), e.getVersion(), e.getCancelledAt(), e.getCancellationReason(),
                    e.getCreatedAt(), usageType, usageQuantity, connectionNumber, employeeName, paymentType, salary, electricity);
        }
    }

    /** Page envelope plus {@code totalAmount}: sum of POSTED rows matching the filters, independent of paging. */
    public record ExpensePage(List<ExpenseResponse> content, int page, int size, long totalElements, BigDecimal totalAmount) {}

    public record SalaryHistoryItem(Long expenseId, String expenseNumber, LocalDate expenseDate, SalaryPaymentType paymentType,
                                    BigDecimal grossSalary, BigDecimal advanceAdjustment, BigDecimal amount, ExpenseStatus status) {}
}
