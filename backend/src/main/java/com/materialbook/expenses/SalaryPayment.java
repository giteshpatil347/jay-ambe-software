package com.materialbook.expenses;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "salary_payments")
public class SalaryPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_id", nullable = false, updatable = false)
    private Long expenseId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false)
    private SalaryPaymentType paymentType;

    @Column(name = "gross_salary", nullable = false)
    private BigDecimal grossSalary;

    @Column(name = "advance_adjustment", nullable = false)
    private BigDecimal advanceAdjustment;

    protected SalaryPayment() {}

    public SalaryPayment(Long expenseId) {
        this.expenseId = expenseId;
    }

    public void update(Long employeeId, SalaryPaymentType paymentType, BigDecimal grossSalary, BigDecimal advanceAdjustment) {
        this.employeeId = employeeId;
        this.paymentType = paymentType;
        this.grossSalary = grossSalary;
        this.advanceAdjustment = advanceAdjustment;
    }

    public Long getExpenseId() { return expenseId; }
    public Long getEmployeeId() { return employeeId; }
    public SalaryPaymentType getPaymentType() { return paymentType; }
    public BigDecimal getGrossSalary() { return grossSalary; }
    public BigDecimal getAdvanceAdjustment() { return advanceAdjustment; }
}
