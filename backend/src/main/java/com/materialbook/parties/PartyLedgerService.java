package com.materialbook.parties;

import com.materialbook.common.ApiException.NotFoundException;
import com.materialbook.common.ApiException.ValidationException;
import com.materialbook.common.PageResponse;
import com.materialbook.parties.PartyLedgerDtos.EntryKind;
import com.materialbook.parties.PartyLedgerDtos.LedgerEntry;
import com.materialbook.parties.PartyLedgerDtos.LedgerParty;
import com.materialbook.parties.PartyLedgerDtos.LedgerResponse;
import com.materialbook.parties.PartyLedgerDtos.LedgerTotals;
import com.materialbook.parties.PartyLedgerDtos.PartyBalance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Party ledger over POSTED rows only. Business formula (positive = party owes us):
 * {@code opening_balance + SUM(OUT) - SUM(IN) - SUM(RECEIVED) + SUM(PAID)}.
 * Material transactions are read with SQL (no dependency on the material module).
 * A ledger request returning more than {@value #MAX_ENTRIES} entries is rejected so the running balance is never truncated.
 */
@Service
public class PartyLedgerService {
    static final int MAX_ENTRIES = 5000;
    private static final int MAX_PAGE_SIZE = 200;

    /** One row per POSTED document; debit raises the balance, credit lowers it. {@code src} breaks date/id ties. */
    private static final String EVENTS = """
            with ev as (
              select transaction_date as d, 0 as src, id,
                     case when transaction_type = 'OUT' then 'DISPATCH' else 'PURCHASE' end as kind,
                     transaction_number as ref,
                     case when transaction_type = 'OUT' then total_amount else 0 end as debit,
                     case when transaction_type = 'IN' then total_amount else 0 end as credit
              from material_transactions where party_id = :party and status = 'POSTED'
              union all
              select payment_date, 1, id,
                     case when direction = 'PAID' then 'PAYMENT_PAID' else 'PAYMENT_RECEIVED' end,
                     payment_number,
                     case when direction = 'PAID' then amount else 0 end,
                     case when direction = 'RECEIVED' then amount else 0 end
              from party_payments where party_id = :party and status = 'POSTED'
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PartyRepository parties;

    public PartyLedgerService(NamedParameterJdbcTemplate jdbc, PartyRepository parties) {
        this.jdbc = jdbc;
        this.parties = parties;
    }

    @Transactional(readOnly = true)
    public LedgerResponse ledger(Long partyId, LocalDate from, LocalDate to) {
        Party party = parties.findById(partyId).orElseThrow(() -> new NotFoundException("Party not found"));
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("party", partyId);
        BigDecimal opening = party.getOpeningBalance();
        if (from != null) {
            params.addValue("from", from);
            opening = opening.add(jdbc.queryForObject(
                    EVENTS + "select coalesce(sum(debit - credit), 0) from ev where d < :from", params, BigDecimal.class));
        }
        String range = (from == null ? "" : " and d >= :from") + (to == null ? "" : " and d <= :to");
        if (to != null) {
            params.addValue("to", to);
        }
        params.addValue("limit", MAX_ENTRIES + 1);
        List<LedgerEntry> entries = new ArrayList<>();
        BigDecimal[] running = {opening};
        BigDecimal[] sums = {BigDecimal.ZERO, BigDecimal.ZERO};
        jdbc.query(EVENTS + "select d, kind, ref, debit, credit from ev where true" + range
                + " order by d, src, id limit :limit", params, rs -> {
            BigDecimal debit = rs.getBigDecimal("debit");
            BigDecimal credit = rs.getBigDecimal("credit");
            running[0] = running[0].add(debit).subtract(credit);
            sums[0] = sums[0].add(debit);
            sums[1] = sums[1].add(credit);
            entries.add(new LedgerEntry(rs.getObject("d", LocalDate.class), EntryKind.valueOf(rs.getString("kind")),
                    rs.getString("ref"), debit, credit, running[0]));
        });
        if (entries.size() > MAX_ENTRIES) {
            throw new ValidationException("Ledger has more than " + MAX_ENTRIES + " entries; narrow the date range");
        }
        return new LedgerResponse(new LedgerParty(party.getId(), party.getCode(), party.getName(), party.getType()),
                opening, entries, running[0], new LedgerTotals(sums[0], sums[1]));
    }

    /** Current (all-time) balance of every party matching the filters; a BOTH party matches either type filter. */
    @Transactional(readOnly = true)
    public PageResponse<PartyBalance> balances(PartyType type, boolean onlyNonZero, int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int pageNo = Math.max(page, 0);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", pageSize)
                .addValue("offset", (long) pageNo * pageSize);
        String typeFilter = "";
        if (type != null) {
            typeFilter = type == PartyType.BOTH ? " and p.party_type = 'BOTH'" : " and p.party_type in (:type, 'BOTH')";
            params.addValue("type", type.name());
        }
        String base = """
                with delta as (
                  select party_id, case when transaction_type = 'OUT' then total_amount else -total_amount end as amt
                  from material_transactions where status = 'POSTED' and party_id is not null
                  union all
                  select party_id, case when direction = 'PAID' then amount else -amount end
                  from party_payments where status = 'POSTED'
                ), bal as (
                  select p.id, p.party_code, p.party_name, p.party_type, p.status,
                         p.opening_balance + coalesce(d.total, 0) as balance
                  from parties p left join (select party_id, sum(amt) as total from delta group by party_id) d on d.party_id = p.id
                  where true""" + typeFilter + """

                )
                """;
        String filter = onlyNonZero ? " where balance <> 0" : "";
        List<PartyBalance> rows = jdbc.query(base + "select * from bal" + filter + " order by party_name, id limit :limit offset :offset",
                params, (rs, i) -> new PartyBalance(rs.getLong("id"), rs.getString("party_code"), rs.getString("party_name"),
                        PartyType.valueOf(rs.getString("party_type")), PartyStatus.valueOf(rs.getString("status")),
                        rs.getBigDecimal("balance")));
        long total = jdbc.queryForObject(base + "select count(*) from bal" + filter, params, Long.class);
        return new PageResponse<>(rows, pageNo, pageSize, total);
    }
}
