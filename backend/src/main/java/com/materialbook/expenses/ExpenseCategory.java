package com.materialbook.expenses;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "expense_categories")
public class ExpenseCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_code", nullable = false, updatable = false)
    private String categoryCode;

    @Column(name = "category_name", nullable = false)
    private String categoryName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_type", nullable = false, updatable = false)
    private CategoryType categoryType;

    @Column(name = "is_fixed", nullable = false, updatable = false)
    private boolean fixed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoryStatus status = CategoryStatus.ACTIVE;

    protected ExpenseCategory() {}

    public ExpenseCategory(String categoryCode, String categoryName, CategoryType categoryType) {
        this.categoryCode = categoryCode;
        this.categoryName = categoryName;
        this.categoryType = categoryType;
    }

    public void update(String categoryName, CategoryStatus status) {
        this.categoryName = categoryName;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getCategoryCode() { return categoryCode; }
    public String getCategoryName() { return categoryName; }
    public CategoryType getCategoryType() { return categoryType; }
    public boolean isFixed() { return fixed; }
    public CategoryStatus getStatus() { return status; }
}
