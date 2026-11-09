package com.materialbook.employees;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class EmployeeDtos {
    private EmployeeDtos() {}

    /** {@code monthlySalary} is the salary in force today (latest revision with effectiveFrom <= today). */
    public record EmployeeResponse(Long id, String employeeCode, String employeeName, String mobile, BigDecimal monthlySalary,
                                   LocalDate joiningDate, EmployeeStatus status, Instant createdAt) {
        static EmployeeResponse of(Employee e, BigDecimal currentSalary) {
            return new EmployeeResponse(e.getId(), e.getEmployeeCode(), e.getEmployeeName(), e.getMobile(),
                    currentSalary != null ? currentSalary : e.getMonthlySalary(), e.getJoiningDate(), e.getStatus(), e.getCreatedAt());
        }
    }

    /**
     * {@code employeeCode} is optional on create (auto-generated as EMP-0001...); it cannot be changed afterwards.
     * {@code effectiveFrom} dates the first salary revision (default: joining date, else today).
     */
    public record CreateEmployeeRequest(
            @Size(max = 50) String employeeCode,
            @NotBlank @Size(max = 150) String employeeName,
            @Size(max = 20) String mobile,
            @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal monthlySalary,
            LocalDate effectiveFrom,
            LocalDate joiningDate) {}

    /**
     * Status ACTIVE/INACTIVE is how an employee is activated/deactivated.
     * {@code monthlySalary} is optional: omitted = salary untouched. When it differs from the salary in force on
     * {@code effectiveFrom} (default today) a revision is added there (a revision on that same date is replaced).
     */
    public record UpdateEmployeeRequest(
            @NotBlank @Size(max = 150) String employeeName,
            @Size(max = 20) String mobile,
            @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal monthlySalary,
            LocalDate effectiveFrom,
            @Size(max = 2000) String salaryRemarks,
            LocalDate joiningDate,
            @NotNull EmployeeStatus status) {}

    public record SalaryRevisionResponse(Long id, LocalDate effectiveFrom, int year, BigDecimal monthlySalary, String remarks,
                                         String createdByName) {}

    public record SalaryAsOfResponse(BigDecimal monthlySalary) {}
}
