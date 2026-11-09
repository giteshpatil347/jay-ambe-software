package com.materialbook.products;

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
import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_code", nullable = false, updatable = false, length = 50)
    private String code;

    @Column(name = "product_name", nullable = false, length = 150)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ProductCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProductUnit unit;

    private String description;

    @Column(name = "image_key")
    private String imageKey;

    @Column(name = "minimum_stock", nullable = false)
    private BigDecimal minimumStock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProductStatus status = ProductStatus.ACTIVE;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    protected Product() {}

    public Product(String code, String name, ProductCategory category, ProductUnit unit, String description,
                   BigDecimal minimumStock, Long createdBy) {
        this.code = code;
        this.createdBy = createdBy;
        update(name, category, unit, description, minimumStock);
    }

    public void update(String name, ProductCategory category, ProductUnit unit, String description, BigDecimal minimumStock) {
        this.name = name;
        this.category = category;
        this.unit = unit;
        this.description = description;
        this.minimumStock = minimumStock;
    }

    public void setStatus(ProductStatus status) { this.status = status; }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public ProductCategory getCategory() { return category; }
    public ProductUnit getUnit() { return unit; }
    public String getDescription() { return description; }
    public String getImageKey() { return imageKey; }
    public BigDecimal getMinimumStock() { return minimumStock; }
    public ProductStatus getStatus() { return status; }
}
