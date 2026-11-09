package com.materialbook.products;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class ProductDtos {
    private ProductDtos() {}

    public record CategoryResponse(Long id, String name, ProductStatus status) {}

    public record CategoryRequest(
            @NotBlank @Size(max = 100) String name,
            ProductStatus status) {}

    public record ProductResponse(Long id, String code, String name, Long categoryId, String categoryName,
                                  ProductUnit unit, String description, String imageKey, BigDecimal minimumStock,
                                  BigDecimal availableStock, ProductStatus status) {}

    public record CreateProductRequest(
            @Pattern(regexp = "^[A-Za-z0-9_-]{1,50}$", message = "may contain letters, digits, '-' and '_' only") String productCode,
            @NotBlank @Size(max = 150) String name,
            Long categoryId,
            @NotNull ProductUnit unit,
            @Size(max = 2000) String description,
            @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal minimumStock) {}

    public record UpdateProductRequest(
            @NotBlank @Size(max = 150) String name,
            Long categoryId,
            @NotNull ProductUnit unit,
            @Size(max = 2000) String description,
            @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal minimumStock) {}
}
