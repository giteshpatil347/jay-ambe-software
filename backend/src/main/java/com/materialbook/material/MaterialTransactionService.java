package com.materialbook.material;

import com.materialbook.audit.service.AuditService;
import com.materialbook.common.ApiException.ConflictException;
import com.materialbook.common.ApiException.NotFoundException;
import com.materialbook.common.ApiException.ValidationException;
import com.materialbook.inventory.StockService;
import com.materialbook.inventory.StockService.Line;
import com.materialbook.material.MaterialDtos.ItemInput;
import com.materialbook.material.MaterialDtos.ItemResponse;
import com.materialbook.material.MaterialDtos.TransactionDetail;
import com.materialbook.material.MaterialDtos.TransactionPage;
import com.materialbook.material.MaterialDtos.TransactionRequest;
import com.materialbook.material.MaterialDtos.QuantityByUnit;
import com.materialbook.material.MaterialDtos.TransactionRow;
import com.materialbook.material.UnitConverter.UnitConversionException;
import com.materialbook.parties.PartyType;
import com.materialbook.products.ProductUnit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Material IN / OUT, shared by both directions (parameterised by {@link TransactionType}).
 * <ul>
 *   <li>Quantities are entered in TON/KG/PIECE but stored, stocked, totalled and reported in the PRODUCT unit
 *       ({@link UnitConverter}); the rate is always per product unit.</li>
 *   <li>Number: {@code MIN|MOUT-<UTC year of creation>-NNNNN} from {@code next_document_number}.</li>
 *   <li>Stock is only touched through {@link StockService}, in the same transaction, so an insufficient-stock 409
 *       rolls back the header, items and the consumed number.</li>
 *   <li>Edit = mark old items {@code superseded_at},
 *       insert the new items, then {@code StockService.replace} (net-checked reversal + repost). Items with movements are never deleted; every read and total only
 *       uses items where {@code superseded_at IS NULL}.</li>
 *   <li>Cancel = reverse the movements, status CANCELLED. Cancelled documents are immutable.</li>
 *   <li>The header row is locked ({@code FOR UPDATE}) for edit/cancel, then the version is compared.</li>
 * </ul>
 */
@Service
public class MaterialTransactionService {
    private static final BigDecimal MAX_TOTAL = new BigDecimal("9999999999.99");
    private static final String ACTIVE = "ACTIVE";

    private final NamedParameterJdbcTemplate jdbc;
    private final StockService stock;
    private final AuditService audit;

    public MaterialTransactionService(NamedParameterJdbcTemplate jdbc, StockService stock, AuditService audit) {
        this.jdbc = jdbc;
        this.stock = stock;
        this.audit = audit;
    }

    @Transactional
    public TransactionDetail create(TransactionType type, Long userId, TransactionRequest r) {
        List<BigDecimal> quantities = validate(type, r);
        String number = jdbc.getJdbcTemplate().queryForObject("select next_document_number(?, ?)", String.class,
                type.numberPrefix, Year.now(ZoneOffset.UTC).getValue());
        Long id = jdbc.queryForObject("""
                insert into material_transactions(transaction_number, transaction_type, transaction_date, party_id,
                    challan_number, vehicle_number, driver_name, remarks, created_by)
                values (:number, :type, :date, :party, :challan, :vehicle, :driver, :remarks, :user) returning id
                """, headerParams(type, r).addValue("number", number).addValue("user", userId), Long.class);
        List<Line> lines = insertItems(id, r.items(), quantities);
        refreshTotal(id);
        stock.post(type.movement, id, r.transactionDate(), lines);
        TransactionDetail created = detail(type, id);
        audit.log("CREATE", type.module(), id, null, created);
        return created;
    }

    @Transactional
    public TransactionDetail update(TransactionType type, Long userId, Long id, TransactionRequest r) {
        if (r.version() == null) {
            throw new ValidationException("version is required");
        }
        Locked locked = lock(type, id);
        if (locked.status() == TransactionStatus.CANCELLED) {
            throw new ConflictException("Cancelled transactions cannot be edited");
        }
        if (locked.version() != r.version()) {
            throw new ConflictException("The record was modified by someone else; reload and retry");
        }
        List<BigDecimal> quantities = validate(type, r);
        TransactionDetail before = detail(type, id);
        jdbc.update("update material_transaction_items set superseded_at = now() where transaction_id = :id and superseded_at is null",
                new MapSqlParameterSource("id", id));
        List<Line> lines = insertItems(id, r.items(), quantities);
        stock.replace(type.movement, id, locked.date(), r.transactionDate(), lines);
        jdbc.update("""
                update material_transactions set transaction_date = :date, party_id = :party, challan_number = :challan,
                    vehicle_number = :vehicle, driver_name = :driver, remarks = :remarks, updated_by = :user,
                    version = version + 1 where id = :id
                """, headerParams(type, r).addValue("user", userId).addValue("id", id));
        refreshTotal(id);
        TransactionDetail after = detail(type, id);
        audit.log("UPDATE", type.module(), id, before, after);
        return after;
    }

