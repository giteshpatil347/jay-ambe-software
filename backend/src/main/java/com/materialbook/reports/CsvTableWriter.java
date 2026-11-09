package com.materialbook.reports;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RFC 4180 CSV, UTF-8 with BOM so Excel detects the encoding. Text cells that start with a formula trigger
 * ({@code = + - @ TAB CR}) get a leading apostrophe (CSV injection); numbers and dates are written raw, so negative
 * amounts stay numeric.
 */
public final class CsvTableWriter {
    private static final String BOM = "﻿";
    private static final String EOL = "\r\n";

    private CsvTableWriter() {}

    public static void write(ReportTable table, OutputStream out) throws IOException {
        Writer w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        w.write(BOM);
        line(w, table.columns().stream().map(ReportTable.Column::header).collect(Collectors.toList()));
        for (List<Object> row : table.rows()) {
            line(w, row);
        }
        if (table.totals() != null) {
            line(w, table.totals());
        }
        w.flush();
    }

    private static void line(Writer w, List<?> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                w.write(',');
            }
            w.write(cell(cells.get(i)));
        }
        w.write(EOL);
    }

    static String cell(Object value) {
        return switch (value) {
            case null -> "";
            case BigDecimal d -> d.toPlainString();
            case LocalDate d -> d.toString();
            case Number n -> n.toString();
            default -> quote(neutralise(value.toString()));
        };
    }

    private static String neutralise(String text) {
        String trimmed = text.stripLeading(); // some spreadsheet importers ignore leading blanks before a formula
        if (!text.isEmpty() && ("=+-@\t\r".indexOf(text.charAt(0)) >= 0 || (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0))) {
            return "'" + text;
        }
        return text;
    }

    private static String quote(String text) {
        boolean needsQuotes = text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0
                || text.indexOf('\r') >= 0 || text.startsWith(" ") || text.endsWith(" ");
        return needsQuotes ? '"' + text.replace("\"", "\"\"") + '"' : text;
    }
}
