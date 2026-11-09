package com.materialbook.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.materialbook.common.ApiException.ValidationException;
import com.materialbook.reports.ReportTable.Column;
import com.materialbook.reports.ReportTable.ColumnType;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class ReportColumnSelectionTest {
    private static final List<Column> COLUMNS = List.of(new Column("Number", ColumnType.TEXT, 2), new Column("Date", ColumnType.DATE, 1.5f),
            new Column("Remarks", ColumnType.TEXT, 3), new Column("Amount", ColumnType.MONEY, 2));

    private static ReportTable table() {
        List<Object> totals = new ArrayList<>(Arrays.asList("Grand total", null, null, new BigDecimal("1500.50")));
        List<List<Object>> rows = List.of(List.of("N-1", LocalDate.of(2026, 3, 10), "=evil, \"q\"", new BigDecimal("1500.50")));
        return new ReportTable("Sample", List.of("Period"), COLUMNS, rows, totals, List.of(), false, null);
    }

    private static String csv(ReportTable t) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CsvTableWriter.write(t, out);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void keepsOriginalOrderRegardlessOfRequestOrderAndRescalesWeights() {
        ReportTable t = table().select(List.of("Amount", "Number"));
        assertThat(t.columns()).extracting(Column::header).containsExactly("Number", "Amount");
        assertThat(t.columns().stream().mapToDouble(Column::weight).sum()).isCloseTo(8.5, org.assertj.core.data.Offset.offset(0.001));
        assertThat(t.rows().get(0)).containsExactly("N-1", new BigDecimal("1500.50"));
        assertThat(t.totals()).containsExactly("Grand total", new BigDecimal("1500.50"));
    }

    @Test
    void absentOrBlankSelectionMeansAllColumns() {
        ReportTable all = table();
        assertThat(all.select(null)).isSameAs(all);
        assertThat(all.select(List.of())).isSameAs(all);
        assertThat(all.select(List.of(" "))).isSameAs(all);
    }

    @Test
    void unknownLabelListsValidOnes() {
        assertThatThrownBy(() -> table().select(List.of("Date", "Nope")))
                .isInstanceOf(ValidationException.class).hasMessageContaining("Nope").hasMessageContaining("Number, Date, Remarks, Amount");
    }

    @Test
    void totalsLabelMovesToFirstSelectedColumnWhenItsOwnIsDropped() {
        ReportTable t = table().select(List.of("Date", "Amount"));
        assertThat(t.totals()).containsExactly("Grand total", new BigDecimal("1500.50"));
        // first selected column carries a figure: label is omitted, figure kept
        assertThat(table().select(List.of("Amount")).totals()).containsExactly(new BigDecimal("1500.50"));
    }

    @Test
    void csvHeaderAndCellsMatchSelectionWithQuotingAndInjectionProtection() throws Exception {
        String text = csv(table().select(List.of("Remarks", "Amount")));
        assertThat(text).startsWith("﻿Remarks,Amount\r\n");
        assertThat(text).contains("\"'=evil, \"\"q\"\"\",1500.50\r\n").endsWith("Grand total,1500.50\r\n");
        assertThat(text).doesNotContain("Number").doesNotContain("2026-03-10");
    }

    @Test
    void pdfShowsOnlyChosenHeaders() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfTableWriter.write(table().select(List.of("Number", "Amount")), "Acme", Instant.parse("2026-11-08T10:15:00Z"), out);
        try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("Number", "Amount", "N-1", "Grand total").doesNotContain("Remarks", "Date", "10 Mar 2026");
        }
    }

    @Test
    void everyKindListsItsLabelsFromTheSameDefinitions() {
        assertThat(ReportColumns.labels("material-in")).containsExactly("Number", "Date", "Party", "Challan", "Vehicle", "Status", "Material",
                "Unit", "Quantity", "Rate", "Amount");
        assertThat(ReportColumns.labels("material-out")).isEqualTo(ReportColumns.labels("material-in"));
        assertThat(ReportColumns.labels("expenses")).containsExactly("Number", "Date", "Category", "Paid to", "Mode", "Status", "Remarks", "Amount");
        assertThat(ReportColumns.labels("stock")).containsExactly("Code", "Material", "Category", "Unit", "Quantity", "Minimum", "Status");
        assertThat(ReportColumns.labels("ledger")).containsExactly("Date", "Type", "Reference", "Debit", "Credit", "Balance", "Position");
        assertThat(ReportColumns.labels("challan")).containsExactly("#", "Material", "Unit", "Quantity", "Rate", "Amount");
        assertThatThrownBy(() -> ReportColumns.labels("bogus")).isInstanceOf(ValidationException.class);
    }
}
