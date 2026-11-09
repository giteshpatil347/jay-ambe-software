package com.materialbook.inventory;

import com.materialbook.audit.service.AuditService;
import com.materialbook.common.ApiException.ConflictException;
import com.materialbook.common.ApiException.InsufficientStockException;
import com.materialbook.common.ApiException.ValidationException;
import com.materialbook.common.GlobalExceptionHandler.FieldError;
import com.materialbook.inventory.InventoryDtos.OpeningItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ONLY writer of {@code stock_balances} / {@code stock_movements}. Every method joins the caller's transaction
 * (REQUIRED), so a failure rolls back the caller's own writes too. The authenticated user (JWT sub) is stored as created_by.
 *
 * <p>Concurrency: affected balance rows are locked in ONE statement ({@code ORDER BY product_id FOR UPDATE}), so
 * transactions touching overlapping products lock in the same order (no deadlocks) and stock can never go negative.
 *
 * <p>API for the material module:
 * <ul>
 *   <li>{@code post(MovementType.IN|OUT, transactionId, date, List<Line>)} - one movement row per line (signed delta);
 *       duplicate productIds are summed for the balance check. OUT with insufficient stock throws
 *       {@code InsufficientStockException} (409 INSUFFICIENT_STOCK, fieldErrors = one entry per short product).
 *       The transaction and its items (referenced by {@code Line.transactionItemId}) must already be flushed.</li>
 *   <li>{@code reverse(transactionId, date)} - for every item of the transaction whose movements do not net to zero,
 *       inserts the opposite REVERSAL_IN/REVERSAL_OUT row; originals are untouched. Calling it again is a no-op.
 *       Reversing an IN that would make stock negative throws InsufficientStockException. For an edit call
 *       {@code replace} instead (net-checked) when the transaction is being re-posted.</li>
 *   <li>{@code replace(type, transactionId, oldDate, newDate, List<Line>)} - atomic edit: reversals + new movements,
 *       stock checked on the net effect per product (see method doc).</li>
 * </ul>
 */
@Service
public class StockService {
    /** One line to post; {@code transactionItemId} links the movement to its item (used for reversal bookkeeping). */
    public record Line(Long productId, Long transactionItemId, BigDecimal quantity) {}

    private record Movement(Long productId, Long transactionItemId, MovementType type, BigDecimal delta, LocalDate date) {}

    private record Label(String code, String name) {
        String text() { return code + " - " + name; }
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;

