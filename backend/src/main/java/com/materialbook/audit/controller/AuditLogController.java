package com.materialbook.audit.controller;

import com.materialbook.audit.dto.AuditLogResponse;
import com.materialbook.audit.service.AuditLogQueryService;
import com.materialbook.common.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
@Validated
public class AuditLogController {
    private final AuditLogQueryService queries;

    public AuditLogController(AuditLogQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT.VIEW')")
    public PageResponse<AuditLogResponse> list(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Long recordId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return queries.search(module, recordId, userId, action, from, to, page, size);
    }
}
