package com.materialbook.reports;

import com.materialbook.audit.service.AuditService;
import com.materialbook.common.ApiException.NotFoundException;
import com.materialbook.common.ApiException.ValidationException;
import com.materialbook.parties.PartyLedgerDtos.LedgerEntry;
import com.materialbook.parties.PartyLedgerDtos.LedgerResponse;
import com.materialbook.parties.PartyLedgerService;
import com.materialbook.reports.ReportDtos.CategoryTotal;
import com.materialbook.reports.ReportDtos.ExpenseSummary;
import com.materialbook.reports.ReportDtos.MaterialSummary;
import com.materialbook.reports.ReportTable.Column;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Builds {@link ReportTable}s for each export, audits the export (action EXPORT, module REPORT) and writes PDF or CSV. */
@Service
public class ReportExportService {
    private static final String DEFAULT_BUSINESS_NAME = "MaterialBook";
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);

    private final ReportQueryService queries;
    private final PartyLedgerService ledgers;
    private final AuditService audit;
    private final NamedParameterJdbcTemplate jdbc;

    public ReportExportService(ReportQueryService queries, PartyLedgerService ledgers, AuditService audit, NamedParameterJdbcTemplate jdbc) {
        this.queries = queries;
        this.ledgers = ledgers;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    private enum Format {
        PDF("application/pdf", "pdf"), CSV("text/csv;charset=UTF-8", "csv");

        final String mediaType;
        final String extension;

        Format(String mediaType, String extension) {
            this.mediaType = mediaType;
            this.extension = extension;
        }

        static Format parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ValidationException("format must be pdf or csv");
            }
        }
    }

    // ───────────── exports ─────────────

    /** One row per item line (transaction columns repeat for every item); totals count POSTED documents only. */
    void material(MaterialType type, ReportFilter f, List<String> columns, String format, HttpServletResponse resp) throws IOException {
        Format fmt = Format.parse(format);
        List<List<Object>> rows = queries.materialLineCells(type, f);
        MaterialSummary summary = queries.materialSummary(type, f);
        List<Object> totals = blanks(11);
        totals.set(0, "Total (POSTED)");
        totals.set(10, summary.totalAmount());
        ReportTable table = new ReportTable(type.label + " Report",
                List.of(filterLine(f, true, true, true)),
                ReportColumns.MATERIAL,
                rows, totals, List.of(), true, null);
        deliver(table, columns, fmt, type.slug + "-report", null, Map.of("report", type.slug, "filters", f, "rows", rows.size()), resp);
    }

    void expenses(ReportFilter f, List<String> columns, String format, HttpServletResponse resp) throws IOException {
        Format fmt = Format.parse(format);
        List<List<Object>> rows = queries.expenseCells(f);
        ExpenseSummary summary = queries.expenseSummary(f);
        List<Object> totals = blanks(8);
        totals.set(0, "Total (POSTED)");
        totals.set(7, summary.totalAmount());
        List<String> footer = new ArrayList<>();
        if (!summary.byCategory().isEmpty()) {
            footer.add("By category (POSTED): " + summary.byCategory().stream()
                    .map((CategoryTotal c) -> c.categoryName() + " " + IndianNumber.rupees(c.amount())).collect(Collectors.joining("; ")));
        }
        ReportTable table = new ReportTable("Expense Report",
                List.of(filterLine(f, false, false, true)),
                ReportColumns.EXPENSES,
                rows, totals, footer, true, null);
        deliver(table, columns, fmt, "expense-report", null, Map.of("report", "expenses", "filters", f, "rows", rows.size()), resp);
    }

    void stock(StockFilter f, List<String> columns, String format, HttpServletResponse resp) throws IOException {
        Format fmt = Format.parse(format);
        List<List<Object>> rows = queries.stockCells(f);
        long low = rows.stream().filter(r -> "LOW".equals(r.get(6))).count();
        List<String> info = new ArrayList<>();
        info.add((f.asOf() == null ? "Current stock" : "Stock as of " + ReportTable.displayDate(f.asOf()))
                + (f.categoryId() == null ? "" : " | Category: " + name("select name from product_categories where id = :id", f.categoryId()))
                + (f.productId() == null ? "" : " | Material: " + name("select product_name from products where id = :id", f.productId()))
                + (f.lowOnly() ? " | Low stock only" : ""));
        ReportTable table = new ReportTable("Stock Report", info,
                ReportColumns.STOCK,
                rows, null, List.of("Materials: " + rows.size() + " | Low stock: " + low), false, null);
        deliver(table, columns, fmt, "stock-report", null, Map.of("report", "stock", "filters", f, "rows", rows.size()), resp);
    }

    /** Same figures and sign convention (D11) as the ledger screen: balance &gt; 0 = Receivable, &lt; 0 = Payable. */
    void ledger(Long partyId, LocalDate from, LocalDate to, List<String> columns, String format, HttpServletResponse resp) throws IOException {
        Format fmt = Format.parse(format);
        LedgerResponse ledger = ledgers.ledger(partyId, from, to);
        List<List<Object>> rows = new ArrayList<>();
        rows.add(Arrays.asList(from, "Opening balance", "", null, null, ledger.openingBalance(), position(ledger.openingBalance())));
        for (LedgerEntry e : ledger.entries()) {
            rows.add(List.of(e.date(), kind(e), e.reference(), e.debit(), e.credit(), e.balance(), position(e.balance())));
        }
        List<Object> totals = blanks(7);
        totals.set(0, "Totals / closing");
        totals.set(3, ledger.totals().debit());
        totals.set(4, ledger.totals().credit());
        totals.set(5, ledger.closingBalance());
        totals.set(6, position(ledger.closingBalance()));
        String period = from == null && to == null ? "All dates"
                : (from == null ? "Up to " : "From " + ReportTable.displayDate(from)) + (to == null ? "" : (from == null ? "" : " to ") + ReportTable.displayDate(to));
        ReportTable table = new ReportTable("Party Ledger - " + ledger.party().name(),
                List.of("Party: " + ledger.party().name() + " (" + ledger.party().code() + ", " + ledger.party().type() + ") | Period: " + period,
                        "Balance above zero = Receivable (party owes us); below zero = Payable (we owe the party). Debit raises the balance, credit lowers it."),
                ReportColumns.LEDGER,
                rows, totals, List.of(), false, null);
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("partyId", partyId);
        filters.put("from", from);
        filters.put("to", to);
        deliver(table, columns, fmt, "party-ledger-" + ledger.party().code(), partyId,
                Map.of("report", "party-ledger", "filters", filters, "rows", ledger.entries().size()), resp);
    }

    /** Single-document voucher (IN) or challan (OUT); the caller needs MATERIAL_IN.EXPORT / MATERIAL_OUT.EXPORT by type. */
    void challan(Long id, List<String> columns, Authentication auth, HttpServletResponse resp) throws IOException {
        Map<String, Object> h = jdbc.queryForList("""
                select t.transaction_number, t.transaction_type, t.transaction_date, t.challan_number, t.vehicle_number, t.driver_name,
                       t.remarks, t.total_amount, t.status, t.cancellation_reason, t.cancelled_at, pa.party_name
                from material_transactions t left join parties pa on pa.id = t.party_id where t.id = :id""",
                new MapSqlParameterSource("id", id)).stream().findFirst().orElseThrow(() -> new NotFoundException("Transaction not found"));
        MaterialType type = MaterialType.valueOf((String) h.get("transaction_type"));
        String required = "MATERIAL_" + type.name() + ".EXPORT";
        if (auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals(required))) {
            throw new AccessDeniedException("Access denied");
        }
        List<List<Object>> rows = new ArrayList<>();
        Map<String, BigDecimal> unitTotals = new LinkedHashMap<>();
        int[] n = {0};
        jdbc.query("""
                select pr.product_name, pr.unit, i.quantity, i.rate, i.amount from material_transaction_items i
                join products pr on pr.id = i.product_id where i.transaction_id = :id and i.superseded_at is null order by i.id""",
                new MapSqlParameterSource("id", id), rs -> {
                    String unit = rs.getString("unit");
                    BigDecimal quantity = rs.getBigDecimal("quantity");
                    rows.add(List.of(++n[0], rs.getString("product_name"), unit, quantity, rs.getBigDecimal("rate"), rs.getBigDecimal("amount")));
                    unitTotals.merge(unit, quantity, BigDecimal::add);
                });
        String number = (String) h.get("transaction_number");
        boolean cancelled = "CANCELLED".equals(h.get("status"));
        List<String> info = new ArrayList<>();
        info.add("Date: " + ReportTable.displayDate(((java.sql.Date) h.get("transaction_date")).toLocalDate())
                + (isBlank(h.get("challan_number")) ? "" : " | Challan no: " + h.get("challan_number")));
        info.add((type == MaterialType.IN ? "Supplier: " : "Client: ") + (h.get("party_name") == null ? "-" : h.get("party_name")));
        info.add("Vehicle: " + orDash(h.get("vehicle_number")) + " | Driver: " + orDash(h.get("driver_name")));
        if (!isBlank(h.get("remarks"))) {
            info.add("Remarks: " + h.get("remarks"));
        }
        if (cancelled) {
            info.add("STATUS: CANCELLED on " + ZonedDateTime.ofInstant(((Timestamp) h.get("cancelled_at")).toInstant(), ReportTable.IST)
                    .format(DATE_TIME) + " IST - " + h.get("cancellation_reason"));
        }
        List<Object> totals = blanks(6);
        totals.set(1, "Total");
        totals.set(5, h.get("total_amount"));
        ReportTable table = new ReportTable(type.documentTitle + " " + number, info,
                ReportColumns.CHALLAN,
                rows, totals,
                List.of("Total quantity per unit: " + unitTotals.entrySet().stream()
                        .map(e -> e.getKey() + " " + IndianNumber.format(e.getValue(), 3)).collect(Collectors.joining(", "))),
                false, cancelled ? "CANCELLED" : null);
        deliver(table, columns, Format.PDF, number, id, Map.of("report", "transaction-" + type.slug, "transactionNumber", number, "format", "pdf"), resp);
    }

    // ───────────── helpers ─────────────

    private void deliver(ReportTable full, List<String> columns, Format fmt, String baseName, Long recordId, Map<String, Object> auditValues,
                         HttpServletResponse resp) throws IOException {
        ReportTable table = full.select(columns); // 422 for unknown labels, before anything is written or audited
        Instant now = Instant.now();
        byte[] pdf = null;
        if (fmt == Format.PDF) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            PdfTableWriter.write(table, businessName(), now, buffer);
            pdf = buffer.toByteArray();
        }
        Map<String, Object> values = new LinkedHashMap<>(auditValues);
        values.put("format", fmt.extension);
        values.put("columns", table.columns().stream().map(Column::header).toList());
        audit.log("EXPORT", "REPORT", recordId, null, values);
        resp.setContentType(fmt.mediaType);
        resp.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(safeName(baseName) + "-" + FILE_DATE.format(ZonedDateTime.ofInstant(now, ReportTable.IST)) + "." + fmt.extension).build().toString());
        resp.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        if (pdf != null) {
            resp.setContentLength(pdf.length);
            resp.getOutputStream().write(pdf);
        } else {
            CsvTableWriter.write(table, resp.getOutputStream());
        }
    }

    private String businessName() {
        List<String> names = jdbc.getJdbcTemplate().queryForList(
                "select setting_value #>> '{}' from business_settings where setting_key = 'BUSINESS_NAME' and jsonb_typeof(setting_value) = 'string'",
                String.class);
        return names.isEmpty() || names.get(0).isBlank() ? DEFAULT_BUSINESS_NAME : names.get(0).trim();
    }

    private String filterLine(ReportFilter f, boolean party, boolean product, boolean category) {
        List<String> parts = new ArrayList<>();
        parts.add(f.from() == null && f.to() == null ? "Period: all dates"
                : "Period: " + (f.from() == null ? "start" : ReportTable.displayDate(f.from())) + " to " + (f.to() == null ? "today" : ReportTable.displayDate(f.to())));
        if (party && f.partyId() != null) {
            parts.add("Party: " + name("select party_name from parties where id = :id", f.partyId()));
        }
        if (product && f.productId() != null) {
            parts.add("Material: " + name("select product_name from products where id = :id", f.productId()));
        }
        if (category && f.categoryId() != null) {
            parts.add("Category: " + name(product ? "select name from product_categories where id = :id"
                    : "select category_name from expense_categories where id = :id", f.categoryId()));
        }
        parts.add("Status: " + (f.status() == null ? "all" : f.status()));
        return String.join(" | ", parts);
    }

    private String name(String sql, Long id) {
        List<String> names = jdbc.queryForList(sql, new MapSqlParameterSource("id", id), String.class);
        return names.isEmpty() ? "#" + id : names.get(0);
    }

    private static String safeName(String name) {
        return name.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static List<Object> blanks(int n) {
        return new ArrayList<>(Arrays.asList(new Object[n]));
    }

    private static boolean isBlank(Object o) {
        return o == null || o.toString().isBlank();
    }

    private static String orDash(Object o) {
        return isBlank(o) ? "-" : o.toString();
    }

    private static String position(BigDecimal balance) {
        return balance.signum() > 0 ? "Receivable" : balance.signum() < 0 ? "Payable" : "Settled";
    }

    private static String kind(LedgerEntry e) {
        return switch (e.kind()) {
            case PURCHASE -> "Purchase (IN)";
            case DISPATCH -> "Dispatch (OUT)";
            case PAYMENT_PAID -> "Payment paid";
            case PAYMENT_RECEIVED -> "Payment received";
        };
    }
}
