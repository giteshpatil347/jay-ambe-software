package com.materialbook.expenses;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ElectricityReadingRepository extends JpaRepository<ElectricityReading, Long> {
    Optional<ElectricityReading> findByExpenseId(Long expenseId);

    List<ElectricityReading> findByExpenseIdIn(Collection<Long> expenseIds);
}
