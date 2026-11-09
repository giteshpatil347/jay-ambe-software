package com.materialbook.reports;

import com.materialbook.common.ApiException.ValidationException;
import com.materialbook.reports.ReportDtos.CategoryTotal;
import com.materialbook.reports.ReportDtos.ExpenseRow;
import com.materialbook.reports.ReportDtos.ExpenseSummary;
import com.materialbook.reports.ReportDtos.GroupRow;
import com.materialbook.reports.ReportDtos.MaterialRow;
import com.materialbook.reports.ReportDtos.MaterialSummary;
import com.materialbook.reports.ReportDtos.ReportPage;
import com.materialbook.reports.ReportDtos.StockReportRow;
import com.materialbook.reports.ReportDtos.StockSummary;
import com.materialbook.reports.ReportDtos.UnitQuantity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read models for the reports. Everything is plain SQL over POSTED/CANCELLED documents; material items always filter
 * {@code superseded_at IS NULL} (edits keep old item rows). The {@code *Cells} methods return rows in export column order
 * and refuse to return more than {@value #EXPORT_ROW_CAP} rows.
 */
@Service
@Transactional(readOnly = true)
public class ReportQueryService {
    static final int EXPORT_ROW_CAP = 20_000;
    private static final int MAX_PAGE_SIZE = 200;
    private static final BigDecimal MONEY_ZERO = new BigDecimal("0.00");

    public enum GroupBy {
        PARTY("coalesce(party_id::text, '')", "coalesce(party_name, '(No party)')", "label"),
        PRODUCT("product_id::text", "product_name", "label"),
        DAY("d::text", "d::text", "k desc");

        final String key;
        final String label;
        final String order;

        GroupBy(String key, String label, String order) {
            this.key = key;
            this.label = label;
            this.order = order;
        }

        static GroupBy parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ValidationException("groupBy must be one of party, product, day");
            }
        }
    }

    private record Base(String cte, MapSqlParameterSource params) {}

    private final NamedParameterJdbcTemplate jdbc;

    public ReportQueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ───────────── material IN / OUT ─────────────

    /** CTE {@code base}: one row per active (non-superseded) item with its transaction, product and party. */
    private Base base(MaterialType type, ReportFilter f, boolean postedOnly) {
        f.requireValidRange();
        MapSqlParameterSource p = new MapSqlParameterSource("type", type.name());
        StringBuilder where = new StringBuilder();
        if (f.from() != null) {
            where.append(" and t.transaction_date >= :from");
            p.addValue("from", f.from());
        }
        if (f.to() != null) {
            where.append(" and t.transaction_date <= :to");
            p.addValue("to", f.to());
        }
        if (f.partyId() != null) {
            where.append(" and t.party_id = :party");
            p.addValue("party", f.partyId());
        }
        if (f.productId() != null) {
            where.append(" and i.product_id = :product");
            p.addValue("product", f.productId());
        }
        if (f.categoryId() != null) {
            where.append(" and pr.category_id = :category");
            p.addValue("category", f.categoryId());
        }
        if (postedOnly) {
            where.append(" and t.status = 'POSTED'");
        } else if (f.status() != null) {
            where.append(" and t.status = :status");
            p.addValue("status", f.status().name());
        }
        String cte = """
                with base as (
                  select t.id as tx_id, t.transaction_number as number, t.transaction_date as d, t.party_id, pa.party_name,
                         t.challan_number, t.vehicle_number, t.status, i.id as item_id, i.product_id, pr.product_code,
                         pr.product_name, pr.unit, i.quantity as qty, i.rate, i.amount
                  from material_transactions t
                  join material_transaction_items i on i.transaction_id = t.id and i.superseded_at is null
                  join products pr on pr.id = i.product_id
                  left join parties pa on pa.id = t.party_id
                  where t.transaction_type = :type""" + where + "\n)\n";
        return new Base(cte, p);
    }

    private static int pageSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    public ReportPage<MaterialRow, MaterialSummary> materialReport(MaterialType type, ReportFilter f, int page, int size) {
        Base b = base(type, f, false);
        int pageSize = pageSize(size);
        int pageNo = Math.max(page, 0);
        long total = jdbc.queryForObject(b.cte + "select count(distinct tx_id) from base", b.params, Long.class);
        b.params.addValue("limit", pageSize).addValue("offset", (long) pageNo * pageSize);
        List<MaterialRow> rows = jdbc.query(b.cte + """
                select tx_id, number, d, party_id, party_name, challan_number, vehicle_number, status,
                       count(*) as item_count, sum(amount) as amount
                from base
                group by tx_id, number, d, party_id, party_name, challan_number, vehicle_number, status
                order by d desc, tx_id desc limit :limit offset :offset""", b.params,
                (rs, i) -> new MaterialRow(rs.getLong("tx_id"), rs.getString("number"), rs.getObject("d", LocalDate.class),
                        (Long) rs.getObject("party_id"), rs.getString("party_name"), rs.getString("challan_number"),
                        rs.getString("vehicle_number"), rs.getString("status"), rs.getInt("item_count"),
                        rs.getBigDecimal("amount"), List.of()));
        if (!rows.isEmpty()) {
            // quantity per unit of each entry on this page (units are never added together)
            Map<Long, List<UnitQuantity>> perEntry = new LinkedHashMap<>();
            b.params.addValue("txIds", rows.stream().map(MaterialRow::id).toList());
            jdbc.query(b.cte + "select tx_id, unit, sum(qty) as q from base where tx_id in (:txIds) group by tx_id, unit order by unit",
                    b.params, rs -> {
                        perEntry.computeIfAbsent(rs.getLong("tx_id"), k -> new ArrayList<>())
                                .add(new UnitQuantity(rs.getString("unit"), rs.getBigDecimal("q")));
                    });
            rows = rows.stream().map(r -> new MaterialRow(r.id(), r.transactionNumber(), r.transactionDate(), r.partyId(), r.partyName(),
                    r.challanNumber(), r.vehicleNumber(), r.status(), r.itemCount(), r.amount(),
                    perEntry.getOrDefault(r.id(), List.of()))).toList();
        }
        return new ReportPage<>(rows, pageNo, pageSize, total, materialSummary(type, f));
    }

    /** Aggregated rows over POSTED documents only; the status filter is ignored. */
    public ReportPage<GroupRow, MaterialSummary> materialGrouped(MaterialType type, ReportFilter f, GroupBy by, int page, int size) {
        Base b = base(type, f, true);
        String cte = b.cte + ", g as (select base.*, " + by.key + " as k, " + by.label + " as label from base)\n";
        int pageSize = pageSize(size);
        int pageNo = Math.max(page, 0);
        long total = jdbc.queryForObject(cte + "select count(distinct k) from g", b.params, Long.class);
        b.params.addValue("limit", pageSize).addValue("offset", (long) pageNo * pageSize);
        List<GroupRow> heads = jdbc.query(cte + "select k, label, count(distinct tx_id) as txns, sum(amount) as amount from g"
                + " group by k, label order by " + by.order + ", k limit :limit offset :offset", b.params,
                (rs, i) -> new GroupRow(rs.getString("k"), rs.getString("label"), rs.getLong("txns"), rs.getBigDecimal("amount"), null));
        Map<String, List<UnitQuantity>> quantities = new LinkedHashMap<>();
        if (!heads.isEmpty()) {
            b.params.addValue("keys", heads.stream().map(GroupRow::key).toList());
            jdbc.query(cte + "select k, unit, sum(qty) as q from g where k in (:keys) group by k, unit order by unit", b.params,
                    rs -> {
                        quantities.computeIfAbsent(rs.getString("k"), k -> new ArrayList<>())
                                .add(new UnitQuantity(rs.getString("unit"), rs.getBigDecimal("q")));
                    });
        }
        List<GroupRow> rows = heads.stream().map(h -> new GroupRow(h.key(), h.label(), h.transactionCount(), h.amount(),
                quantities.getOrDefault(h.key(), List.of()))).toList();
        return new ReportPage<>(rows, pageNo, pageSize, total, materialSummary(type, f));
    }

    public MaterialSummary materialSummary(MaterialType type, ReportFilter f) {
        Base b = base(type, f, false);
        List<UnitQuantity> units = new ArrayList<>();
        BigDecimal[] amount = {MONEY_ZERO};
        jdbc.query(b.cte + "select unit, sum(qty) as q, sum(amount) as a from base where status = 'POSTED' group by unit order by unit",
                b.params, rs -> {
                    units.add(new UnitQuantity(rs.getString("unit"), rs.getBigDecimal("q")));
                    amount[0] = amount[0].add(rs.getBigDecimal("a"));
                });
        long count = jdbc.queryForObject(b.cte + "select count(distinct tx_id) from base where status = 'POSTED'", b.params, Long.class);
        return new MaterialSummary(count, amount[0], units);
    }

    /** One row per item line: number, date, party, challan, vehicle, status, product, unit, quantity, rate, amount. */
    public List<List<Object>> materialLineCells(MaterialType type, ReportFilter f) {
        Base b = base(type, f, false);
        b.params.addValue("limit", EXPORT_ROW_CAP + 1);
        return capped(jdbc.query(b.cte + "select * from base order by d, tx_id, item_id limit :limit", b.params,
                (rs, i) -> List.<Object>of(rs.getString("number"), rs.getObject("d", LocalDate.class), orBlank(rs.getString("party_name")),
                        orBlank(rs.getString("challan_number")), orBlank(rs.getString("vehicle_number")), rs.getString("status"),
                        rs.getString("product_name"), rs.getString("unit"), rs.getBigDecimal("qty"), rs.getBigDecimal("rate"),
                        rs.getBigDecimal("amount"))));
    }

    // ───────────── expenses ─────────────

    private String expenseWhere(ReportFilter f, MapSqlParameterSource p) {
        f.requireValidRange();
        StringBuilder where = new StringBuilder(" where true");
        if (f.from() != null) {
            where.append(" and e.expense_date >= :from");
            p.addValue("from", f.from());
        }
        if (f.to() != null) {
            where.append(" and e.expense_date <= :to");
            p.addValue("to", f.to());
        }
        if (f.categoryId() != null) {
            where.append(" and e.category_id = :category");
            p.addValue("category", f.categoryId());
        }
        if (f.status() != null) {
            where.append(" and e.status = :status");
            p.addValue("status", f.status().name());
        }
        return where.toString();
    }

    private static final String EXPENSE_FROM = " from expenses e join expense_categories c on c.id = e.category_id";

    public ReportPage<ExpenseRow, ExpenseSummary> expenseReport(ReportFilter f, int page, int size) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String where = expenseWhere(f, p);
        int pageSize = pageSize(size);
        int pageNo = Math.max(page, 0);
        long total = jdbc.queryForObject("select count(*)" + EXPENSE_FROM + where, p, Long.class);
        p.addValue("limit", pageSize).addValue("offset", (long) pageNo * pageSize);
        List<ExpenseRow> rows = jdbc.query("""
                select e.id, e.expense_number, e.expense_date, e.category_id, c.category_name, e.amount, e.paid_to,
                       e.payment_mode, e.status""" + EXPENSE_FROM + where
                + " order by e.expense_date desc, e.id desc limit :limit offset :offset", p,
                (rs, i) -> new ExpenseRow(rs.getLong("id"), rs.getString("expense_number"), rs.getObject("expense_date", LocalDate.class),
                        rs.getLong("category_id"), rs.getString("category_name"), rs.getBigDecimal("amount"), rs.getString("paid_to"),
                        rs.getString("payment_mode"), rs.getString("status")));
        return new ReportPage<>(rows, pageNo, pageSize, total, expenseSummary(f));
    }

    /** POSTED expenses only, grouped by category (largest first). */
    public ExpenseSummary expenseSummary(ReportFilter f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String where = expenseWhere(f, p) + " and e.status = 'POSTED'";
        List<CategoryTotal> byCategory = jdbc.query("select c.id, c.category_name, count(*) as n, sum(e.amount) as a" + EXPENSE_FROM
                + where + " group by c.id, c.category_name order by a desc, c.category_name", p,
                (rs, i) -> new CategoryTotal(rs.getLong("id"), rs.getString("category_name"), rs.getLong("n"), rs.getBigDecimal("a")));
        return new ExpenseSummary(byCategory.stream().mapToLong(CategoryTotal::count).sum(),
                byCategory.stream().map(CategoryTotal::amount).reduce(MONEY_ZERO, BigDecimal::add), byCategory);
    }

    /** number, date, category, paid to, mode, status, remarks, amount. */
    public List<List<Object>> expenseCells(ReportFilter f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String where = expenseWhere(f, p);
        p.addValue("limit", EXPORT_ROW_CAP + 1);
        return capped(jdbc.query("select e.expense_number, e.expense_date, c.category_name, e.paid_to, e.payment_mode,"
                + " e.status, e.remarks, e.amount" + EXPENSE_FROM + where + " order by e.expense_date, e.id limit :limit", p,
                (rs, i) -> List.<Object>of(rs.getString("expense_number"), rs.getObject("expense_date", LocalDate.class),
                        rs.getString("category_name"), orBlank(rs.getString("paid_to")), orBlank(rs.getString("payment_mode")),
                        rs.getString("status"), orBlank(rs.getString("remarks")),
                        rs.getBigDecimal("amount"))));
    }

    // ───────────── stock ─────────────

    private static final String STOCK_CTE_CURRENT = "select p.id, p.product_code, p.product_name, c.name as category_name, p.unit,"
            + " b.quantity, p.minimum_stock from products p join stock_balances b on b.product_id = p.id"
            + " left join product_categories c on c.id = p.category_id where true";
    private static final String STOCK_CTE_AS_OF = "select p.id, p.product_code, p.product_name, c.name as category_name, p.unit,"
            + " coalesce(m.q, 0.000) as quantity, p.minimum_stock from products p"
            + " left join (select product_id, sum(quantity_delta) as q from stock_movements where movement_date <= :asOf group by product_id) m"
            + " on m.product_id = p.id left join product_categories c on c.id = p.category_id where true";

    private Base stockBase(StockFilter f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder cte = new StringBuilder("with s as (");
        if (f.asOf() == null) {
            cte.append(STOCK_CTE_CURRENT);
        } else {
            cte.append(STOCK_CTE_AS_OF);
            p.addValue("asOf", f.asOf());
        }
        if (f.categoryId() != null) {
            cte.append(" and p.category_id = :category");
            p.addValue("category", f.categoryId());
        }
        if (f.productId() != null) {
            cte.append(" and p.id = :product");
            p.addValue("product", f.productId());
        }
        cte.append("), f as (select s.*, (minimum_stock > 0 and quantity <= minimum_stock) as low from s")
                .append(f.lowOnly() ? " where minimum_stock > 0 and quantity <= minimum_stock" : "").append(")\n");
        return new Base(cte.toString(), p);
    }

    public ReportPage<StockReportRow, StockSummary> stockReport(StockFilter f, int page, int size) {
        Base b = stockBase(f);
        int pageSize = pageSize(size);
        int pageNo = Math.max(page, 0);
        StockSummary summary = jdbc.queryForObject(b.cte + "select count(*) as n, count(*) filter (where low) as l from f", b.params,
                (rs, i) -> new StockSummary(rs.getLong("n"), rs.getLong("l")));
        b.params.addValue("limit", pageSize).addValue("offset", (long) pageNo * pageSize);
        List<StockReportRow> rows = jdbc.query(b.cte + "select * from f order by product_name, id limit :limit offset :offset", b.params,
                (rs, i) -> new StockReportRow(rs.getLong("id"), rs.getString("product_code"), rs.getString("product_name"),
                        rs.getString("category_name"), rs.getString("unit"), rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("minimum_stock"), rs.getBoolean("low")));
        return new ReportPage<>(rows, pageNo, pageSize, summary.productCount(), summary);
    }

    public long lowStockCount(StockFilter f) {
        Base b = stockBase(f);
        return jdbc.queryForObject(b.cte + "select count(*) filter (where low) from f", b.params, Long.class);
    }

    /** code, product, category, unit, quantity, minimum, status (LOW/OK). */
    public List<List<Object>> stockCells(StockFilter f) {
        Base b = stockBase(f);
        b.params.addValue("limit", EXPORT_ROW_CAP + 1);
        return capped(jdbc.query(b.cte + "select * from f order by product_name, id limit :limit", b.params,
                (rs, i) -> List.<Object>of(rs.getString("product_code"), rs.getString("product_name"), orBlank(rs.getString("category_name")),
                        rs.getString("unit"), rs.getBigDecimal("quantity"), rs.getBigDecimal("minimum_stock"),
                        rs.getBoolean("low") ? "LOW" : "OK")));
    }

    // ───────────── helpers ─────────────

    private static String orBlank(String s) {
        return s == null ? "" : s;
    }

    private static List<List<Object>> capped(List<List<Object>> rows) {
        if (rows.size() > EXPORT_ROW_CAP) {
            throw new ValidationException("Export has more than " + EXPORT_ROW_CAP + " rows; narrow the filters (dates, party, material)");
        }
        return rows;
    }
}
