package com.materialbook.reports;

import com.materialbook.reports.ReportDtos.DayPoint;
import com.materialbook.reports.ReportDtos.Dashboard;
import com.materialbook.reports.ReportDtos.LowStockItem;
import com.materialbook.reports.ReportDtos.Period;
import com.materialbook.reports.ReportDtos.RecentTransaction;
import com.materialbook.reports.ReportDtos.StockLine;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import com.materialbook.common.ApiException.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard figures over POSTED rows only, built per the caller's authorities (unpermitted sections are not queried and are omitted).
 * Material IN/OUT and stock are TONS (a quantity view); only expenses/receivable/payable are rupees (DASHBOARD.FINANCIAL).
 * Daily sums are fetched once and folded into today / month-to-date / 30-day series.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {
    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 366;
    private static final BigDecimal MONEY_ZERO = new BigDecimal("0.00");
    private static final BigDecimal TONS_ZERO = new BigDecimal("0.000");
    /** 1 TON = 1000 KG; items counted in pieces cannot be expressed in tons and contribute 0 (they are listed in pieces). */
    private static final String TONS = "case p.unit when 'TON' then 1 when 'KG' then 0.001 else 0 end";

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Default period: the last 30 days up to today. */
    public Dashboard dashboard(LocalDate today) {
        return dashboard(today, null, null);
    }

    /**
     * {@code from}/{@code to} select the period of the totals and the daily series (both inclusive). Missing {@code to} = today,
     * missing {@code from} = 30 days ending at {@code to}; a {@code to} in the future is clamped to today.
     */
    public Dashboard dashboard(LocalDate today, LocalDate fromParam, LocalDate toParam) {
        LocalDate to = toParam == null || toParam.isAfter(today) ? today : toParam;
        LocalDate from = fromParam == null ? to.minusDays(DEFAULT_DAYS - 1) : fromParam;
        if (from.isAfter(to)) {
            throw new ValidationException("'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new ValidationException("The period can cover at most " + MAX_DAYS + " days");
        }
        Set<String> granted = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        boolean in = granted.contains("MATERIAL_IN.VIEW");
        boolean out = granted.contains("MATERIAL_OUT.VIEW");
        boolean stock = granted.contains("INVENTORY.VIEW");
        boolean financial = granted.contains("DASHBOARD.FINANCIAL");

        Map<String, Map<LocalDate, BigDecimal>> daily = dailySums(from, to, in, out, financial);
        boolean anySeries = in || out || stock || financial;

        Map<LocalDate, BigDecimal> balanceByDay = stock ? closingBalances(from, to) : Map.of();
        List<DayPoint> series = null;
        if (anySeries) {
            series = new ArrayList<>();
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                series.add(new DayPoint(d, in ? at(daily, "IN", d) : null, out ? at(daily, "OUT", d) : null,
                        stock ? balanceByDay.get(d) : null, financial ? at(daily, "EXP", d) : null));
            }
        }
        Period period = new Period(in ? periodSum(daily, "IN") : null, out ? periodSum(daily, "OUT") : null,
                financial ? periodSum(daily, "EXP") : null);

        BigDecimal[] balances = financial ? balances() : null;
        BigDecimal stockTons = stock ? stockTons() : null;
        Long pieceProducts = stock ? pieceProducts() : null;
        Long lowCount = stock ? lowStockCount() : null;
        List<LowStockItem> low = stock ? lowStock() : null;
        List<StockLine> byProduct = stock ? stockByProduct() : null;
        List<RecentTransaction> recent = financial && (in || out) ? recent(in, out) : null;

        return new Dashboard(today, from, to, period, balances == null ? null : balances[0], balances == null ? null : balances[1],
                stockTons, pieceProducts, lowCount, low, byProduct, series, recent);
    }

    /** Daily sums over the selected period; only the caller's permitted kinds are queried. */
    private Map<String, Map<LocalDate, BigDecimal>> dailySums(LocalDate from, LocalDate to, boolean in, boolean out, boolean expenses) {
        Map<String, Map<LocalDate, BigDecimal>> daily = new HashMap<>();
        List<String> parts = new ArrayList<>();
        if (in) {
            parts.add("""
                    select 'IN' as kind, t.transaction_date as d, i.quantity * (%s) as a
                      from material_transactions t
                      join material_transaction_items i on i.transaction_id = t.id and i.superseded_at is null
                      join products p on p.id = i.product_id
                      where t.transaction_type = 'IN' and t.status = 'POSTED' and t.transaction_date between :from and :to""".formatted(TONS));
        }
        if (out) {
            parts.add("""
                    select 'OUT' as kind, t.transaction_date as d, i.quantity * (%s) as a
                      from material_transactions t
                      join material_transaction_items i on i.transaction_id = t.id and i.superseded_at is null
                      join products p on p.id = i.product_id
                      where t.transaction_type = 'OUT' and t.status = 'POSTED' and t.transaction_date between :from and :to""".formatted(TONS));
        }
        if (expenses) {
            parts.add("""
                    select 'EXP' as kind, expense_date as d, amount as a from expenses
                      where status = 'POSTED' and expense_date between :from and :to""");
        }
        if (parts.isEmpty()) {
            return daily;
        }
        jdbc.query("select kind, d, sum(a) as a from (" + String.join(" union all ", parts) + ") x group by kind, d",
                new MapSqlParameterSource("from", from).addValue("to", to), rs -> {
                    String kind = rs.getString("kind");
                    BigDecimal sum = rs.getBigDecimal("a");
                    daily.computeIfAbsent(kind, k -> new HashMap<>())
                            .put(rs.getObject("d", LocalDate.class), "EXP".equals(kind) ? sum : sum.setScale(3, RoundingMode.HALF_UP));
                });
        return daily;
    }

    /** Closing stock (tons) at the end of each day of the period: the total at {@code today} (= period end), walked backwards by each day's net movement. */
    private Map<LocalDate, BigDecimal> closingBalances(LocalDate from, LocalDate today) {
        Map<LocalDate, BigDecimal> net = new HashMap<>();
        jdbc.query("select m.movement_date as d, sum(m.quantity_delta * (" + TONS + ")) as n from stock_movements m"
                        + " join products p on p.id = m.product_id where m.movement_date between :from and :to group by m.movement_date",
                new MapSqlParameterSource("from", from).addValue("to", today),
                rs -> { net.put(rs.getObject("d", LocalDate.class), rs.getBigDecimal("n")); });
        BigDecimal balance = jdbc.queryForObject("select coalesce(sum(m.quantity_delta * (" + TONS + ")), 0) from stock_movements m"
                + " join products p on p.id = m.product_id where m.movement_date <= :to", new MapSqlParameterSource("to", today), BigDecimal.class);
        Map<LocalDate, BigDecimal> closing = new HashMap<>();
        for (LocalDate d = today; !d.isBefore(from); d = d.minusDays(1)) {
            closing.put(d, balance.setScale(3, RoundingMode.HALF_UP));
            balance = balance.subtract(net.getOrDefault(d, TONS_ZERO));
        }
        return closing;
    }

    private BigDecimal stockTons() {
        return jdbc.getJdbcTemplate().queryForObject("select coalesce(sum(b.quantity * (" + TONS + ")), 0) from stock_balances b"
                + " join products p on p.id = b.product_id", BigDecimal.class).setScale(3, RoundingMode.HALF_UP);
    }

    /** Products counted in pieces that hold stock: they are not part of any tons figure. */
    private long pieceProducts() {
        return jdbc.getJdbcTemplate().queryForObject("select count(*) from products p join stock_balances b on b.product_id = p.id"
                + " where p.unit = 'PIECE' and b.quantity > 0", Long.class);
    }

    /** Biggest stocks first (weight products by tons, then piece products), each in its own unit. */
    private List<StockLine> stockByProduct() {
        return jdbc.getJdbcTemplate().query("select p.id, p.product_code, p.product_name, p.unit, b.quantity from products p"
                        + " join stock_balances b on b.product_id = p.id where b.quantity > 0"
                        + " order by (case p.unit when 'PIECE' then 1 else 0 end), b.quantity * (" + TONS + ") desc, p.product_name limit 8",
                (rs, i) -> new StockLine(rs.getLong("id"), rs.getString("product_code"), rs.getString("product_name"),
                        rs.getString("unit"), rs.getBigDecimal("quantity")));
    }

    private BigDecimal[] balances() {
        return jdbc.getJdbcTemplate().queryForObject("""
                with delta as (
                  select party_id, case when transaction_type = 'OUT' then total_amount else -total_amount end as amt
                  from material_transactions where status = 'POSTED' and party_id is not null
                  union all
                  select party_id, case when direction = 'PAID' then amount else -amount end
                  from party_payments where status = 'POSTED'
                ), bal as (
                  select p.opening_balance + coalesce(d.total, 0) as balance
                  from parties p left join (select party_id, sum(amt) as total from delta group by party_id) d on d.party_id = p.id
                )
                select coalesce(sum(balance) filter (where balance > 0), 0), coalesce(-sum(balance) filter (where balance < 0), 0) from bal""",
                (rs, i) -> new BigDecimal[] {rs.getBigDecimal(1), rs.getBigDecimal(2)});
    }

    private long lowStockCount() {
        return jdbc.getJdbcTemplate().queryForObject("""
                select count(*) from products p join stock_balances b on b.product_id = p.id
                where p.minimum_stock > 0 and b.quantity <= p.minimum_stock""", Long.class);
    }

    private List<LowStockItem> lowStock() {
        return jdbc.getJdbcTemplate().query("""
                select p.id, p.product_code, p.product_name, p.unit, b.quantity, p.minimum_stock
                from products p join stock_balances b on b.product_id = p.id
                where p.minimum_stock > 0 and b.quantity <= p.minimum_stock
                order by b.quantity / p.minimum_stock, p.product_name limit 5""",
                (rs, i) -> new LowStockItem(rs.getLong("id"), rs.getString("product_code"), rs.getString("product_name"),
                        rs.getString("unit"), rs.getBigDecimal("quantity"), rs.getBigDecimal("minimum_stock")));
    }

    /** Latest posted documents of the types the caller may view (caller must also hold DASHBOARD.FINANCIAL). */
    private List<RecentTransaction> recent(boolean in, boolean out) {
        List<String> parts = new ArrayList<>();
        for (String type : List.of("IN", "OUT")) {
            if (type.equals("IN") ? in : out) {
                parts.add("(select * from material_transactions where transaction_type = '" + type + "' and status = 'POSTED'"
                        + " order by transaction_date desc, id desc limit 10)");
            }
        }
        return jdbc.getJdbcTemplate().query("""
                select r.id, r.transaction_type, r.transaction_number, r.transaction_date, pa.party_name, r.total_amount
                from (""" + String.join(" union all ", parts) + """
                ) r left join parties pa on pa.id = r.party_id
                order by r.transaction_date desc, r.id desc limit 10""",
                (rs, i) -> new RecentTransaction(rs.getLong("id"), rs.getString("transaction_type"), rs.getString("transaction_number"),
                        rs.getObject("transaction_date", LocalDate.class), rs.getString("party_name"), rs.getBigDecimal("total_amount")));
    }

    private static BigDecimal at(Map<String, Map<LocalDate, BigDecimal>> daily, String kind, LocalDate day) {
        return daily.getOrDefault(kind, Map.of()).getOrDefault(day, zero(kind));
    }

    private static BigDecimal periodSum(Map<String, Map<LocalDate, BigDecimal>> daily, String kind) {
        return daily.getOrDefault(kind, Map.of()).values().stream().reduce(zero(kind), BigDecimal::add);
    }

    private static BigDecimal zero(String kind) {
        return "EXP".equals(kind) ? MONEY_ZERO : TONS_ZERO;
    }
}
