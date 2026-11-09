package com.materialbook.expenses;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {
    boolean existsByCategoryCode(String categoryCode);

    boolean existsByCategoryNameIgnoreCase(String categoryName);

    boolean existsByCategoryNameIgnoreCaseAndIdNot(String categoryName, Long id);

    List<ExpenseCategory> findByStatusOrderByCategoryNameAsc(CategoryStatus status);

    List<ExpenseCategory> findAllByOrderByCategoryNameAsc();
}
