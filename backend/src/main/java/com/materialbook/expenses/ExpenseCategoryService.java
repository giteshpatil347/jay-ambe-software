package com.materialbook.expenses;

import com.materialbook.audit.service.AuditService;
import com.materialbook.common.ApiException.ConflictException;
import com.materialbook.common.ApiException.NotFoundException;
import com.materialbook.common.ApiException.ValidationException;
import com.materialbook.expenses.ExpenseDtos.CategoryResponse;
import com.materialbook.expenses.ExpenseDtos.CreateCategoryRequest;
import com.materialbook.expenses.ExpenseDtos.UpdateCategoryRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseCategoryService {
    static final String MODULE = "EXPENSE_CATEGORY";

    private final ExpenseCategoryRepository repository;
    private final AuditService audit;

    public ExpenseCategoryService(ExpenseCategoryRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(CategoryStatus status) {
        List<ExpenseCategory> rows = status == null
                ? repository.findAllByOrderByCategoryNameAsc()
                : repository.findByStatusOrderByCategoryNameAsc(status);
        return rows.stream().map(CategoryResponse::of).toList();
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest r) {
        if (repository.existsByCategoryCode(r.categoryCode())) {
            throw new ConflictException("Category code already exists: " + r.categoryCode());
        }
        String name = r.categoryName().trim();
        if (repository.existsByCategoryNameIgnoreCase(name)) {
            throw new ConflictException("Category name already exists: " + name);
        }
        CategoryResponse created = CategoryResponse.of(
                repository.saveAndFlush(new ExpenseCategory(r.categoryCode(), name, r.categoryType())));
        audit.log("CREATE", MODULE, created.id(), null, created);
        return created;
    }

    @Transactional
    public CategoryResponse update(Long id, UpdateCategoryRequest r) {
        ExpenseCategory c = require(id);
        CategoryResponse before = CategoryResponse.of(c);
        String name = r.categoryName().trim();
        if (c.isFixed() && (!c.getCategoryName().equals(name) || r.status() != CategoryStatus.ACTIVE)) {
            throw new ValidationException("Fixed categories cannot be renamed or deactivated");
        }
        if (repository.existsByCategoryNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("Category name already exists: " + name);
        }
        c.update(name, r.status());
        CategoryResponse after = CategoryResponse.of(repository.saveAndFlush(c));
        audit.log("UPDATE", MODULE, id, before, after);
        return after;
    }

    ExpenseCategory require(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Expense category not found: " + id));
    }
}
