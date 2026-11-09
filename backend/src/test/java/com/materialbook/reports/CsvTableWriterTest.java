package com.materialbook.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.materialbook.reports.ReportTable.Column;
import com.materialbook.reports.ReportTable.ColumnType;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvTableWriterTest {
    private static String csv(List<Object> row, List<Object> totals) throws Exception {
        ReportTable table = new ReportTable("T", List.of(), List.of(new Column("Name", ColumnType.TEXT, 1),
                new Column("Date", ColumnType.DATE, 1), new Column("Amount", ColumnType.MONEY, 1)), List.of(row), totals, List.of(), false, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CsvTableWriter.write(table, out);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void startsWithBomAndUsesCrlfRecords() throws Exception {
        String text = csv(List.of("Plain", LocalDate.of(2026, 3, 10), new BigDecimal("1500.50")), null);
        assertThat(text).startsWith("﻿Name,Date,Amount\r\n");
        assertThat(text).endsWith("Plain,2026-03-10,1500.50\r\n");
    }

    @Test
    void quotesCommasQuotesAndLineBreaks() throws Exception {
        String text = csv(List.of("Acme, \"Steel\"\nLtd", LocalDate.of(2026, 1, 1), BigDecimal.ONE), null);
        assertThat(text).contains("\"Acme, \"\"Steel\"\"\nLtd\"");
    }

    @Test
    void neutralisesFormulaTriggersInTextCells() throws Exception {
        for (String evil : List.of("=SUM(A1)", "+1+1", "-2+3", "@cmd", "\tx", "\rx", " =1+1", "  @SUM(A1)")) {
            String text = csv(List.of(evil, LocalDate.of(2026, 1, 1), BigDecimal.ONE), null);
            String firstCell = text.split("\r\n", 2)[1].split(",", 2)[0];
            assertThat(firstCell.replace("\"", "")).as(evil).startsWith("'");
        }
    }

    @Test
    void negativeNumbersStayRawAndNullsAreBlank() throws Exception {
        String text = csv(List.of("x", LocalDate.of(2026, 1, 1), new BigDecimal("-600.00")),
                Arrays.asList("Total", null, new BigDecimal("-600.00")));
        assertThat(text).contains("x,2026-01-01,-600.00\r\n").endsWith("Total,,-600.00\r\n");
    }
}
