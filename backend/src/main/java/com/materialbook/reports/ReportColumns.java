package com.materialbook.reports;

import com.materialbook.common.ApiException.ValidationException;
import com.materialbook.reports.ReportTable.Column;
import com.materialbook.reports.ReportTable.ColumnType;
import java.util.List;
import java.util.Locale;

/**
 * Single source of truth for the column definitions of every export. The exporters build their tables from these lists and
 * {@code GET /reports/export-columns} lists their labels, so the column picker can never drift from what is exported.
 * Row cells are produced by {@link ReportQueryService} / {@link ReportExportService} in exactly this order.
 */
public final class ReportColumns {
    public static final List<Column> MATERIAL = List.of(text("Number", 2.2f), date("Date", 1.5f), text("Party", 2.6f), text("Challan", 1.6f),
            text("Vehicle", 1.6f), text("Status", 1.4f), text("Material", 2.6f), text("Unit", 1f), qty("Quantity", 1.6f), money("Rate", 1.6f),
            money("Amount", 1.9f));
    public static final List<Column> EXPENSES = List.of(text("Number", 2f), date("Date", 1.5f), text("Category", 2f), text("Paid to", 2f),
            text("Mode", 1f), text("Status", 1.3f), text("Remarks", 3f), money("Amount", 1.8f));
    public static final List<Column> STOCK = List.of(text("Code", 1.6f), text("Material", 3.5f), text("Category", 2.2f), text("Unit", 1f),
            qty("Quantity", 1.7f), qty("Minimum", 1.7f), text("Status", 1.2f));
    public static final List<Column> LEDGER = List.of(date("Date", 1.4f), text("Type", 2f), text("Reference", 2.2f), money("Debit", 1.8f),
            money("Credit", 1.8f), money("Balance", 2f), text("Position", 1.5f));
    public static final List<Column> CHALLAN = List.of(new Column("#", ColumnType.INTEGER, 0.7f), text("Material", 4f), text("Unit", 1f),
            qty("Quantity", 1.8f), money("Rate", 1.8f), money("Amount", 2f));

    private ReportColumns() {}

    /** Columns for an export kind: material-in, material-out, expenses, stock, ledger or challan. */
    public static List<Column> forKind(String kind) {
        String k = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        return switch (k) {
            case "material-in", "material-out" -> MATERIAL;
            case "expenses" -> EXPENSES;
            case "stock" -> STOCK;
            case "ledger" -> LEDGER;
            case "challan" -> CHALLAN;
            default -> throw new ValidationException("kind must be one of material-in, material-out, expenses, stock, ledger, challan");
        };
    }

    public static List<String> labels(String kind) {
        return forKind(kind).stream().map(Column::header).toList();
    }

    private static Column text(String h, float w) { return new Column(h, ColumnType.TEXT, w); }
    private static Column date(String h, float w) { return new Column(h, ColumnType.DATE, w); }
    private static Column money(String h, float w) { return new Column(h, ColumnType.MONEY, w); }
    private static Column qty(String h, float w) { return new Column(h, ColumnType.QUANTITY, w); }
}
