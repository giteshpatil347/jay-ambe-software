package com.materialbook.products;

import com.materialbook.audit.service.AuditService;
import com.materialbook.common.ApiException.ConflictException;
import com.materialbook.common.ApiException.NotFoundException;
import com.materialbook.common.ApiException.ValidationException;
import com.materialbook.common.PageResponse;
import com.materialbook.products.ProductDtos.CreateProductRequest;
import com.materialbook.products.ProductDtos.ProductResponse;
import com.materialbook.products.ProductDtos.UpdateProductRequest;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
    private static final String MODULE = "PRODUCT";
    private static final int MAX_PAGE_SIZE = 200;

    private final ProductRepository products;
    private final ProductCategoryRepository categories;
    private final AuditService audit;

    public ProductService(ProductRepository products, ProductCategoryRepository categories, AuditService audit) {
        this.products = products;
        this.categories = categories;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(String q, Long categoryId, ProductStatus status, int page, int size) {
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String like = com.materialbook.common.LikePattern.contains(q);
                where.add(cb.or(cb.like(cb.lower(root.get("code")), like), cb.like(cb.lower(root.get("name")), like)));
            }
            if (categoryId != null) {
                where.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (status != null) {
                where.add(cb.equal(root.get("status"), status));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        Page<Product> result = products.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by("name", "id")));
        Map<Long, BigDecimal> stock = result.isEmpty() ? Map.of() : products.findStock(result.map(Product::getId).getContent()).stream()
                .collect(Collectors.toMap(ProductRepository.StockRow::getProductId, ProductRepository.StockRow::getQuantity));
        return PageResponse.of(result, p -> toResponse(p, stock.get(p.getId())));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public ProductResponse create(Long userId, CreateProductRequest request) {
        String code = request.productCode();
        if (code == null) {
            do {
                code = String.format("PRD-%04d", products.nextCodeNumber());
            } while (products.existsByCode(code));
        } else if (products.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Material code already exists: " + code);
        }
        Product product = products.saveAndFlush(new Product(code, request.name().trim(), category(request.categoryId()),
                request.unit(), blankToNull(request.description()), minimum(request.minimumStock()), userId));
        ProductResponse response = toResponse(product);
        audit.log("PRODUCT_CREATE", MODULE, product.getId(), null, response);
        return response;
    }

    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = require(id);
        ProductResponse before = toResponse(product);
        if (request.unit() != product.getUnit() && products.hasMovements(id)) {
            throw new ConflictException("Unit cannot be changed: the material already has stock movements or transactions");
        }
        product.update(request.name().trim(), category(request.categoryId()), request.unit(),
                blankToNull(request.description()), minimum(request.minimumStock()));
        products.flush();
        ProductResponse after = toResponse(product);
        audit.log("PRODUCT_UPDATE", MODULE, id, before, after);
        return after;
    }

    @Transactional
    public ProductResponse setStatus(Long id, ProductStatus status) {
        Product product = require(id);
        ProductResponse before = toResponse(product);
        product.setStatus(status);
        products.flush();
        ProductResponse after = toResponse(product);
        audit.log(status == ProductStatus.ACTIVE ? "PRODUCT_ACTIVATE" : "PRODUCT_DEACTIVATE", MODULE, id, before, after);
        return after;
    }

    private Product require(Long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Material not found"));
    }

    private ProductCategory category(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categories.findById(categoryId).orElseThrow(() -> new ValidationException("Unknown categoryId: " + categoryId));
    }

    private static BigDecimal minimum(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private ProductResponse toResponse(Product p) {
        return toResponse(p, products.findStock(List.of(p.getId())).stream().findFirst().map(ProductRepository.StockRow::getQuantity).orElse(null));
    }

    private static ProductResponse toResponse(Product p, BigDecimal available) {
        ProductCategory c = p.getCategory();
        return new ProductResponse(p.getId(), p.getCode(), p.getName(), c == null ? null : c.getId(), c == null ? null : c.getName(),
                p.getUnit(), p.getDescription(), p.getImageKey(), p.getMinimumStock(), available, p.getStatus());
    }
}
