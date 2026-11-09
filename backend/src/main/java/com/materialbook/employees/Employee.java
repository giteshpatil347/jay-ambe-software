package com.materialbook.employees;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "employees")
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", nullable = false, updatable = false)
    private String employeeCode;

    @Column(name = "employee_name", nullable = false)
    private String employeeName;

    private String mobile;

    /** Mirrors the salary revision current today (see EmployeeService); API reads resolve it from the revisions. */
    @Column(name = "monthly_salary", nullable = false)
    private BigDecimal monthlySalary;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Employee() {}

    public Employee(String employeeCode, String employeeName, String mobile, BigDecimal monthlySalary,
                    LocalDate joiningDate, EmployeeStatus status) {
        this.employeeCode = employeeCode;
        this.monthlySalary = monthlySalary;
        update(employeeName, mobile, joiningDate, status);
    }

    public void update(String employeeName, String mobile, LocalDate joiningDate, EmployeeStatus status) {
        this.employeeName = employeeName;
        this.mobile = mobile;
        this.joiningDate = joiningDate;
        this.status = status;
    }

    public void setMonthlySalary(BigDecimal monthlySalary) {
        this.monthlySalary = monthlySalary;
    }

    public Long getId() { return id; }
    public String getEmployeeCode() { return employeeCode; }
    public String getEmployeeName() { return employeeName; }
    public String getMobile() { return mobile; }
    public BigDecimal getMonthlySalary() { return monthlySalary; }
    public LocalDate getJoiningDate() { return joiningDate; }
    public EmployeeStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
