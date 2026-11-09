package com.materialbook.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.materialbook.reports.ReportTable.Column;
import com.materialbook.reports.ReportTable.ColumnType;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class PdfTableWriterTest {
    private static final List<Column> COLUMNS = List.of(new Column("Number", ColumnType.TEXT, 2), new Column("Date", ColumnType.DATE, 1.5f),
            new Column("Remarks", ColumnType.TEXT, 3), new Column("Amount", ColumnType.MONEY, 2));

    private static byte[] render(List<List<Object>> rows, boolean landscape, String watermark) throws Exception {
        List<Object> totals = List.of("Grand total", "", "", new BigDecimal("187500"));
        ReportTable table = new ReportTable("Sample Report", List.of("Period: all dates"), COLUMNS, rows, totals, List.of("Footer note"), landscape, watermark);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfTableWriter.write(table, "Acme Traders", Instant.parse("2026-11-08T10:15:00Z"), out);
        return out.toByteArray();
    }

    private static String text(PDDocument doc, int page) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        return stripper.getText(doc);
    }

    private static List<Object> row(int i, String remarks) {
        return List.of("MIN-2026-" + String.format("%05d", i), LocalDate.of(2026, 3, 10), remarks, new BigDecimal("1500.50"));
    }

    @Test
    void rendersRupeeSignHeaderTotalsAndFooter() throws Exception {
        byte[] pdf = render(List.of(row(1, "short")), false, null);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            String t = text(doc, 1);
            assertThat(t).contains("Acme Traders", "Sample Report", "Period: all dates", "MIN-2026-00001", "10 Mar 2026",
                    "₹1,500.50", "Grand total", "₹1,87,500.00", "Footer note", "Page 1 of 1", "Generated: 08 Nov 2026, 03:45 PM IST");
        }
    }

    @Test
    void threeHundredRowsRepeatColumnHeadingsAndNumberPages() throws Exception {
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 1; i <= 300; i++) {
            rows.add(row(i, "row " + i));
        }
        try (PDDocument doc = Loader.loadPDF(render(rows, true, null))) {
            int pages = doc.getNumberOfPages();
            assertThat(pages).isGreaterThan(5);
            for (int p = 1; p <= pages; p++) {
                String t = text(doc, p);
                assertThat(t).as("page " + p).contains("Number", "Remarks", "Acme Traders").contains("Page " + p + " of " + pages);
            }
            assertThat(text(doc, pages)).contains("Grand total");
            assertThat(new PDFTextStripper().getText(doc)).contains("MIN-2026-00300");
        }
    }

    @Test
    void wrapsLongTextAndTruncatesWithEllipsis() throws Exception {
        String wraps = "alpha bravo charlie delta echo foxtrot golf hotel india juliet kilo lima mike november oscar papa";
        String endless = "word ".repeat(400).trim();
        try (PDDocument doc = Loader.loadPDF(render(List.of(row(1, wraps), row(2, endless)), false, null))) {
            String t = text(doc, 1);
            assertThat(t).contains("alpha", "papa");
            assertThat(t).contains("…");
            assertThat(t.split("word", -1).length).isLessThan(60);
        }
    }

    @Test
    void unbreakableTokensAreBrokenAndUnsupportedScriptsBecomeQuestionMarks() throws Exception {
        String unbroken = "X".repeat(300);
        try (PDDocument doc = Loader.loadPDF(render(List.of(row(1, unbroken), row(2, "नमस्ते Steel")), false, null))) {
            String t = text(doc, 1);
            assertThat(t).contains("XXXXXXXX").contains("Steel").contains("?");
            assertThat(t).doesNotContain("नमस्ते");
        }
    }

    @Test
    void emptyTableAndWatermark() throws Exception {
        try (PDDocument doc = Loader.loadPDF(render(List.of(), false, "CANCELLED"))) {
            assertThat(text(doc, 1)).contains("No records for the selected filters.", "CANCELLED");
        }
    }
}
