package com.materialbook.expenses;

import com.materialbook.audit.service.AuditService;
import com.materialbook.common.ApiException.ConflictException;
import com.materialbook.common.ApiException.NotFoundException;
import com.materialbook.common.ApiException.ValidationException;
import com.materialbook.common.PageResponse;
import com.materialbook.employees.EmployeeService;
import com.materialbook.expenses.ExpenseDtos.ElectricityDetail;
import com.materialbook.expenses.ExpenseDtos.ElectricityInput;
import com.materialbook.expenses.ExpenseDtos.ExpensePage;
import com.materialbook.expenses.ExpenseDtos.ExpenseRequest;
import com.materialbook.expenses.ExpenseDtos.ExpenseResponse;
import com.materialbook.expenses.ExpenseDtos.SalaryDetail;
import com.materialbook.expenses.ExpenseDtos.SalaryHistoryItem;
import com.materialbook.expenses.ExpenseDtos.SalaryInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expenses with their optional salary / electricity detail, all written in one transaction and audited.
 * <ul>
 *   <li>Number: {@code EXP-<current UTC year>-NNNNN} from {@code next_document_number('EXP', year)} (year of creation, not of expense_date).</li>
 *   <li>Detail is dictated by the category type: SALARY needs {@code salary}, ELECTRICITY needs {@code electricity}, GENERAL neither.</li>
 *   <li>SALARY stores no payment mode and ELECTRICITY no paid-to / payment mode (null even if sent).</li>
 *   <li>Salary: {@code expenses.amount} is the paid amount; only {@code advanceAdjustment <= grossSalary} is enforced
 *       (no amount == gross - adjustment check, so ADHOC/ADVANCE payments stay free-form).</li>
 *   <li>Update may not move an expense to a category of a different type; cancelled expenses are immutable.</li>
 * </ul>
 */
@Service
public class ExpenseService {
    static final String MODULE = "EXPENSE";

    private final ExpenseRepository expenses;
    private final ExpenseCategoryService categories;
    private final SalaryPaymentRepository salaries;
    private final ElectricityReadingRepository readings;
    private final EmployeeService employees;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final EntityManager em;

    public ExpenseService(ExpenseRepository expenses, ExpenseCategoryService categories, SalaryPaymentRepository salaries,
                          ElectricityReadingRepository readings, EmployeeService employees, AuditService audit,
                          JdbcTemplate jdbc, EntityManager em) {
        this.expenses = expenses;
        this.categories = categories;
        this.salaries = salaries;
        this.readings = readings;
        this.employees = employees;
        this.audit = audit;
        this.jdbc = jdbc;
        this.em = em;
    }

