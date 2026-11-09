package com.materialbook.inventory;

import com.materialbook.common.ApiException.NotFoundException;
import com.materialbook.common.PageResponse;
import com.materialbook.inventory.InventoryDtos.LedgerResponse;
import com.materialbook.inventory.InventoryDtos.LedgerRow;
import com.materialbook.inventory.InventoryDtos.StockRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the inventory module (current stock and per-product ledger). */
@Service
public class InventoryQueryService {
    private static final int MAX_PAGE_SIZE = 200;

    private final NamedParameterJdbcTemplate jdbc;

    public InventoryQueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageResponse<StockRow> stock(String q, Long categoryId, boolean lowStockOnly, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder(" where true");
        if (q != null && !q.isBlank()) {
            where.append(" and (lower(p.product_code) like :like or lower(p.product_name) like :like)");
            params.addValue("like", com.materialbook.common.LikePattern.contains(q));
        }
        if (categoryId != null) {
            where.append(" and p.category_id = :category");
            params.addValue("category", categoryId);
        }
        if (lowStockOnly) {
            where.append(" and p.minimum_stock > 0 and b.quantity <= p.minimum_stock");
        }
        String from = " from products p join stock_balances b on b.product_id = p.id"
                + " left join product_categories c on c.id = p.category_id" + where;
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int pageNo = Math.max(page, 0);
        long total = jdbc.queryForObject("select count(*)" + from, params, Long.class);
        List<StockRow> rows = jdbc.query("""
                select p.id, p.product_code, p.product_name, c.name as category_name, p.unit, b.quantity, p.minimum_stock,
                       (p.minimum_stock > 0 and b.quantity <= p.minimum_stock) as low_stock
                """ + from + " order by p.product_name, p.id limit :limit offset :offset",
                params.addValue("limit", pageSize).addValue("offset", (long) pageNo * pageSize),
                (rs, i) -> new StockRow(rs.getLong("id"), rs.getString("product_code"), rs.getString("product_name"),
                        rs.getString("category_name"), rs.getString("unit"), rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("minimum_stock"), rs.getBoolean("low_stock")));
        return new PageResponse<>(rows, pageNo, pageSize, total);
    }

    @Transactional(readOnly = true)
    public LedgerResponse ledger(Long productId, LocalDate from, LocalDate to, boolean newestFirst, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource("product", productId);
        Boolean exists = jdbc.queryForObject("select exists(select 1 from products where id = :product)", params, Boolean.class);
        if (!Boolean.TRUE.equals(exists)) {
            throw new NotFoundException("Material not found");
        }
        StringBuilder where = new StringBuilder(" where true");
        if (from != null) {
            where.append(" and l.movement_date >= :from");
            params.addValue("from", from);
        }
        if (to != null) {
            where.append(" and l.movement_date <= :to");
            params.addValue("to", to);
        }
        // Running balance is computed over the product's whole history, then filtered to the requested range.
        String ledger = """
                from (select m.id, m.movement_date, m.movement_type, m.quantity_delta, m.transaction_id, m.created_by,
                             sum(m.quantity_delta) over (order by m.movement_date, m.id) as balance
                      from stock_movements m where m.product_id = :product) l
                left join material_transactions t on t.id = l.transaction_id
                left join users u on u.id = l.created_by
                """ + where;
        BigDecimal opening = from == null ? BigDecimal.ZERO : jdbc.queryForObject(
                "select coalesce(sum(quantity_delta), 0) from stock_movements where product_id = :product and movement_date < :from",
                params, BigDecimal.class);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int pageNo = Math.max(page, 0);
        long total = jdbc.queryForObject("select count(*) " + ledger, params, Long.class);
        String direction = newestFirst ? "desc" : "asc";
        List<LedgerRow> rows = jdbc.query("""
                select l.movement_date, l.movement_type, l.quantity_delta, l.balance, l.transaction_id,
                       t.transaction_number, u.full_name
                """ + ledger + " order by l.movement_date " + direction + ", l.id " + direction + " limit :limit offset :offset",
                params.addValue("limit", pageSize).addValue("offset", (long) pageNo * pageSize),
                (rs, i) -> new LedgerRow(rs.getObject("movement_date", LocalDate.class),
                        MovementType.valueOf(rs.getString("movement_type")), rs.getBigDecimal("quantity_delta"),
                        rs.getBigDecimal("balance"), rs.getObject("transaction_id", Long.class),
                        rs.getString("transaction_number"), rs.getString("full_name")));
        return new LedgerResponse(opening, rows, pageNo, pageSize, total);
    }
}
