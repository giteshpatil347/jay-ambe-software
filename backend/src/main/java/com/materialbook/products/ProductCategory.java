package com.materialbook.products;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_categories")
public class ProductCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProductStatus status = ProductStatus.ACTIVE;

    protected ProductCategory() {}

    public ProductCategory(String name) { this.name = name; }

    public void update(String name, ProductStatus status) {
        this.name = name;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public ProductStatus getStatus() { return status; }
}
