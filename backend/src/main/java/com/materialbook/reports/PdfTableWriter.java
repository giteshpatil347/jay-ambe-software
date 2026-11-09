package com.materialbook.reports;

import com.materialbook.reports.ReportTable.Column;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.util.Matrix;

/**
 * Minimal table-to-PDF renderer (Apache PDFBox + embedded Noto Sans, so the rupee sign renders). A4, business name and
 * title header and column headings on every page, wrapped/truncated text cells, right-aligned numerics, totals row and
 * "Page x of y" footer. Characters missing from Noto Sans (e.g. Devanagari) are printed as '?' (CSV keeps them).
 */
public final class PdfTableWriter {
    private static final float MARGIN = 36f;
    private static final float FONT_SIZE = 8.5f;
    private static final float LEADING = 11f;
    private static final float PAD = 3f;
    private static final int MAX_CELL_LINES = 3;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a 'IST'", Locale.ENGLISH);
    private static final byte[] REGULAR = font("/fonts/NotoSans-Regular.ttf");
    private static final byte[] BOLD = font("/fonts/NotoSans-Bold.ttf");

    private PdfTableWriter() {}

    private static byte[] font(String path) {
        try (InputStream in = PdfTableWriter.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing font resource " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void write(ReportTable table, String businessName, Instant generatedAt, OutputStream out) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDType0Font regular = PDType0Font.load(doc, new ByteArrayInputStream(REGULAR), true);
            PDType0Font bold = PDType0Font.load(doc, new ByteArrayInputStream(BOLD), true);
            new Renderer(doc, regular, bold, table, businessName, generatedAt).render();
            doc.save(out);
        }
    }

    private static final class Renderer {
        private final PDDocument doc;
        private final PDType0Font regular;
        private final PDType0Font bold;
        private final ReportTable table;
        private final String businessName;
        private final Instant generatedAt;
        private final PDRectangle pageSize;
        private final float[] colX;
        private final float[] colW;
        private final List<PDPage> pages = new ArrayList<>();
        private PDPageContentStream cs;
        private float y;

        Renderer(PDDocument doc, PDType0Font regular, PDType0Font bold, ReportTable table, String businessName, Instant generatedAt) {
            this.doc = doc;
            this.regular = regular;
            this.bold = bold;
            this.table = table;
            this.businessName = businessName;
            this.generatedAt = generatedAt;
            this.pageSize = table.landscape() ? new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()) : PDRectangle.A4;
            float usable = pageSize.getWidth() - 2 * MARGIN;
            float totalWeight = 0;
            for (Column c : table.columns()) {
                totalWeight += c.weight();
            }
            colX = new float[table.columns().size()];
            colW = new float[colX.length];
            float x = MARGIN;
            for (int i = 0; i < colX.length; i++) {
                colX[i] = x;
                colW[i] = usable * table.columns().get(i).weight() / totalWeight;
                x += colW[i];
            }
        }

        void render() throws IOException {
            newPage();
            if (table.rows().isEmpty()) {
                ensureSpace(LEADING + 2 * PAD);
                text(regular, MARGIN + PAD, y - LEADING, "No records for the selected filters.");
                y -= LEADING + 2 * PAD;
            }
            for (List<Object> row : table.rows()) {
                drawRow(row, regular, false);
            }
            if (table.totals() != null) {
                drawRow(table.totals(), bold, true);
            }
            for (String line : table.footerLines()) {
                for (String part : wrap(line, regular, pageSize.getWidth() - 2 * MARGIN, 10)) {
                    ensureSpace(LEADING);
                    text(regular, MARGIN, y - LEADING, part);
                    y -= LEADING;
                }
            }
            cs.close();
            stampFooters();
        }

        private void newPage() throws IOException {
            if (cs != null) {
                cs.close();
            }
            PDPage page = new PDPage(pageSize);
            doc.addPage(page);
            pages.add(page);
            cs = new PDPageContentStream(doc, page);
            if (table.watermark() != null) {
                watermark();
            }
            y = pageSize.getHeight() - MARGIN;
            text(bold, MARGIN, y - 13, businessName, 13);
            y -= 17;
            text(bold, MARGIN, y - 11, table.title(), 11);
            y -= 15;
            for (String info : table.infoLines()) {
                for (String part : wrap(info, regular, pageSize.getWidth() - 2 * MARGIN, 3)) {
                    text(regular, MARGIN, y - 9, part, 8);
                    y -= 10;
                }
            }
            y -= 6;
            columnHeadings();
        }

        private void columnHeadings() throws IOException {
            float height = LEADING + 2 * PAD;
            cs.setNonStrokingColor(0.90f, 0.92f, 0.95f);
            cs.addRect(MARGIN, y - height, pageSize.getWidth() - 2 * MARGIN, height);
            cs.fill();
            cs.setNonStrokingColor(0f, 0f, 0f);
            for (int i = 0; i < colX.length; i++) {
                Column c = table.columns().get(i);
                cell(bold, i, y - PAD - LEADING + 2.5f, wrap(c.header(), bold, colW[i] - 2 * PAD, 2).get(0), c.type().numeric());
            }
            y -= height;
        }

