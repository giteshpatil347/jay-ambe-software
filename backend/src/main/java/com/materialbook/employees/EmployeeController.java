package com.materialbook.employees;

import com.materialbook.common.PageResponse;
import com.materialbook.employees.EmployeeDtos.CreateEmployeeRequest;
import com.materialbook.employees.EmployeeDtos.EmployeeResponse;
import com.materialbook.employees.EmployeeDtos.SalaryAsOfResponse;
import com.materialbook.employees.EmployeeDtos.SalaryRevisionResponse;
import com.materialbook.employees.EmployeeDtos.UpdateEmployeeRequest;
import com.materialbook.expenses.ExpenseDtos.SalaryHistoryItem;
import com.materialbook.expenses.ExpenseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employees")
@Validated
public class EmployeeController {
    private final EmployeeService service;
    private final ExpenseService expenses;

    public EmployeeController(EmployeeService service, ExpenseService expenses) {
        this.service = service;
        this.expenses = expenses;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EMPLOYEE.VIEW')")
    PageResponse<EmployeeResponse> list(
            @RequestParam(required = false) EmployeeStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return service.list(status, q, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE.VIEW')")
    EmployeeResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('EMPLOYEE.CREATE')")
    EmployeeResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateEmployeeRequest request) {
        return service.create(Long.valueOf(jwt.getSubject()), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE.UPDATE')")
    EmployeeResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody UpdateEmployeeRequest request) {
        return service.update(Long.valueOf(jwt.getSubject()), id, request);
    }

    @GetMapping("/{id}/salary-revisions")
    @PreAuthorize("hasAuthority('EMPLOYEE.VIEW')")
    List<SalaryRevisionResponse> salaryRevisions(@PathVariable Long id) {
        return service.salaryRevisions(id);
    }

    @GetMapping("/{id}/salary-as-of")
    @PreAuthorize("hasAuthority('EMPLOYEE.VIEW')")
    SalaryAsOfResponse salaryAsOf(@PathVariable Long id,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return new SalaryAsOfResponse(service.salaryAsOf(id, date));
    }

    @GetMapping("/{id}/salary-history")
    @PreAuthorize("hasAuthority('EMPLOYEE.VIEW')")
    PageResponse<SalaryHistoryItem> salaryHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return expenses.salaryHistory(id, page, size);
    }
}
