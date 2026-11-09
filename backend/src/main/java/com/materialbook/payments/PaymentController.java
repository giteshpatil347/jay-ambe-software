package com.materialbook.payments;

import com.materialbook.payments.PaymentDtos.CancelRequest;
import com.materialbook.payments.PaymentDtos.PaymentPage;
import com.materialbook.payments.PaymentDtos.PaymentRequest;
import com.materialbook.payments.PaymentDtos.PaymentResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@Validated
public class PaymentController {
    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAYMENT.VIEW')")
    PaymentPage list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long partyId,
            @RequestParam(required = false) PaymentDirection direction,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return service.list(new PaymentFilter(from, to, partyId, direction, status, q), page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYMENT.VIEW')")
    PaymentResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PAYMENT.CREATE')")
    PaymentResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PaymentRequest request) {
        return service.create(Long.valueOf(jwt.getSubject()), request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PAYMENT.CANCEL')")
    PaymentResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody CancelRequest request) {
        return service.cancel(Long.valueOf(jwt.getSubject()), id, request.reason());
    }
}
