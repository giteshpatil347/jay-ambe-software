package com.materialbook.material;

import com.materialbook.material.MaterialDtos.CancelRequest;
import com.materialbook.material.MaterialDtos.TransactionDetail;
import com.materialbook.material.MaterialDtos.TransactionPage;
import com.materialbook.material.MaterialDtos.TransactionRequest;
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
@RequestMapping("/api/v1/material-in")
@Validated
public class MaterialInController {
    private static final TransactionType TYPE = TransactionType.IN;

    private final MaterialTransactionService service;

    public MaterialInController(MaterialTransactionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MATERIAL_IN.VIEW')")
    TransactionPage list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long partyId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return service.list(TYPE, new MaterialFilter(from, to, partyId, productId, status, q), page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MATERIAL_IN.VIEW')")
    TransactionDetail get(@PathVariable Long id) {
        return service.get(TYPE, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MATERIAL_IN.CREATE')")
    TransactionDetail create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TransactionRequest request) {
        return service.create(TYPE, Long.valueOf(jwt.getSubject()), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MATERIAL_IN.UPDATE')")
    TransactionDetail update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody TransactionRequest request) {
        return service.update(TYPE, Long.valueOf(jwt.getSubject()), id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('MATERIAL_IN.CANCEL')")
    TransactionDetail cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody CancelRequest request) {
        return service.cancel(TYPE, Long.valueOf(jwt.getSubject()), id, request.reason());
    }
}
