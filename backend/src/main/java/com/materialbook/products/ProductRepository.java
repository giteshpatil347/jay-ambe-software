package com.materialbook.products;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    interface StockRow {
        Long getProductId();

        BigDecimal getQuantity();
    }

    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    boolean existsByCode(String code);

    boolean existsByCodeIgnoreCase(String code);

    /** Read-only view of the inventory module's balances (rows are created by a DB trigger). */
    @Query(value = "select product_id as productId, quantity from stock_balances where product_id in (:ids)", nativeQuery = true)
    List<StockRow> findStock(@Param("ids") Collection<Long> ids);

    /** True once any stock movement or transaction item references the product (unit becomes immutable). */
    @Query(value = "select exists(select 1 from stock_movements where product_id = :id) "
            + "or exists(select 1 from material_transaction_items where product_id = :id)", nativeQuery = true)
    boolean hasMovements(@Param("id") Long id);

    @Query(value = "select nextval('product_code_seq')", nativeQuery = true)
    long nextCodeNumber();
}
