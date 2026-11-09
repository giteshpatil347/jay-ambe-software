package com.materialbook.material;

import java.time.LocalDate;

/** List filters; {@code q} matches transaction number, challan number or vehicle number (case-insensitive substring). */
record MaterialFilter(LocalDate from, LocalDate to, Long partyId, Long productId, TransactionStatus status, String q) {}
