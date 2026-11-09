package com.materialbook.payments;

import java.time.LocalDate;

/** Optional list filters; {@code q} matches payment number and reference number (case-insensitive substring). */
public record PaymentFilter(LocalDate from, LocalDate to, Long partyId, PaymentDirection direction, PaymentStatus status, String q) {}
