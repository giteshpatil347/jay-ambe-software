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
import java.math.RoundingMode;

@Entity
@Table(name = "electricity_readings")
public class ElectricityReading {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_id", nullable = false, updatable = false)
    private Long expenseId;

    @Column(name = "connection_number", nullable = false)
    private String connectionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", nullable = false)
    private UsageType usageType;

    /** Hours (HOURLY) or days (DAILY); null only on rows created before V9. */
    @Column(name = "usage_quantity")
    private BigDecimal usageQuantity;

    protected ElectricityReading() {}

    public ElectricityReading(Long expenseId) {
        this.expenseId = expenseId;
    }

    public void update(String connectionNumber, UsageType usageType, BigDecimal usageQuantity) {
        this.connectionNumber = connectionNumber;
        this.usageType = usageType;
        this.usageQuantity = usageQuantity.setScale(2, RoundingMode.UNNECESSARY); // validated to at most 2 decimals
    }

    public Long getExpenseId() { return expenseId; }
    public String getConnectionNumber() { return connectionNumber; }
    public UsageType getUsageType() { return usageType; }
    public BigDecimal getUsageQuantity() { return usageQuantity; }
}
