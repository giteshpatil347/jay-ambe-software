package com.materialbook.attachments;

import com.materialbook.common.ApiException.ValidationException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

/**
 * The file's real type is decided by its magic bytes (Tika); the declared Content-Type and the file extension
 * must agree with it, so a renamed or mislabelled upload is rejected rather than trusted.
 */
@Component
class FileValidator {
    static final int MAX_NAME_LENGTH = 255;

    enum FileKind {
        JPEG("image/jpeg", Set.of("jpg", "jpeg")),
        PNG("image/png", Set.of("png")),
        WEBP("image/webp", Set.of("webp")),
        PDF("application/pdf", Set.of("pdf"));

        final String mime;
        final Set<String> extensions;

        FileKind(String mime, Set<String> extensions) {
            this.mime = mime;
            this.extensions = extensions;
        }

        static FileKind ofMime(String mime) {
            for (FileKind k : values()) {
                if (k.mime.equals(mime)) {
                    return k;
                }
            }
            return null;
        }
    }

    record Policy(Set<FileKind> allowed, long maxBytes, String description) {
        static final Policy DOCUMENT = new Policy(Set.of(FileKind.JPEG, FileKind.PNG, FileKind.PDF), 10L * 1024 * 1024, "JPG, PNG or PDF");
        static final Policy PRODUCT_IMAGE = new Policy(Set.of(FileKind.JPEG, FileKind.PNG, FileKind.WEBP), 5L * 1024 * 1024, "JPG, PNG or WEBP");
    }

    record Validated(String fileName, String contentType, long size, String sha256) {}

    private final Tika tika = new Tika();

    Validated validate(String originalName, String declaredContentType, byte[] content, Policy policy) {
        if (content.length == 0) {
            throw new ValidationException("File is empty");
        }
        if (content.length > policy.maxBytes()) {
            throw new ValidationException("File is larger than " + policy.maxBytes() / (1024 * 1024) + " MB");
        }
        FileKind kind = FileKind.ofMime(tika.detect(content));
        if (kind == null || !policy.allowed().contains(kind)) {
            throw new ValidationException("File content is not an allowed type (" + policy.description() + ")");
        }
        String name = sanitizeName(originalName);
        if (!kind.extensions.contains(extension(name))) {
            throw new ValidationException("File extension does not match its content (" + kind.mime + ")");
        }
        if (declaredContentType == null || !declaredContentType.toLowerCase(Locale.ROOT).split(";")[0].trim().equals(kind.mime)) {
            throw new ValidationException("Declared content type does not match the file content (" + kind.mime + ")");
        }
        return new Validated(name, kind.mime, content.length, sha256(content));
    }

    /** Detected mime of a stored product image (jpeg/png/webp) or null. */
    String detectImageType(byte[] content) {
        FileKind kind = FileKind.ofMime(tika.detect(content));
        return kind != null && Policy.PRODUCT_IMAGE.allowed().contains(kind) ? kind.mime : null;
    }

    /** Keeps only the last path segment, drops control characters and filesystem-hostile characters, caps the length. */
    static String sanitizeName(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("\\p{Cntrl}", "").replaceAll("[:*?\"<>|]", "_").trim();
        name = name.replaceFirst("^\\.+", "");
        if (name.isEmpty()) {
            throw new ValidationException("File name is required");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            String ext = extension(name);
            name = ext.isEmpty() || ext.length() > 10 ? name.substring(0, MAX_NAME_LENGTH)
                    : name.substring(0, MAX_NAME_LENGTH - ext.length() - 1) + "." + ext;
        }
        return name;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
