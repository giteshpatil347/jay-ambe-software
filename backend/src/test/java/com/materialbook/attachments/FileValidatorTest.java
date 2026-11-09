package com.materialbook.attachments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.materialbook.attachments.FileValidator.Policy;
import com.materialbook.attachments.FileValidator.Validated;
import com.materialbook.common.ApiException.ValidationException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class FileValidatorTest {
    private final FileValidator validator = new FileValidator();

    static byte[] png() { return pad(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}); }

    static byte[] jpeg() { return pad(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}); }

    static byte[] pdf() { return pad("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII)); }

    static byte[] webp() { return pad("RIFF\u0010\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1)); }

    private static byte[] pad(byte[] header) {
        byte[] out = Arrays.copyOf(header, 64);
        return out;
    }

    @Test
    void acceptsGenuineFilesAndReportsDetectedTypeAndHash() {
        Validated v = validator.validate("bill.PDF", "application/pdf", pdf(), Policy.DOCUMENT);
        assertThat(v.contentType()).isEqualTo("application/pdf");
        assertThat(v.fileName()).isEqualTo("bill.PDF");
        assertThat(v.size()).isEqualTo(64);
        assertThat(v.sha256()).hasSize(64).matches("[0-9a-f]+");
        assertThat(validator.validate("a.jpeg", "image/jpeg; charset=binary", jpeg(), Policy.DOCUMENT).contentType()).isEqualTo("image/jpeg");
        assertThat(validator.validate("a.png", "image/png", png(), Policy.DOCUMENT).contentType()).isEqualTo("image/png");
        assertThat(validator.validate("a.webp", "image/webp", webp(), Policy.PRODUCT_IMAGE).contentType()).isEqualTo("image/webp");
    }

    @Test
    void rejectsExecutableOrTextRenamedToAllowedExtension() {
        byte[] script = "#!/bin/sh\nrm -rf /\n".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> validator.validate("invoice.pdf", "application/pdf", script, Policy.DOCUMENT))
                .isInstanceOf(ValidationException.class).hasMessageContaining("not an allowed type");
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> validator.validate("x.png", "image/png", html, Policy.DOCUMENT)).isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsSpoofedExtension() {
        assertThatThrownBy(() -> validator.validate("photo.jpg", "image/png", png(), Policy.DOCUMENT))
                .isInstanceOf(ValidationException.class).hasMessageContaining("extension");
        assertThatThrownBy(() -> validator.validate("noext", "image/png", png(), Policy.DOCUMENT)).isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsSpoofedContentType() {
        assertThatThrownBy(() -> validator.validate("a.png", "application/pdf", png(), Policy.DOCUMENT))
                .isInstanceOf(ValidationException.class).hasMessageContaining("content type");
        assertThatThrownBy(() -> validator.validate("a.png", null, png(), Policy.DOCUMENT)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validator.validate("a.png", "application/octet-stream", png(), Policy.DOCUMENT)).isInstanceOf(ValidationException.class);
    }

    @Test
    void polyglotWithPdfHeaderIsTreatedAsPdfAndCannotPassAsImage() {
        byte[] polyglot = ("%PDF-1.4\n" + "\u0089PNG\r\n\u001a\n<script>alert(1)</script>").getBytes(StandardCharsets.ISO_8859_1);
        assertThatThrownBy(() -> validator.validate("evil.png", "image/png", polyglot, Policy.DOCUMENT))
                .isInstanceOf(ValidationException.class);
        assertThat(validator.validate("evil.pdf", "application/pdf", polyglot, Policy.DOCUMENT).contentType()).isEqualTo("application/pdf");
    }

    @Test
    void rejectsPdfAsProductImageAndWebpAsDocument() {
        assertThatThrownBy(() -> validator.validate("a.pdf", "application/pdf", pdf(), Policy.PRODUCT_IMAGE)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validator.validate("a.webp", "image/webp", webp(), Policy.DOCUMENT)).isInstanceOf(ValidationException.class);
    }

    @Test
    void enforcesSizeLimitsPerPolicy() {
        byte[] big = Arrays.copyOf(png(), 10 * 1024 * 1024 + 1);
        assertThatThrownBy(() -> validator.validate("a.png", "image/png", big, Policy.DOCUMENT)).hasMessageContaining("10 MB");
        byte[] sixMb = Arrays.copyOf(png(), 6 * 1024 * 1024);
        assertThatThrownBy(() -> validator.validate("a.png", "image/png", sixMb, Policy.PRODUCT_IMAGE)).hasMessageContaining("5 MB");
        assertThat(validator.validate("a.png", "image/png", Arrays.copyOf(png(), 10 * 1024 * 1024), Policy.DOCUMENT).size()).isEqualTo(10 * 1024 * 1024);
        assertThatThrownBy(() -> validator.validate("a.png", "image/png", new byte[0], Policy.DOCUMENT)).hasMessageContaining("empty");
    }

    @Test
    void sanitizesNamesAndStripsPathTraversal() {
        assertThat(FileValidator.sanitizeName("../../etc/passwd.png")).isEqualTo("passwd.png");
        assertThat(FileValidator.sanitizeName("C:\\Users\\x\\..\\scan.pdf")).isEqualTo("scan.pdf");
        assertThat(FileValidator.sanitizeName("bad\u0000na\nme\t.pdf")).isEqualTo("badname.pdf");
        assertThat(FileValidator.sanitizeName("a<b>:c?.png")).isEqualTo("a_b__c_.png");
        assertThat(FileValidator.sanitizeName("...hidden.png")).isEqualTo("hidden.png");
        assertThat(FileValidator.sanitizeName("  receipt \u20B9500.jpg ")).isEqualTo("receipt \u20B9500.jpg");
        assertThatThrownBy(() -> FileValidator.sanitizeName("../")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> FileValidator.sanitizeName(null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void capsNameLengthAtColumnSizeKeepingExtension() {
        String sanitized = FileValidator.sanitizeName("a".repeat(400) + ".pdf");
        assertThat(sanitized).hasSize(255).endsWith(".pdf");
    }

    @Test
    void traversalNameStillPassesValidationAsPlainFileName() {
        assertThat(validator.validate("../../secret.png", "image/png", png(), Policy.DOCUMENT).fileName()).isEqualTo("secret.png");
    }
}
