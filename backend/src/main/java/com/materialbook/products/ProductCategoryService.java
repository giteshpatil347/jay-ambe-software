package com.materialbook.products;

import com.materialbook.audit.service.AuditService;
import com.materialbook.common.ApiException.ConflictException;
import com.materialbook.common.ApiException.NotFoundException;
import com.materialbook.products.ProductDtos.CategoryRequest;
import com.materialbook.products.ProductDtos.CategoryResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductCategoryService {
    private static final String MODULE = "PRODUCT_CATEGORY";

    private final ProductCategoryRepository categories;
    private final AuditService audit;

    public ProductCategoryService(ProductCategoryRepository categories, AuditService audit) {
        this.categories = categories;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categories.findAllByOrderByNameAsc().stream().map(ProductCategoryService::toResponse).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        if (categories.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Category already exists: " + name);
        }
        ProductCategory category = new ProductCategory(name);
        if (request.status() != null) {
            category.update(name, request.status());
        }
        category = categories.saveAndFlush(category);
        CategoryResponse response = toResponse(category);
        audit.log("PRODUCT_CATEGORY_CREATE", MODULE, category.getId(), null, response);
        return response;
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        ProductCategory category = categories.findById(id).orElseThrow(() -> new NotFoundException("Category not found"));
        String name = request.name().trim();
        if (categories.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("Category already exists: " + name);
        }
        CategoryResponse before = toResponse(category);
        category.update(name, request.status() != null ? request.status() : category.getStatus());
        CategoryResponse after = toResponse(category);
        audit.log("PRODUCT_CATEGORY_UPDATE", MODULE, id, before, after);
        return after;
    }

    private static CategoryResponse toResponse(ProductCategory c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getStatus());
    }
}