    @Transactional(readOnly = true)
    public ExpensePage list(ExpenseFilter filter, int page, int size) {
        Page<Expense> result = expenses.findAll(filter.toSpecification(),
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.desc("id"))));
        // one query for the usage of all electricity rows on the page (no per-row lookups)
        List<Long> electricityIds = result.getContent().stream()
                .filter(e -> e.getCategory().getCategoryType() == CategoryType.ELECTRICITY).map(Expense::getId).toList();
        Map<Long, ElectricityReading> usage = electricityIds.isEmpty() ? Map.of()
                : readings.findByExpenseIdIn(electricityIds).stream().collect(Collectors.toMap(ElectricityReading::getExpenseId, r -> r));
        // likewise one query for the salary rows and one for their employees' names
        List<Long> salaryIds = result.getContent().stream()
                .filter(e -> e.getCategory().getCategoryType() == CategoryType.SALARY).map(Expense::getId).toList();
        Map<Long, SalaryPayment> pays = salaryIds.isEmpty() ? Map.of()
                : salaries.findByExpenseIdIn(salaryIds).stream().collect(Collectors.toMap(SalaryPayment::getExpenseId, s -> s));
        Map<Long, String> names = employees.names(pays.values().stream().map(SalaryPayment::getEmployeeId).collect(Collectors.toSet()));
        PageResponse<ExpenseResponse> p = PageResponse.of(result, e -> {
            ElectricityReading r = usage.get(e.getId());
            SalaryPayment s = pays.get(e.getId());
            return ExpenseResponse.of(e, null, null, r == null ? null : r.getUsageType(), r == null ? null : r.getUsageQuantity(),
                    r == null ? null : r.getConnectionNumber(), s == null ? null : names.get(s.getEmployeeId()),
                    s == null ? null : s.getPaymentType());
        });
        return new ExpensePage(p.content(), p.page(), p.size(), p.totalElements(), postedTotal(filter));
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(Long id) {
        return snapshot(require(id));
    }

    @Transactional
    public ExpenseResponse create(Long userId, ExpenseRequest r) {
        ExpenseCategory category = categories.require(r.categoryId());
        if (category.getStatus() != CategoryStatus.ACTIVE) {
            throw new ValidationException("Expense category is inactive");
        }
        requireDetailFor(category.getCategoryType(), r);
        requireAmountFor(category.getCategoryType(), r);
        String number = jdbc.queryForObject("select next_document_number('EXP', ?)", String.class,
                Year.now(ZoneOffset.UTC).getValue());
        Expense e = new Expense(number, category, userId);
        applyCommon(e, r, category.getCategoryType(), null);
        expenses.saveAndFlush(e);
        if (r.salary() != null) {
            employees.requireActive(r.salary().employeeId());
            applySalary(new SalaryPayment(e.getId()), r.salary());
        }
        if (r.electricity() != null) {
            applyElectricity(new ElectricityReading(e.getId()), r.electricity());
        }
        ExpenseResponse created = snapshot(e);
        audit.log("CREATE", MODULE, e.getId(), null, created);
        return created;
    }

    @Transactional
    public ExpenseResponse update(Long userId, Long id, ExpenseRequest r) {
        if (r.version() == null) {
            throw new ValidationException("version is required");
        }
        Expense e = require(id);
        if (e.getStatus() == ExpenseStatus.CANCELLED) {
            throw new ConflictException("Cancelled expenses cannot be edited");
        }
        if (!e.getVersion().equals(r.version())) {
            throw new ConflictException("The record was modified by someone else; reload and retry");
        }
        ExpenseCategory category = categories.require(r.categoryId());
        if (category.getCategoryType() != e.getCategory().getCategoryType()) {
            throw new ValidationException("Category type of an expense cannot be changed");
        }
        requireDetailFor(category.getCategoryType(), r);
        requireAmountFor(category.getCategoryType(), r);
        ExpenseResponse before = snapshot(e);
        e.setCategory(category);
        applyCommon(e, r, category.getCategoryType(), userId);
        if (r.salary() != null) {
            SalaryPayment s = salaries.findByExpenseId(id).orElseThrow();
            if (!s.getEmployeeId().equals(r.salary().employeeId())) {
                employees.requireActive(r.salary().employeeId());
            }
            applySalary(s, r.salary());
        }
        if (r.electricity() != null) {
            applyElectricity(readings.findByExpenseId(id).orElseThrow(), r.electricity());
        }
        expenses.saveAndFlush(e);
        ExpenseResponse after = snapshot(e);
        audit.log("UPDATE", MODULE, id, before, after);
        return after;
    }

    @Transactional
    public ExpenseResponse cancel(Long userId, Long id, String reason) {
        Expense e = require(id);
        if (e.getStatus() == ExpenseStatus.CANCELLED) {
            throw new ConflictException("Expense is already cancelled");
        }
        ExpenseResponse before = snapshot(e);
        e.cancel(reason.trim(), userId);
        expenses.saveAndFlush(e);
        ExpenseResponse after = snapshot(e);
        audit.log("CANCEL", MODULE, id, before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public PageResponse<SalaryHistoryItem> salaryHistory(Long employeeId, int page, int size) {
        employees.requireExists(employeeId);
        return PageResponse.of(salaries.history(employeeId, PageRequest.of(page, size)), item -> item);
    }

    private Expense require(Long id) {
        return expenses.findById(id).orElseThrow(() -> new NotFoundException("Expense not found: " + id));
    }

    private ExpenseResponse snapshot(Expense e) {
        return ExpenseResponse.of(e,
                salaries.findByExpenseId(e.getId()).map(SalaryDetail::of).orElse(null),
                readings.findByExpenseId(e.getId()).map(ElectricityDetail::of).orElse(null));
    }

    private static void requireDetailFor(CategoryType type, ExpenseRequest r) {
        boolean salaryOk = (type == CategoryType.SALARY) == (r.salary() != null);
        boolean electricityOk = (type == CategoryType.ELECTRICITY) == (r.electricity() != null);
        if (!salaryOk || !electricityOk) {
            throw new ValidationException(switch (type) {
                case SALARY -> "A SALARY category requires 'salary' details and no 'electricity' details";
                case ELECTRICITY -> "An ELECTRICITY category requires 'electricity' details and no 'salary' details";
                case GENERAL -> "A GENERAL category must not carry 'salary' or 'electricity' details";
            });
        }
    }

    /** The rupee amount is required (> 0) for every category except ELECTRICITY, where the price is optional (absent = 0.00). */
    private static void requireAmountFor(CategoryType type, ExpenseRequest r) {
        if (type != CategoryType.ELECTRICITY && (r.amount() == null || r.amount().signum() <= 0)) {
            throw new ValidationException("Amount is required and must be greater than zero");
        }
    }

    /** Fields the forms no longer offer for a category type are stored as null even if an old client still sends them. */
    private static void applyCommon(Expense e, ExpenseRequest r, CategoryType type, Long userId) {
        boolean salary = type == CategoryType.SALARY;
        boolean electricity = type == CategoryType.ELECTRICITY;
        e.update(r.expenseDate(), r.amount() == null ? new BigDecimal("0.00") : r.amount(), electricity ? null : r.paidTo(), salary || electricity ? null : r.paymentMode(),
                r.remarks(), userId);
    }

    private void applySalary(SalaryPayment s, SalaryInput in) {
        BigDecimal adjustment = in.advanceAdjustment() == null ? BigDecimal.ZERO : in.advanceAdjustment();
        if (adjustment.compareTo(in.grossSalary()) > 0) {
            throw new ValidationException("advanceAdjustment cannot exceed grossSalary");
        }
        s.update(in.employeeId(), SalaryPaymentType.valueOf(in.paymentType()), in.grossSalary(), adjustment);
        salaries.save(s);
    }

    private void applyElectricity(ElectricityReading reading, ElectricityInput in) {
        reading.update(in.connectionNumber(), in.usageType(), in.usageQuantity());
        readings.save(reading);
    }

    private BigDecimal postedTotal(ExpenseFilter filter) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<BigDecimal> q = cb.createQuery(BigDecimal.class);
        Root<Expense> root = q.from(Expense.class);
        List<Predicate> ps = filter.predicates(root, cb);
        ps.add(cb.equal(root.get("status"), ExpenseStatus.POSTED));
        q.select(cb.coalesce(cb.sum(root.<BigDecimal>get("amount")), BigDecimal.ZERO)).where(ps.toArray(Predicate[]::new));
        return em.createQuery(q).getSingleResult();
    }
}