        private void drawRow(List<Object> row, PDType0Font font, boolean totals) throws IOException {
            List<List<String>> cells = new ArrayList<>();
            int lines = 1;
            for (int i = 0; i < colX.length; i++) {
                Column c = table.columns().get(i);
                String shown = ReportTable.display(row.get(i), c.type());
                List<String> parts = c.type().numeric() ? List.of(clean(shown)) : wrap(shown, font, colW[i] - 2 * PAD, MAX_CELL_LINES);
                cells.add(parts);
                lines = Math.max(lines, parts.size());
            }
            float height = lines * LEADING + 2 * PAD;
            ensureSpace(height);
            if (totals) {
                cs.setLineWidth(1f);
                line(y);
            }
            for (int i = 0; i < colX.length; i++) {
                boolean right = table.columns().get(i).type().numeric();
                float ty = y - PAD - LEADING + 2.5f;
                for (String part : cells.get(i)) {
                    cell(font, i, ty, part, right);
                    ty -= LEADING;
                }
            }
            y -= height;
            cs.setLineWidth(0.3f);
            cs.setStrokingColor(0.75f, 0.75f, 0.75f);
            line(y);
            cs.setStrokingColor(0f, 0f, 0f);
        }

        private void ensureSpace(float height) throws IOException {
            if (y - height < MARGIN + 24) {
                newPage();
            }
        }

        private void line(float at) throws IOException {
            cs.moveTo(MARGIN, at);
            cs.lineTo(pageSize.getWidth() - MARGIN, at);
            cs.stroke();
        }

        private void cell(PDType0Font font, int col, float baseline, String text, boolean right) throws IOException {
            float x = colX[col] + PAD;
            if (right) {
                x = colX[col] + colW[col] - PAD - width(font, text, FONT_SIZE);
            }
            text(font, x, baseline, text);
        }

        private void text(PDType0Font font, float x, float baseline, String text) throws IOException {
            text(font, x, baseline, text, FONT_SIZE);
        }

        private void text(PDType0Font font, float x, float baseline, String text, float size) throws IOException {
            cs.beginText();
            cs.setFont(font, size);
            cs.newLineAtOffset(x, baseline);
            cs.showText(clean(text));
            cs.endText();
        }

        private void watermark() throws IOException {
            String mark = table.watermark();
            float size = 90f;
            float w = width(bold, mark, size);
            double angle = Math.toRadians(35);
            float cx = pageSize.getWidth() / 2;
            float cy = pageSize.getHeight() / 2;
            cs.setNonStrokingColor(0.97f, 0.80f, 0.80f);
            cs.beginText();
            cs.setFont(bold, size);
            cs.setTextMatrix(Matrix.getRotateInstance(angle, cx - (float) (w / 2 * Math.cos(angle)), cy - (float) (w / 2 * Math.sin(angle))));
            cs.showText(mark);
            cs.endText();
            cs.setNonStrokingColor(0f, 0f, 0f);
        }

        private void stampFooters() throws IOException {
            String stamp = "Generated: " + ZonedDateTime.ofInstant(generatedAt, ReportTable.IST).format(STAMP);
            for (int i = 0; i < pages.size(); i++) {
                try (PDPageContentStream footer = new PDPageContentStream(doc, pages.get(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                    String pageText = "Page " + (i + 1) + " of " + pages.size();
                    footer.beginText();
                    footer.setFont(regular, 8);
                    footer.newLineAtOffset(MARGIN, MARGIN - 8);
                    footer.showText(stamp);
                    footer.endText();
                    footer.beginText();
                    footer.setFont(regular, 8);
                    footer.newLineAtOffset(pageSize.getWidth() - MARGIN - width(regular, pageText, 8), MARGIN - 8);
                    footer.showText(pageText);
                    footer.endText();
                }
            }
        }

        private float width(PDType0Font font, String text, float size) throws IOException {
            return font.getStringWidth(clean(text)) / 1000f * size;
        }

        /** Replaces line breaks/control characters with spaces and glyphs Noto Sans lacks with '?'. */
        private String clean(String text) {
            StringBuilder sb = new StringBuilder(text.length());
            text.codePoints().forEach(cp -> {
                if (Character.isISOControl(cp) || Character.isWhitespace(cp)) {
                    sb.append(' ');
                } else if (cp < 128 || hasGlyph(cp)) {
                    sb.appendCodePoint(cp);
                } else {
                    sb.append('?');
                }
            });
            return sb.toString();
        }

        private boolean hasGlyph(int codePoint) {
            try {
                regular.encode(new String(Character.toChars(codePoint)));
                return true;
            } catch (IllegalArgumentException | IOException e) {
                return false;
            }
        }

        /** Word-wraps (breaking over-long words) into at most {@code maxLines}; the last line ends with an ellipsis if cut. */
        private List<String> wrap(String raw, PDType0Font font, float maxWidth, int maxLines) throws IOException {
            String text = clean(raw).trim();
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean truncated = false;
            for (String word : text.split(" +")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (width(font, candidate, FONT_SIZE) <= maxWidth) {
                    current = new StringBuilder(candidate);
                    continue;
                }
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                    current = new StringBuilder();
                }
                String rest = word;
                while (width(font, rest, FONT_SIZE) > maxWidth) {
                    int cut = rest.length() - 1;
                    while (cut > 1 && width(font, rest.substring(0, cut), FONT_SIZE) > maxWidth) {
                        cut--;
                    }
                    lines.add(rest.substring(0, cut));
                    rest = rest.substring(cut);
                }
                current = new StringBuilder(rest);
            }
            lines.add(current.toString());
            if (lines.size() > maxLines) {
                lines = new ArrayList<>(lines.subList(0, maxLines));
                truncated = true;
            }
            if (truncated) {
                String last = lines.get(maxLines - 1);
                while (!last.isEmpty() && width(font, last + "…", FONT_SIZE) > maxWidth) {
                    last = last.substring(0, last.length() - 1);
                }
                lines.set(maxLines - 1, last + "…");
            }
            return lines;
        }
    }
}