    @Transactional
    public TransactionDetail cancel(TransactionType type, Long userId, Long id, String reason) {
        Locked locked = lock(type, id);
        if (locked.status() == TransactionStatus.CANCELLED) {
            throw new ConflictException("Transaction is already cancelled");
        }
        TransactionDetail before = detail(type, id);
        stock.reverse(id, locked.date());
        jdbc.update("""
                update material_transactions set status = 'CANCELLED', cancelled_by = :user, cancelled_at = now(),
                    cancellation_reason = :reason, updated_by = :user, version = version + 1 where id = :id
                """, new MapSqlParameterSource("user", userId).addValue("reason", reason.trim()).addValue("id", id));
        TransactionDetail after = detail(type, id);
        audit.log("CANCEL", type.module(), id, before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public TransactionDetail get(TransactionType type, Long id) {
        return detail(type, id);
    }

    @Transactional(readOnly = true)
    public TransactionPage list(TransactionType type, MaterialFilter f, int page, int size) {
        MapSqlParameterSource p = new MapSqlParameterSource("type", type.name());
        StringBuilder where = new StringBuilder(" where t.transaction_type = :type");
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
            where.append(" and exists (select 1 from material_transaction_items i where i.transaction_id = t.id"
                    + " and i.superseded_at is null and i.product_id = :product)");
            p.addValue("product", f.productId());
        }
        if (f.status() != null) {
            where.append(" and t.status = :status");
            p.addValue("status", f.status().name());
        }
        if (f.q() != null && !f.q().isBlank()) {
            where.append(" and (position(lower(:q) in lower(t.transaction_number)) > 0"
                    + " or position(lower(:q) in lower(coalesce(t.challan_number, ''))) > 0"
                    + " or position(lower(:q) in lower(coalesce(t.vehicle_number, ''))) > 0)");
            p.addValue("q", f.q().trim());
        }
        long total = jdbc.queryForObject("select count(*) from material_transactions t" + where, p, Long.class);
        BigDecimal postedTotal = jdbc.queryForObject("select coalesce(sum(t.total_amount), 0) from material_transactions t"
                + where + " and t.status = 'POSTED'", p, BigDecimal.class);
        List<TransactionRow> rows = jdbc.query("""
                select t.id, t.transaction_number, t.transaction_date, t.party_id, p.party_name, t.total_amount, t.status,
                       u.full_name as created_by_name
                from material_transactions t
                left join parties p on p.id = t.party_id
                join users u on u.id = t.created_by
                """ + where + " order by t.transaction_date desc, t.id desc limit :limit offset :offset",
                p.addValue("limit", size).addValue("offset", (long) page * size),
                (rs, i) -> new TransactionRow(rs.getLong("id"), rs.getString("transaction_number"),
                        rs.getObject("transaction_date", LocalDate.class), rs.getObject("party_id", Long.class),
                        rs.getString("party_name"), 0, List.of(), List.of(), rs.getBigDecimal("total_amount"), rs.getString("status"),
                        rs.getString("created_by_name")));
        return new TransactionPage(withProducts(rows), page, size, total, postedTotal);
    }

    private List<TransactionRow> withProducts(List<TransactionRow> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        Map<Long, List<String>> names = new HashMap<>();
        jdbc.query("""
                select i.transaction_id, pr.product_name
                from material_transaction_items i join products pr on pr.id = i.product_id
                where i.transaction_id in (:ids) and i.superseded_at is null order by i.id
                """, new MapSqlParameterSource("ids", rows.stream().map(TransactionRow::id).toList()),
                rs -> { names.computeIfAbsent(rs.getLong("transaction_id"), k -> new ArrayList<>()).add(rs.getString("product_name")); });
        // total quantity per unit of each entry (active items only)
        Map<Long, List<QuantityByUnit>> quantities = new HashMap<>();
        jdbc.query("""
                select i.transaction_id, pr.unit, sum(i.quantity) as q
                from material_transaction_items i join products pr on pr.id = i.product_id
                where i.transaction_id in (:ids) and i.superseded_at is null
                group by i.transaction_id, pr.unit order by pr.unit
                """, new MapSqlParameterSource("ids", rows.stream().map(TransactionRow::id).toList()),
                rs -> { quantities.computeIfAbsent(rs.getLong("transaction_id"), k -> new ArrayList<>())
                        .add(new QuantityByUnit(rs.getString("unit"), rs.getBigDecimal("q"))); });
        return rows.stream().map(row -> {
            List<String> all = names.getOrDefault(row.id(), List.of());
            return new TransactionRow(row.id(), row.transactionNumber(), row.transactionDate(), row.partyId(), row.partyName(),
                    all.size(), all.stream().limit(2).toList(), quantities.getOrDefault(row.id(), List.of()), row.totalAmount(),
                    row.status(), row.createdByName());
        }).toList();
    }

    // ---- validation ----

    /** Validates the request and returns each item's quantity converted to its product's unit (same order as items). */
    private List<BigDecimal> validate(TransactionType type, TransactionRequest r) {
        if (r.partyId() == null) {
            if (type == TransactionType.IN) {
                throw new ValidationException("partyId is required for Material IN");
            }
        } else {
            requireParty(type, r.partyId());
        }
        Map<Long, ProductUnit> units = requireActiveProducts(r.items().stream().map(ItemInput::productId).collect(Collectors.toSet()));
        List<BigDecimal> quantities = convertQuantities(r.items(), units);
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < quantities.size(); i++) {
            total = total.add(quantities.get(i).multiply(rate(r.items().get(i))).setScale(2, RoundingMode.HALF_UP));
        }
        if (total.compareTo(MAX_TOTAL) > 0) {
            throw new ValidationException("Total amount is too large");
        }
        return quantities;
    }

