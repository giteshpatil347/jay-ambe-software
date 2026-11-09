package com.materialbook.products;

import com.materialbook.common.PageResponse;
import com.materialbook.products.ProductDtos.CreateProductRequest;
import com.materialbook.products.ProductDtos.ProductResponse;
import com.materialbook.products.ProductDtos.UpdateProductRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductService products;

    public ProductController(ProductService products) {
        this.products = products;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT.VIEW')")
    PageResponse<ProductResponse> list(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) Long categoryId,
                                       @RequestParam(required = false) ProductStatus status,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return products.list(q, categoryId, status, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT.VIEW')")
    ProductResponse get(@PathVariable Long id) {
        return products.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCT.CREATE')")
    ProductResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateProductRequest request) {
        return products.create(Long.valueOf(jwt.getSubject()), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT.UPDATE')")
    ProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return products.update(id, request);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('PRODUCT.DELETE')")
    ProductResponse deactivate(@PathVariable Long id) {
        return products.setStatus(id, ProductStatus.INACTIVE);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PRODUCT.DELETE')")
    ProductResponse activate(@PathVariable Long id) {
        return products.setStatus(id, ProductStatus.ACTIVE);
    }
}
