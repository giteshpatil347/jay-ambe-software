package com.materialbook.parties;

import com.materialbook.common.PageResponse;
import com.materialbook.parties.PartyLedgerDtos.LedgerResponse;
import com.materialbook.parties.PartyLedgerDtos.PartyBalance;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/parties")
@Validated
public class PartyLedgerController {
    private final PartyLedgerService ledgers;

    public PartyLedgerController(PartyLedgerService ledgers) {
        this.ledgers = ledgers;
    }

    @GetMapping("/{id}/ledger")
    @PreAuthorize("hasAuthority('PARTY.VIEW')")
    LedgerResponse ledger(@PathVariable Long id,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ledgers.ledger(id, from, to);
    }

    @GetMapping("/balances")
    @PreAuthorize("hasAuthority('PARTY.VIEW')")
    PageResponse<PartyBalance> balances(@RequestParam(required = false) PartyType type,
                                        @RequestParam(defaultValue = "false") boolean onlyNonZero,
                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return ledgers.balances(type, onlyNonZero, page, size);
    }
}
