package com.materialbook.reports;

import com.materialbook.common.ApiException.ValidationException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Format-neutral tabular report shared by the PDF and CSV writers.
 * Cells are String, BigDecimal, LocalDate or Integer/Long (null = blank); the column type decides how they are rendered.
 *
 * @param infoLines   header lines under the title (filters, party, vehicle ...); PDF only
 * @param totals      optional totals row aligned with the columns
 * @param footerLines free text printed after the table (e.g. per-unit quantity totals); PDF only
 * @param watermark   optional diagonal text on every PDF page (e.g. CANCELLED)
 */
public record ReportTable(String title, List<String> infoLines, List<Column> columns, List<List<Object>> rows,
                          List<Object> totals, List<String> footerLines, boolean landscape, String watermark) {
    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    public enum ColumnType {
        TEXT, DATE, MONEY, QUANTITY, INTEGER;

        boolean numeric() { return this != TEXT && this != DATE; }
    }

    /** @param weight relative width of the column in the PDF */
    public record Column(String header, ColumnType type, float weight) {}

    /**
     * Keeps only the columns whose header is in {@code labels}, in the ORIGINAL order. Rows, totals and weights follow; weights are
     * rescaled so the remaining columns still fill the page width. Null/empty = unchanged; unknown label = 422 listing the valid ones.
     * The totals label (first text cell of the totals row) moves to the first selected column when its own column is dropped and that
     * cell is free; if that column carries a total figure itself, the label is omitted rather than overwriting the figure.
     */
    public ReportTable select(List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return this;
        }
        List<String> valid = columns.stream().map(Column::header).toList();
        List<String> wanted = labels.stream().map(String::trim).filter(l -> !l.isEmpty()).distinct().toList();
        if (wanted.isEmpty()) {
            return this;
        }
        List<String> unknown = wanted.stream().filter(l -> !valid.contains(l)).toList();
        if (!unknown.isEmpty()) {
            throw new ValidationException("Unknown export column(s): " + String.join(", ", unknown) + ". Valid columns: " + String.join(", ", valid));
        }
        List<Integer> keep = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            if (wanted.contains(columns.get(i).header())) {
                keep.add(i);
            }
        }
        float oldSum = 0;
        float newSum = 0;
        for (int i = 0; i < columns.size(); i++) {
            oldSum += columns.get(i).weight();
            if (keep.contains(i)) {
                newSum += columns.get(i).weight();
            }
        }
        List<Column> cols = new ArrayList<>();
        for (int i : keep) {
            Column c = columns.get(i);
            cols.add(new Column(c.header(), c.type(), c.weight() * oldSum / newSum));
        }
        List<List<Object>> newRows = new ArrayList<>();
        for (List<Object> row : rows) {
            newRows.add(pick(row, keep));
        }
        List<Object> newTotals = null;
        if (totals != null) {
            newTotals = pick(totals, keep);
            int labelAt = -1;
            for (int i = 0; i < totals.size(); i++) {
                if (totals.get(i) instanceof String t && !t.isBlank()) {
                    labelAt = i;
                    break;
                }
            }
            if (labelAt >= 0 && !keep.contains(labelAt) && (newTotals.get(0) == null || (newTotals.get(0) instanceof String f && f.isBlank()))) {
                newTotals.set(0, totals.get(labelAt));
            }
        }
        return new ReportTable(title, infoLines, cols, newRows, newTotals, footerLines, landscape, watermark);
    }

    private static List<Object> pick(List<Object> row, List<Integer> keep) {
        List<Object> out = new ArrayList<>();
        for (int i : keep) {
            out.add(row.get(i));
        }
        return out;
    }

    /** Human rendering used by the PDF writer. */
    static String display(Object value, ColumnType type) {
        if (value == null) {
            return "";
        }
        return switch (value) {
            case BigDecimal d when type == ColumnType.MONEY -> IndianNumber.rupees(d);
            case BigDecimal d -> IndianNumber.format(d, 3);
            case LocalDate d -> d.format(DISPLAY_DATE);
            default -> value.toString();
        };
    }

    static String displayDate(LocalDate date) {
        return date.format(DISPLAY_DATE);
    }
}