    public StockService(NamedParameterJdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public void post(MovementType type, Long transactionId, LocalDate date, List<Line> lines) {
        if (type != MovementType.IN && type != MovementType.OUT) {
            throw new IllegalArgumentException("post() accepts IN or OUT only");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("lines must not be empty");
        }
        List<Movement> movements = newMovements(type, date, lines);
        apply(transactionId, movements, lockBalances(movements.stream().map(Movement::productId).toList()));
    }

    /**
     * Edit of a posted transaction: reverses whatever the transaction currently holds (dated {@code oldDate}) and posts
     * {@code newLines} (dated {@code newDate}) as ONE atomic stock change. Stock is checked against the NET effect per
     * product, so e.g. raising an IN from 12.5 to 20 after 5 were dispatched is allowed (net +7.5), while lowering it
     * below what has already left is rejected. The ledger stays append-only: reversal rows plus new rows are inserted.
     */
    @Transactional
    public void replace(MovementType type, Long transactionId, LocalDate oldDate, LocalDate newDate, List<Line> newLines) {
        if (type != MovementType.IN && type != MovementType.OUT) {
            throw new IllegalArgumentException("replace() accepts IN or OUT only");
        }
        if (newLines == null || newLines.isEmpty()) {
            throw new IllegalArgumentException("lines must not be empty");
        }
        List<Long> existing = jdbc.queryForList("select distinct product_id from stock_movements where transaction_id = :tx",
                new MapSqlParameterSource("tx", transactionId), Long.class);
        List<Movement> added = newMovements(type, newDate, newLines);
        List<Long> productIds = java.util.stream.Stream.concat(existing.stream(), added.stream().map(Movement::productId))
                .distinct().toList();
        Map<Long, BigDecimal> balances = lockBalances(productIds);
        List<Movement> movements = new ArrayList<>(pendingReversals(transactionId, oldDate));
        movements.addAll(added);
        apply(transactionId, movements, balances);
    }

    private static List<Movement> newMovements(MovementType type, LocalDate date, List<Line> lines) {
        List<Movement> movements = new ArrayList<>();
        for (Line line : lines) {
            if (line.quantity() == null || line.quantity().signum() <= 0) {
                throw new ValidationException("Quantity must be greater than zero");
            }
            movements.add(new Movement(line.productId(), line.transactionItemId(), type,
                    type == MovementType.IN ? line.quantity() : line.quantity().negate(), date));
        }
        return movements;
    }

    @Transactional
    public void reverse(Long transactionId, LocalDate date) {
        MapSqlParameterSource tx = new MapSqlParameterSource("tx", transactionId);
        List<Long> productIds = jdbc.queryForList(
                "select distinct product_id from stock_movements where transaction_id = :tx", tx, Long.class);
        if (productIds.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> balances = lockBalances(productIds);
        // Read after locking: a concurrent reverse of the same transaction has committed (or is blocked) by now.
        List<Movement> reversals = pendingReversals(transactionId, date);
        if (!reversals.isEmpty()) {
            apply(transactionId, reversals, balances);
        }
    }

    /** Opposite movements for every item of the transaction whose movements do not net to zero. Call with balances locked. */
    private List<Movement> pendingReversals(Long transactionId, LocalDate date) {
        return jdbc.query("""
                select transaction_item_id, product_id, sum(quantity_delta) as net
                from stock_movements where transaction_id = :tx
                group by transaction_item_id, product_id
                having sum(quantity_delta) <> 0
                """, new MapSqlParameterSource("tx", transactionId), (rs, i) -> {
            BigDecimal delta = rs.getBigDecimal("net").negate();
            return new Movement(rs.getObject("product_id", Long.class), rs.getObject("transaction_item_id", Long.class),
                    delta.signum() > 0 ? MovementType.REVERSAL_IN : MovementType.REVERSAL_OUT, delta, date);
        });
    }

    /**
     * Opening stock for products that have never had a movement; creates OPENING rows without a transaction.
     * Used by the inventory endpoint only.
     */
    @Transactional
    public void recordOpening(LocalDate date, List<OpeningItem> items) {
        Map<Long, BigDecimal> quantities = new TreeMap<>();
        for (OpeningItem item : items) {
            if (quantities.put(item.productId(), item.quantity()) != null) {
                throw new ValidationException("Duplicate productId in request: " + item.productId());
            }
        }
        Map<Long, BigDecimal> balances = lockBalances(quantities.keySet());
        List<Long> used = jdbc.queryForList("select distinct product_id from stock_movements where product_id in (:ids)",
                new MapSqlParameterSource("ids", quantities.keySet()), Long.class);
        if (!used.isEmpty()) {
            throw new ConflictException("Opening stock is only allowed for materials without stock movements: "
                    + describe(used).values().stream().map(Label::text).collect(Collectors.joining(", ")));
        }
        apply(null, quantities.entrySet().stream()
                .map(e -> new Movement(e.getKey(), null, MovementType.OPENING, e.getValue(), date)).toList(), balances);
        quantities.forEach((productId, quantity) -> audit.log("OPENING_STOCK", "INVENTORY", productId, null,
                Map.of("productId", productId, "quantity", quantity, "date", date)));
    }

    /** Locks the balance rows of the given products (deterministic order) and returns current quantities. */
    private Map<Long, BigDecimal> lockBalances(Collection<Long> productIds) {
        Map<Long, BigDecimal> balances = new TreeMap<>();
        jdbc.query("select product_id, quantity from stock_balances where product_id in (:ids) order by product_id for update",
                new MapSqlParameterSource("ids", productIds),
                rs -> { balances.put(rs.getLong("product_id"), rs.getBigDecimal("quantity")); });
        for (Long id : productIds) {
            if (!balances.containsKey(id)) {
                throw new ValidationException("Unknown productId: " + id);
            }
        }
        return balances;
    }

    private void apply(Long transactionId, List<Movement> movements, Map<Long, BigDecimal> balances) {
        Map<Long, BigDecimal> net = new TreeMap<>();
        movements.forEach(m -> net.merge(m.productId(), m.delta(), BigDecimal::add));

        List<Long> shortProducts = net.entrySet().stream()
                .filter(e -> balances.get(e.getKey()).add(e.getValue()).signum() < 0).map(Map.Entry::getKey).toList();
        if (!shortProducts.isEmpty()) {
            throw insufficient(shortProducts, balances, net);
        }

        long userId = currentUserId();
        jdbc.batchUpdate("""
                insert into stock_movements(product_id, transaction_id, transaction_item_id, movement_type, quantity_delta, movement_date, created_by)
                values (:product, :tx, :item, :type, :delta, :date, :user)
                """, movements.stream().map(m -> new MapSqlParameterSource()
                .addValue("product", m.productId()).addValue("tx", transactionId).addValue("item", m.transactionItemId())
                .addValue("type", m.type().name()).addValue("delta", m.delta()).addValue("date", m.date())
                .addValue("user", userId)).toArray(MapSqlParameterSource[]::new));
        jdbc.batchUpdate("update stock_balances set quantity = quantity + :delta, updated_at = now() where product_id = :product",
                net.entrySet().stream().map(e -> new MapSqlParameterSource("product", e.getKey()).addValue("delta", e.getValue()))
                        .toArray(MapSqlParameterSource[]::new));
    }

    private InsufficientStockException insufficient(List<Long> productIds, Map<Long, BigDecimal> balances, Map<Long, BigDecimal> net) {
        Map<Long, Label> labels = describe(productIds);
        List<FieldError> errors = productIds.stream().map(id -> new FieldError(labels.get(id).code(),
                "Insufficient stock for " + labels.get(id).text() + ": available " + balances.get(id).setScale(3).toPlainString()
                        + ", required " + net.get(id).negate().setScale(3).toPlainString())).toList();
        return new InsufficientStockException(errors.stream().map(FieldError::message).collect(Collectors.joining("; ")), errors);
    }

    private Map<Long, Label> describe(Collection<Long> productIds) {
        Map<Long, Label> labels = new TreeMap<>();
        jdbc.query("select id, product_code, product_name from products where id in (:ids)",
                new MapSqlParameterSource("ids", productIds),
                rs -> { labels.put(rs.getLong("id"), new Label(rs.getString("product_code"), rs.getString("product_name"))); });
        return labels;
    }

    private static long currentUserId() {
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof Jwt jwt) {
            return Long.parseLong(jwt.getSubject());
        }
        throw new IllegalStateException("Stock movements require an authenticated user");
    }
}