    /** Entered unit -> product unit; failures are 422s pinned to {@code items[i].unit|quantity}. */
    private static List<BigDecimal> convertQuantities(List<ItemInput> items, Map<Long, ProductUnit> units) {
        List<BigDecimal> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ItemInput item = items.get(i);
            ProductUnit entered;
            try {
                entered = item.unit() == null || item.unit().isBlank() ? null : ProductUnit.valueOf(item.unit().trim());
            } catch (IllegalArgumentException e) {
                throw new ItemValidationException("Unknown unit: " + item.unit(), "items[" + i + "].unit");
            }
            BigDecimal q;
            try {
                q = UnitConverter.convert(item.quantity(), entered, units.get(item.productId()));
            } catch (UnitConversionException e) {
                throw new ItemValidationException(e.getMessage(), "items[" + i + "].quantity");
            }
            if (q.precision() - q.scale() > 11) {
                throw new ItemValidationException("Quantity is too large for the material unit", "items[" + i + "].quantity");
            }
            out.add(q);
        }
        return out;
    }

    /** 422 whose single fieldError points at one item property. */
    static final class ItemValidationException extends com.materialbook.common.ApiException {
        private final List<com.materialbook.common.GlobalExceptionHandler.FieldError> fieldErrors;

        ItemValidationException(String message, String field) {
            super("VALIDATION_ERROR", org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, message);
            this.fieldErrors = List.of(new com.materialbook.common.GlobalExceptionHandler.FieldError(field, message));
        }

        @Override
        public List<com.materialbook.common.GlobalExceptionHandler.FieldError> getFieldErrors() { return fieldErrors; }
    }

    private void requireParty(TransactionType type, Long partyId) {
        Map<String, Object> row = jdbc.getJdbcTemplate().queryForList("select party_type, status from parties where id = ?", partyId)
                .stream().findFirst().orElseThrow(() -> new ValidationException("Unknown partyId: " + partyId));
        if (!ACTIVE.equals(row.get("status"))) {
            throw new ValidationException("Party is inactive");
        }
        if (!type.allowedPartyTypes.contains(PartyType.valueOf((String) row.get("party_type")))) {
            throw new ValidationException(type == TransactionType.IN
                    ? "Material IN requires a supplier party" : "Material OUT requires a client party");
        }
    }

    private Map<Long, ProductUnit> requireActiveProducts(Set<Long> productIds) {
        Map<Long, String> statuses = new HashMap<>();
        Map<Long, ProductUnit> units = new HashMap<>();
        jdbc.query("select id, status, unit from products where id in (:ids)", new MapSqlParameterSource("ids", productIds),
                rs -> { statuses.put(rs.getLong("id"), rs.getString("status")); units.put(rs.getLong("id"), ProductUnit.valueOf(rs.getString("unit"))); });
        for (Long id : productIds) {
            if (!statuses.containsKey(id)) {
                throw new ValidationException("Unknown productId: " + id);
            }
            if (!ACTIVE.equals(statuses.get(id))) {
                throw new ValidationException("Material is inactive: " + id);
            }
        }
        return units;
    }

    // ---- persistence helpers ----

    private record Locked(TransactionStatus status, long version, LocalDate date) {}

    private Locked lock(TransactionType type, Long id) {
        return jdbc.query("""
                select status, version, transaction_date from material_transactions
                where id = :id and transaction_type = :type for update
                """, new MapSqlParameterSource("id", id).addValue("type", type.name()),
                (rs, i) -> new Locked(TransactionStatus.valueOf(rs.getString("status")), rs.getLong("version"),
                        rs.getObject("transaction_date", LocalDate.class)))
                .stream().findFirst().orElseThrow(() -> new NotFoundException("Transaction not found: " + id));
    }

    private static MapSqlParameterSource headerParams(TransactionType type, TransactionRequest r) {
        return new MapSqlParameterSource("type", type.name()).addValue("date", r.transactionDate())
                .addValue("party", r.partyId()).addValue("challan", blankToNull(r.challanNumber()))
                .addValue("vehicle", blankToNull(r.vehicleNumber())).addValue("driver", blankToNull(r.driverName()))
                .addValue("remarks", blankToNull(r.remarks()));
    }

    private List<Line> insertItems(Long transactionId, List<ItemInput> items, List<BigDecimal> quantities) {
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ItemInput item = items.get(i);
            BigDecimal quantity = quantities.get(i);
            Long itemId = jdbc.queryForObject("""
                    insert into material_transaction_items(transaction_id, product_id, quantity, rate, remarks)
                    values (:tx, :product, :quantity, :rate, :remarks) returning id
                    """, new MapSqlParameterSource("tx", transactionId).addValue("product", item.productId())
                    .addValue("quantity", quantity).addValue("rate", rate(item))
                    .addValue("remarks", blankToNull(item.remarks())), Long.class);
            lines.add(new Line(item.productId(), itemId, quantity));
        }
        return lines;
    }

    /** Header total = sum of the generated item amounts, read back from the database. */
    private void refreshTotal(Long id) {
        jdbc.update("""
                update material_transactions set total_amount = (select coalesce(sum(amount), 0)
                    from material_transaction_items where transaction_id = :id and superseded_at is null) where id = :id
                """, new MapSqlParameterSource("id", id));
    }

    private TransactionDetail detail(TransactionType type, Long id) {
        List<ItemResponse> items = jdbc.query("""
                select i.id, i.product_id, pr.product_code, pr.product_name, pr.unit, i.quantity, i.rate, i.amount, i.remarks
                from material_transaction_items i join products pr on pr.id = i.product_id
                where i.transaction_id = :id and i.superseded_at is null order by i.id
                """, new MapSqlParameterSource("id", id),
                (rs, i) -> new ItemResponse(rs.getLong("id"), rs.getLong("product_id"), rs.getString("product_code"),
                        rs.getString("product_name"), rs.getString("unit"), rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("rate"), rs.getBigDecimal("amount"), rs.getString("remarks")));
        return jdbc.query("""
                select t.*, p.party_name, cu.full_name as created_name, xu.full_name as cancelled_name
                from material_transactions t
                left join parties p on p.id = t.party_id
                join users cu on cu.id = t.created_by
                left join users xu on xu.id = t.cancelled_by
                where t.id = :id and t.transaction_type = :type
                """, new MapSqlParameterSource("id", id).addValue("type", type.name()),
                (rs, i) -> new TransactionDetail(rs.getLong("id"), rs.getString("transaction_number"),
                        rs.getString("transaction_type"), rs.getObject("transaction_date", LocalDate.class),
                        rs.getObject("party_id", Long.class), rs.getString("party_name"), rs.getString("challan_number"),
                        rs.getString("vehicle_number"), rs.getString("driver_name"), rs.getString("remarks"),
                        rs.getBigDecimal("total_amount"), rs.getString("status"), rs.getLong("version"),
                        rs.getString("created_name"), rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant(), rs.getString("cancelled_name"),
                        instant(rs.getTimestamp("cancelled_at")), rs.getString("cancellation_reason"), items))
                .stream().findFirst().orElseThrow(() -> new NotFoundException("Transaction not found: " + id));
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static BigDecimal rate(ItemInput i) {
        return i.rate() == null ? BigDecimal.ZERO : i.rate();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
