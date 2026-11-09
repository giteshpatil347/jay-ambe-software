package com.materialbook.expenses;

import com.materialbook.expenses.ExpenseDtos.CancelRequest;
import com.materialbook.expenses.ExpenseDtos.ExpensePage;
import com.materialbook.expenses.ExpenseDtos.ExpenseRequest;
import com.materialbook.expenses.ExpenseDtos.ExpenseResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
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
@RequestMapping("/api/v1/expenses")
@Validated
public class ExpenseController {
    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EXPENSE.VIEW')")
    ExpensePage list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ExpenseStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return service.list(new ExpenseFilter(from, to, categoryId, status, q), page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EXPENSE.VIEW')")
    ExpenseResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('EXPENSE.CREATE')")
    ExpenseResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ExpenseRequest request) {
        return service.create(Long.valueOf(jwt.getSubject()), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EXPENSE.UPDATE')")
    ExpenseResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody ExpenseRequest request) {
        return service.update(Long.valueOf(jwt.getSubject()), id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('EXPENSE.CANCEL')")
    ExpenseResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody CancelRequest request) {
        return service.cancel(Long.valueOf(jwt.getSubject()), id, request.reason());
    }
}
