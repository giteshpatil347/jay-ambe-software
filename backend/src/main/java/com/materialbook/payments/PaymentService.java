package com.materialbook.payments;

import com.materialbook.audit.service.AuditService;
import com.materialbook.common.ApiException.ConflictException;
import com.materialbook.common.ApiException.NotFoundException;
import com.materialbook.common.ApiException.ValidationException;
import com.materialbook.expenses.PaymentMode;
import com.materialbook.parties.PartyDtos.PartyResponse;
import com.materialbook.parties.PartyService;
import com.materialbook.parties.PartyStatus;
import com.materialbook.payments.PaymentDtos.PaymentPage;
import com.materialbook.payments.PaymentDtos.PaymentRequest;
import com.materialbook.payments.PaymentDtos.PaymentResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Party payments (we paid a supplier / a client paid us). Plain SQL on {@code party_payments}.
 * <ul>
 *   <li>Number: {@code PAY-<current UTC year>-NNNNN} from {@code next_document_number('PAY', year)}.</li>
 *   <li>There is deliberately <b>no edit</b>: a wrong payment is cancelled (reason required) and re-created,
 *       so the ledger history stays explainable. Cancelled payments are excluded from ledgers.</li>
 *   <li>Payments can only be created for ACTIVE parties.</li>
 * </ul>
 */
@Service
public class PaymentService {
    private static final String MODULE = "PAYMENT";
    private static final int MAX_PAGE_SIZE = 200;
    private static final String SELECT = """
            select p.id, p.payment_number, p.party_id, pt.party_name, p.payment_date, p.direction, p.amount,
                   p.payment_mode, p.reference_number, p.remarks, p.status, p.created_at, p.cancelled_at, p.cancellation_reason
            from party_payments p join parties pt on pt.id = p.party_id
            """;
    private static final RowMapper<PaymentResponse> MAPPER = PaymentService::map;

    private final NamedParameterJdbcTemplate jdbc;
    private final PartyService parties;
    private final AuditService audit;

    public PaymentService(NamedParameterJdbcTemplate jdbc, PartyService parties, AuditService audit) {
        this.jdbc = jdbc;
        this.parties = parties;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PaymentPage list(PaymentFilter f, int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = where(f, params);
        params.addValue("limit", pageSize).addValue("offset", (long) Math.max(page, 0) * pageSize);
        List<PaymentResponse> rows = jdbc.query(SELECT + where + " order by p.payment_date desc, p.id desc limit :limit offset :offset",
                params, MAPPER);
        long total = jdbc.queryForObject("select count(*) from party_payments p " + where, params, Long.class);
        BigDecimal posted = jdbc.queryForObject("select coalesce(sum(p.amount), 0) from party_payments p " + where
                + (where.isEmpty() ? " where" : " and") + " p.status = 'POSTED'", params, BigDecimal.class);
        return new PaymentPage(rows, Math.max(page, 0), pageSize, total, posted);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long id) {
        return jdbc.query(SELECT + " where p.id = :id", new MapSqlParameterSource("id", id), MAPPER).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("Payment not found: " + id));
    }

    @Transactional
    public PaymentResponse create(Long userId, PaymentRequest r) {
        PartyResponse party = parties.get(r.partyId());
        if (party.status() != PartyStatus.ACTIVE) {
            throw new ValidationException("Party is inactive");
        }
        String number = jdbc.getJdbcTemplate().queryForObject("select next_document_number('PAY', ?)", String.class,
                Year.now(ZoneOffset.UTC).getValue());
        Long id = jdbc.queryForObject("""
                insert into party_payments (payment_number, party_id, payment_date, direction, amount, payment_mode,
                                            reference_number, remarks, created_by)
                values (:number, :party, :date, :direction, :amount, :mode, :ref, :remarks, :user) returning id
                """, new MapSqlParameterSource().addValue("number", number).addValue("party", r.partyId())
                .addValue("date", r.paymentDate()).addValue("direction", r.direction().name()).addValue("amount", r.amount())
                .addValue("mode", r.paymentMode() == null ? null : r.paymentMode().name())
                .addValue("ref", blankToNull(r.referenceNumber())).addValue("remarks", blankToNull(r.remarks()))
                .addValue("user", userId), Long.class);
        PaymentResponse created = get(id);
        audit.log("PAYMENT_CREATE", MODULE, id, null, created);
        return created;
    }

    @Transactional
    public PaymentResponse cancel(Long userId, Long id, String reason) {
        PaymentResponse before = get(id);
        int updated = jdbc.update("""
                update party_payments set status = 'CANCELLED', cancelled_by = :user, cancelled_at = now(), cancellation_reason = :reason
                where id = :id and status = 'POSTED'
                """, new MapSqlParameterSource().addValue("user", userId).addValue("reason", reason.trim()).addValue("id", id));
        if (updated == 0) {
            throw new ConflictException("Payment is already cancelled");
        }
        PaymentResponse after = get(id);
        audit.log("PAYMENT_CANCEL", MODULE, id, before, after);
        return after;
    }

    private static String where(PaymentFilter f, MapSqlParameterSource params) {
        StringBuilder w = new StringBuilder();
        String glue = " where ";
        if (f.from() != null) {
            w.append(glue).append("p.payment_date >= :from");
            params.addValue("from", f.from());
            glue = " and ";
        }
        if (f.to() != null) {
            w.append(glue).append("p.payment_date <= :to");
            params.addValue("to", f.to());
            glue = " and ";
        }
        if (f.partyId() != null) {
            w.append(glue).append("p.party_id = :partyId");
            params.addValue("partyId", f.partyId());
            glue = " and ";
        }
        if (f.direction() != null) {
            w.append(glue).append("p.direction = :direction");
            params.addValue("direction", f.direction().name());
            glue = " and ";
        }
        if (f.status() != null) {
            w.append(glue).append("p.status = :status");
            params.addValue("status", f.status().name());
            glue = " and ";
        }
        if (f.q() != null && !f.q().isBlank()) {
            w.append(glue).append("(lower(p.payment_number) like :q escape '\\' or lower(p.reference_number) like :q escape '\\')");
            params.addValue("q", "%" + f.q().trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        return w.toString();
    }

    private static PaymentResponse map(ResultSet rs, int row) throws SQLException {
        String mode = rs.getString("payment_mode");
        OffsetDateTime cancelledAt = rs.getObject("cancelled_at", OffsetDateTime.class);
        return new PaymentResponse(rs.getLong("id"), rs.getString("payment_number"), rs.getLong("party_id"),
                rs.getString("party_name"), rs.getObject("payment_date", LocalDate.class),
                PaymentDirection.valueOf(rs.getString("direction")), rs.getBigDecimal("amount"),
                mode == null ? null : PaymentMode.valueOf(mode), rs.getString("reference_number"), rs.getString("remarks"),
                PaymentStatus.valueOf(rs.getString("status")), rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                cancelledAt == null ? null : cancelledAt.toInstant(), rs.getString("cancellation_reason"));
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
