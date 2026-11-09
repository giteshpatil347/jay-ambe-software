package com.materialbook.expenses;

import com.materialbook.expenses.ExpenseDtos.SalaryHistoryItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalaryPaymentRepository extends JpaRepository<SalaryPayment, Long> {
    Optional<SalaryPayment> findByExpenseId(Long expenseId);

    List<SalaryPayment> findByExpenseIdIn(Collection<Long> expenseIds);

    @Query(value = """
            select new com.materialbook.expenses.ExpenseDtos$SalaryHistoryItem(e.id, e.expenseNumber, e.expenseDate,
                   sp.paymentType, sp.grossSalary, sp.advanceAdjustment, e.amount, e.status)
            from SalaryPayment sp, Expense e
            where e.id = sp.expenseId and sp.employeeId = :employeeId
            order by e.expenseDate desc, e.id desc""",
            countQuery = "select count(sp) from SalaryPayment sp where sp.employeeId = :employeeId")
    Page<SalaryHistoryItem> history(@Param("employeeId") Long employeeId, Pageable pageable);
}
