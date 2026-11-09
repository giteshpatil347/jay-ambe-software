package com.materialbook.expenses;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Optional list filters; {@code q} matches paid-to and expense number (case-insensitive substring). */
public record ExpenseFilter(LocalDate from, LocalDate to, Long categoryId, ExpenseStatus status, String q) {

    Specification<Expense> toSpecification() {
        return (root, query, cb) -> cb.and(predicates(root, cb).toArray(Predicate[]::new));
    }

    List<Predicate> predicates(Root<Expense> root, CriteriaBuilder cb) {
        List<Predicate> ps = new ArrayList<>();
        if (from != null) {
            ps.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), from));
        }
        if (to != null) {
            ps.add(cb.lessThanOrEqualTo(root.get("expenseDate"), to));
        }
        if (categoryId != null) {
            ps.add(cb.equal(root.get("category").get("id"), categoryId));
        }
        if (status != null) {
            ps.add(cb.equal(root.get("status"), status));
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            ps.add(cb.or(cb.like(cb.lower(root.get("paidTo")), like, '\\'),
                    cb.like(cb.lower(root.get("expenseNumber")), like, '\\')));
        }
        return ps;
    }
}
