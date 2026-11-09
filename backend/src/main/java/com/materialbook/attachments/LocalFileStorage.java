package com.materialbook.attachments;

import com.materialbook.common.ApiException.NotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores files under one root directory on local disk. Every key is checked to stay inside the root (no {@code ..},
 * no absolute paths, no symlink leading out); writes go to a temp file in the target directory and are moved into
 * place atomically; files are created 600 and directories 700 where the file system supports POSIX permissions.
 * Files are never served by URL: the only read path is {@link #get}, behind the authenticated API.
 */
public class LocalFileStorage implements FileStorage {
    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final boolean POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    private static final FileAttribute<?>[] FILE_ATTRS = POSIX
            ? new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))} : new FileAttribute<?>[0];
    private static final FileAttribute<?>[] DIR_ATTRS = POSIX
            ? new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))} : new FileAttribute<?>[0];

    private final Path root;      // absolute, normalized
    private final Path realRoot;  // symlinks resolved

    /** Creates the root if needed and proves it is writable; fails fast with a clear message otherwise. */
    public LocalFileStorage(Path dir) {
        this.root = dir.toAbsolutePath().normalize();
        try {
            Files.createDirectories(root, DIR_ATTRS);
            Path probe = Files.createTempFile(root, ".write-check-", ".tmp", FILE_ATTRS);
            Files.delete(probe);
            this.realRoot = root.toRealPath();
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("File storage directory '" + root + "' cannot be created or is not writable by the application user ("
                    + System.getProperty("user.name") + "). Set APP_STORAGE_DIR to a writable folder or fix its ownership/permissions: " + e, e);
        }
        log.info("File storage directory: {}", realRoot);
    }

    @Override
    public void put(String key, byte[] content) {
        Path target = resolve(key);
        Path dir = target.getParent();
        Path tmp = null;
        try {
            Files.createDirectories(dir, DIR_ATTRS);
            requireInsideRoot(dir.toRealPath());
            tmp = Files.createTempFile(dir, ".upload-", ".tmp", FILE_ATTRS);
            Files.write(tmp, content);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp = null;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store file", e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException e) {
                    log.warn("Could not remove temp file {}", tmp, e);
                }
            }
        }
    }

    @Override
    public byte[] get(String key) {
        Path file = resolve(key);
        try {
            requireInsideRoot(file.toRealPath());
            return Files.readAllBytes(file);
        } catch (NoSuchFileException e) {
            throw new NotFoundException("Attachment file not found");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read file", e);
        }
    }

    @Override
    public void delete(String key) {
        Path file = resolve(key);
        try {
            if (Files.exists(file.getParent())) {
                requireInsideRoot(file.getParent().toRealPath());
            }
            Files.deleteIfExists(file); // removes a symlink itself, never its target
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete file", e);
        }
    }

    /** Lexical check: only plain relative segments, then normalised and confined to the root. */
    private Path resolve(String key) {
        if (key == null || key.isEmpty() || key.startsWith("/")) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        for (String segment : key.split("/", -1)) {
            if (!SEGMENT.matcher(segment).matches() || segment.contains("..")) {
                throw new IllegalArgumentException("Invalid storage key");
            }
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return resolved;
    }

    private void requireInsideRoot(Path real) {
        if (!real.startsWith(realRoot)) {
            throw new IllegalArgumentException("Storage key escapes the storage directory");
        }
    }
}
