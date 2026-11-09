package com.materialbook.expenses;

import com.materialbook.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "expenses")
public class Expense extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_number", nullable = false, updatable = false)
    private String expenseNumber;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ExpenseCategory category;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "paid_to")
    private String paidTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode")
    private PaymentMode paymentMode;


    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpenseStatus status = ExpenseStatus.POSTED;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    protected Expense() {}

    public Expense(String expenseNumber, ExpenseCategory category, Long createdBy) {
        this.expenseNumber = expenseNumber;
        this.category = category;
        this.createdBy = createdBy;
    }

    public void update(LocalDate expenseDate, BigDecimal amount, String paidTo, PaymentMode paymentMode,
                       String remarks, Long userId) {
        this.expenseDate = expenseDate;
        this.amount = amount;
        this.paidTo = paidTo;
        this.paymentMode = paymentMode;
        this.remarks = remarks;
        this.updatedBy = userId;
    }

    public void setCategory(ExpenseCategory category) {
        this.category = category;
    }

    public void cancel(String reason, Long userId) {
        this.status = ExpenseStatus.CANCELLED;
        this.cancelledBy = userId;
        this.cancelledAt = Instant.now();
        this.cancellationReason = reason;
        this.updatedBy = userId;
    }

    public Long getId() { return id; }
    public String getExpenseNumber() { return expenseNumber; }
    public LocalDate getExpenseDate() { return expenseDate; }
    public ExpenseCategory getCategory() { return category; }
    public BigDecimal getAmount() { return amount; }
    public String getPaidTo() { return paidTo; }
    public PaymentMode getPaymentMode() { return paymentMode; }
    public String getRemarks() { return remarks; }
    public ExpenseStatus getStatus() { return status; }
    public Long getVersion() { return version; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancellationReason() { return cancellationReason; }
}
