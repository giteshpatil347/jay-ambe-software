package com.materialbook.attachments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.materialbook.common.ApiException.NotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {
    @TempDir Path tmp;
    Path root;
    LocalFileStorage storage;

    @BeforeEach
    void init() {
        root = tmp.resolve("uploads");
        storage = new LocalFileStorage(root);
    }

    @Test
    void roundTripCreatesRootAndStoresPrivately() throws Exception {
        String key = FileStorage.newKey();
        byte[] data = FileValidatorTest.png();
        storage.put(key, data);
        assertThat(storage.get(key)).isEqualTo(data);
        assertThat(Files.readAllBytes(root.resolve(key))).isEqualTo(data);
        if (Files.getFileStore(root).supportsFileAttributeView("posix")) {
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(root.resolve(key)))).isEqualTo("rw-------");
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(root.resolve(key).getParent()))).isEqualTo("rwx------");
        }
        storage.delete(key);
        assertThat(root.resolve(key)).doesNotExist();
        storage.delete(key); // idempotent
        assertThatThrownBy(() -> storage.get(key)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void atomicWriteLeavesNoTempFiles() throws Exception {
        for (int i = 0; i < 5; i++) {
            storage.put(FileStorage.newKey(), FileValidatorTest.pdf());
        }
        try (Stream<Path> walk = Files.walk(root)) {
            assertThat(walk.filter(Files::isRegularFile).map(p -> p.getFileName().toString())).hasSize(5).noneMatch(n -> n.startsWith("."));
        }
    }

    @Test
    void rejectsTraversalAbsoluteAndMalformedKeys() {
        for (String bad : new String[] {"../x", "2026/../../x", "/etc/passwd", "2026/01/../../../x", "..", ".", "", "a//b", "a/./b", "a\\b", "2026/01/x\0y", "2026/01/..hidden", "%2e%2e/x", "a/b/"}) {
            assertThatThrownBy(() -> storage.put(bad, new byte[] {1})).as("put " + bad).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.get(bad)).as("get " + bad).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.delete(bad)).as("delete " + bad).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> storage.get(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(tmp.resolve("x")).doesNotExist();
    }

    @Test
    void rejectsSymlinksLeadingOutOfTheRoot() throws Exception {
        Path outside = Files.createDirectory(tmp.resolve("outside"));
        Files.write(outside.resolve("secret"), new byte[] {9});
        Files.createDirectories(root.resolve("2026"));
        Files.createSymbolicLink(root.resolve("2026/01"), outside);          // directory symlink
        Files.createDirectories(root.resolve("2026/02"));
        Files.createSymbolicLink(root.resolve("2026/02/leak"), outside.resolve("secret")); // file symlink

        assertThatThrownBy(() -> storage.put("2026/01/abc", new byte[] {1})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.get("2026/01/secret")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.get("2026/02/leak")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete("2026/01/secret")).isInstanceOf(IllegalArgumentException.class);
        assertThat(outside.resolve("secret")).exists();
        assertThat(outside.resolve("abc")).doesNotExist();
        storage.delete("2026/02/leak"); // removes the link itself only
        assertThat(root.resolve("2026/02/leak")).doesNotExist();
        assertThat(outside.resolve("secret")).exists();
    }

    @Test
    void contentSurvivesARestart() {
        String key = FileStorage.newKey();
        storage.put(key, FileValidatorTest.png());
        assertThat(new LocalFileStorage(root).get(key)).isEqualTo(FileValidatorTest.png());
    }

    @Test
    void relativeDirectoryResolvesAgainstWorkingDirectory() {
        LocalFileStorage relative = new LocalFileStorage(Path.of("target/test-uploads-" + System.nanoTime()));
        String key = FileStorage.newKey();
        relative.put(key, new byte[] {1});
        assertThat(relative.get(key)).isEqualTo(new byte[] {1});
    }

    @Test
    void startupFailsClearlyWhenRootCannotBeCreated() throws Exception {
        Path file = Files.write(tmp.resolve("not-a-dir"), new byte[] {1});
        assertThatThrownBy(() -> new LocalFileStorage(file.resolve("uploads")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("cannot be created or is not writable").hasMessageContaining("APP_STORAGE_DIR");
    }

    @Test
    void startupFailsClearlyWhenRootIsReadOnly() throws Exception {
        assumeFalse("root".equals(System.getProperty("user.name")), "root ignores directory permissions");
        Path ro = Files.createDirectory(tmp.resolve("ro"), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("r-x------")));
        try {
            assertThatThrownBy(() -> new LocalFileStorage(ro)).isInstanceOf(IllegalStateException.class).hasMessageContaining("not writable");
        } finally {
            Files.setPosixFilePermissions(ro, PosixFilePermissions.fromString("rwx------"));
        }
    }
}
