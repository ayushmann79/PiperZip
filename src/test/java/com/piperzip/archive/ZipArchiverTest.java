package com.piperzip.archive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.piperzip.config.ExtractionLimits;
import com.piperzip.exception.InvalidInputException;
import com.piperzip.testutil.TestData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipArchiverTest {

    private final ZipArchiver archiver = new ZipArchiver();

    private static List<String> entryNames(Path zip) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            file.stream().map(ZipEntry::getName).forEach(names::add);
        }
        return names;
    }

    @Test
    void archivesFilesAndDirectoriesWithRelativeSlashNames(@TempDir Path dir) throws IOException {
        Path project = dir.resolve("project");
        TestData.writeText(project, "readme.txt", "hello");
        TestData.writeText(project, "src/Main.java", "class Main {}");
        Files.createDirectories(project.resolve("empty"));
        Path loose = TestData.writeText(dir, "loose.txt", "loose");
        Path zip = dir.resolve("out.zip");

        ArchiveResult result = archiver.create(List.of(project, loose), zip, false, 6);

        assertEquals(List.of("project/", "project/empty/", "project/readme.txt", "project/src/",
                "project/src/Main.java", "loose.txt"), entryNames(zip));
        assertEquals(6, result.entryCount());
        assertEquals(5 + 13 + 5, result.stats().originalBytes());
        assertEquals(Files.size(zip), result.stats().compressedBytes());
    }

    @Test
    void createThenExtractRoundTrips(@TempDir Path dir) throws IOException {
        assumeTrue(TestData.supportsUnicodeFileNames(), "JVM file-name encoding is not UTF-8");
        byte[] binary = TestData.randomBytes(11, 250_000);
        Path src = dir.resolve("src");
        TestData.write(src, "bin/data.bin", binary);
        TestData.writeText(src, "ünï/日本語 file.txt", "unicode ✓");
        TestData.write(src, "empty.txt", new byte[0]);
        Path zip = dir.resolve("a.zip");
        archiver.create(List.of(src), zip, false, 9);

        Path out = dir.resolve("out");
        ExtractionResult result = new SafeExtractor(ExtractionLimits.defaults()).extract(zip, out, false);

        assertArrayEquals(binary, Files.readAllBytes(out.resolve("src/bin/data.bin")));
        assertEquals("unicode ✓", Files.readString(out.resolve("src/ünï/日本語 file.txt")));
        assertEquals(0, Files.size(out.resolve("src/empty.txt")));
        assertTrue(result.entryCount() >= 5);
    }

    @Test
    void entriesAreInDeterministicOrder(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("src");
        for (String name : List.of("c.txt", "a.txt", "b.txt")) {
            TestData.writeText(src, name, name);
        }
        archiver.create(List.of(src), dir.resolve("1.zip"), false, 6);
        archiver.create(List.of(src), dir.resolve("2.zip"), false, 6);
        assertEquals(entryNames(dir.resolve("1.zip")), entryNames(dir.resolve("2.zip")));
        assertEquals(List.of("src/", "src/a.txt", "src/b.txt", "src/c.txt"), entryNames(dir.resolve("1.zip")));
    }

    @Test
    void rejectsEmptyInputList(@TempDir Path dir) {
        assertThrows(InvalidInputException.class, () -> archiver.create(List.of(), dir.resolve("a.zip"), false, 6));
    }

    @Test
    void rejectsMissingInput(@TempDir Path dir) {
        assertThrows(InvalidInputException.class,
                () -> archiver.create(List.of(dir.resolve("nope")), dir.resolve("a.zip"), false, 6));
    }

    @Test
    void rejectsInvalidLevel(@TempDir Path dir) throws IOException {
        Path file = TestData.writeText(dir, "a.txt", "x");
        assertThrows(InvalidInputException.class,
                () -> archiver.create(List.of(file), dir.resolve("a.zip"), false, 10));
    }

    @Test
    void refusesToOverwriteExistingArchive(@TempDir Path dir) throws IOException {
        Path file = TestData.writeText(dir, "a.txt", "x");
        Path zip = TestData.writeText(dir, "a.zip", "existing");
        assertThrows(InvalidInputException.class, () -> archiver.create(List.of(file), zip, false, 6));
        assertEquals("existing", Files.readString(zip));
        archiver.create(List.of(file), zip, true, 6);
        assertEquals(List.of("a.txt"), entryNames(zip));
    }

    @Test
    void rejectsDuplicateEntryNames(@TempDir Path dir) throws IOException {
        Path one = TestData.writeText(dir, "one/same.txt", "1");
        Path two = TestData.writeText(dir, "two/same.txt", "2");
        InvalidInputException e = assertThrows(InvalidInputException.class,
                () -> archiver.create(List.of(one, two), dir.resolve("a.zip"), false, 6));
        assertTrue(e.getMessage().contains("same archive entry name"));
        assertTrue(Files.notExists(dir.resolve("a.zip")));
    }

    @Test
    void rejectsArchiveInsideItsOwnInputDirectory(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("src");
        TestData.writeText(src, "a.txt", "x");
        assertThrows(InvalidInputException.class,
                () -> archiver.create(List.of(src), src.resolve("out.zip"), false, 6));
    }

    @Test
    void rejectsSymbolicLinkInput(@TempDir Path dir) throws IOException {
        Path target = TestData.writeText(dir, "target.txt", "x");
        Path link = dir.resolve("link.txt");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links not supported here");
        }
        assertThrows(InvalidInputException.class, () -> archiver.create(List.of(link), dir.resolve("a.zip"), false, 6));
    }

    @Test
    void rejectsSymbolicLinkInsideDirectory(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("src");
        Path target = TestData.writeText(dir, "outside.txt", "secret");
        Files.createDirectories(src);
        try {
            Files.createSymbolicLink(src.resolve("link"), target);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links not supported here");
        }
        assertThrows(InvalidInputException.class, () -> archiver.create(List.of(src), dir.resolve("a.zip"), false, 6));
        assertTrue(Files.notExists(dir.resolve("a.zip")));
    }

    @Test
    void rejectsBackslashInFileNameOnPosix(@TempDir Path dir) throws IOException {
        assumeTrue(TestData.isPosix(dir));
        Path weird;
        try {
            weird = TestData.writeText(dir, "back\\slash.txt", "x");
        } catch (Exception e) {
            assumeTrue(false, "file system rejects this name");
            return;
        }
        assertThrows(InvalidInputException.class, () -> archiver.create(List.of(weird), dir.resolve("a.zip"), false, 6));
    }

    @Test
    void failureLeavesNoPartialArchive(@TempDir Path dir) throws IOException {
        Path good = TestData.writeText(dir, "good.txt", "x");
        assertThrows(InvalidInputException.class,
                () -> archiver.create(List.of(good, dir.resolve("missing")), dir.resolve("a.zip"), false, 6));
        try (var files = Files.list(dir)) {
            assertEquals(1, files.count(), "only good.txt should remain");
        }
    }
}
