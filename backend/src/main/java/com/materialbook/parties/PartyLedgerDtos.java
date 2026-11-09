package com.materialbook.parties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Sign convention: balance &gt; 0 means the party owes us. A debit raises the balance (we dispatched material, or we paid
 * the party); a credit lowers it (we bought material, or the party paid us).
 */
public final class PartyLedgerDtos {
    private PartyLedgerDtos() {}

    public enum EntryKind { PURCHASE, DISPATCH, PAYMENT_PAID, PAYMENT_RECEIVED }

    public record LedgerParty(Long id, String code, String name, PartyType type) {}

    public record LedgerEntry(LocalDate date, EntryKind kind, String reference, BigDecimal debit, BigDecimal credit, BigDecimal balance) {}

    public record LedgerTotals(BigDecimal debit, BigDecimal credit) {}

    public record LedgerResponse(LedgerParty party, BigDecimal openingBalance, List<LedgerEntry> entries,
                                 BigDecimal closingBalance, LedgerTotals totals) {}

    public record PartyBalance(Long id, String code, String name, PartyType type, PartyStatus status, BigDecimal balance) {}
}
