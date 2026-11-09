package com.materialbook.reports;

import com.materialbook.common.ApiException.ValidationException;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** Shared query-string filters; each report ignores the ones that do not apply to it. {@code to} is inclusive. */
public record ReportFilter(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        Long partyId, Long productId, Long categoryId, DocumentStatus status) {

    public enum DocumentStatus { POSTED, CANCELLED }

    void requireValidRange() {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ValidationException("'from' must not be after 'to'");
        }
    }
}
