package com.piperzip.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.piperzip.config.ExtractionLimits;
import com.piperzip.exception.CorruptDataException;
import com.piperzip.exception.InvalidInputException;
import com.piperzip.exception.UnsafeArchiveException;
import com.piperzip.testutil.TestData;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SafeExtractorTest {

    private final SafeExtractor extractor = new SafeExtractor(ExtractionLimits.defaults());

    private static long countFiles(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private static void assertDestinationEmpty(Path dest) throws IOException {
        try (Stream<Path> children = Files.list(dest)) {
            assertEquals(0, children.count(), "destination must be left untouched (no staging leftovers)");
        }
    }

    // ---- ZIP Slip and unsafe names -----------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
        "../evil.txt",
        "../../evil.txt",
        "a/../../evil.txt",
        "a/b/../../../evil.txt",
        "/tmp/evil.txt",
        "/evil.txt",
        "..\\evil.txt",
        "sub\\file.txt",
        "C:/evil.txt",
        "c:evil.txt",
        "ok/../../evil.txt"
    })
    void rejectsHostileEntryNames(String name, @TempDir Path dir) throws IOException {
        Path zip = TestData.zipWithEntries(dir.resolve("evil.zip"), "harmless.txt", name);
        Path dest = dir.resolve("dest");
        Files.createDirectories(dest);

        assertThrows(UnsafeArchiveException.class, () -> extractor.extract(zip, dest, false));

        assertDestinationEmpty(dest);
        assertFalse(Files.exists(dir.resolve("evil.txt")));
        assertFalse(Files.exists(Path.of("/tmp/evil.txt")));
    }

    @Test
    void nothingIsExtractedWhenALaterEntryIsMalicious(@TempDir Path dir) throws IOException {
        Path zip = TestData.zipWithEntries(dir.resolve("evil.zip"), "good1.txt", "good2.txt", "../evil.txt");
        Path dest = dir.resolve("dest");
        assertThrows(UnsafeArchiveException.class, () -> extractor.extract(zip, dest, false));
        assertEquals(0, countFiles(dest));
    }

    @Test
    void rejectsEntryNamesWithControlCharactersSafely(@TempDir Path dir) throws IOException {
        Path zip = TestData.zipWithEntries(dir.resolve("evil.zip"), "bad\0name.txt");
        UnsafeArchiveException e = assertThrows(UnsafeArchiveException.class,
                () -> extractor.extract(zip, dir.resolve("dest"), false));
        assertFalse(e.getMessage().contains("\0"));
    }

    @Test
    void rejectsDuplicateEntriesThatNormalizeToTheSamePath(@TempDir Path dir) throws IOException {
        Path zip = TestData.zipWithEntries(dir.resolve("dup.zip"), "a/b.txt", "a//b.txt");
        Path dest = dir.resolve("dest");
        assertThrows(UnsafeArchiveException.class, () -> extractor.extract(zip, dest, false));
        assertDestinationEmpty(dest);
    }

    @Test
    void conflictingFileAndDirectoryEntriesAreRejected(@TempDir Path dir) throws IOException {
        Path zip = TestData.zipWithEntries(dir.resolve("conflict.zip"), "a", "a/b.txt");
        Path dest = dir.resolve("dest");
        assertThrows(CorruptDataException.class, () -> extractor.extract(zip, dest, false));
        assertDestinationEmpty(dest);
    }

    @Test
    void currentDirectoryEntryIsIgnored(@TempDir Path dir) throws IOException {
        Path zip = TestData.zipWithEntries(dir.resolve("ok.zip"), "./", "file.txt");
        ExtractionResult result = extractor.extract(zip, dir.resolve("dest"), false);
        assertEquals(1, result.entryCount());
        assertTrue(Files.exists(dir.resolve("dest/file.txt")));
    }

    // ---- Symbolic links in the destination -----------------------------------------------

    @Test
    void refusesToWriteThroughSymlinkInDestination(@TempDir Path dir) throws IOException {
        Path outside = Files.createDirectory(dir.resolve("outside"));
        Path dest = Files.createDirectory(dir.resolve("dest"));
        try {
            Files.createSymbolicLink(dest.resolve("link"), outside);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links not supported here");
        }
        Path zip = TestData.zipWithEntries(dir.resolve("a.zip"), "link/pwned.txt");

        assertThrows(UnsafeArchiveException.class, () -> extractor.extract(zip, dest, true));

        assertEquals(0, countFiles(outside), "nothing may be written outside the destination");
    }

    // ---- Limits --------------------------------------------------------------------------

    @Test
    void entryCountLimitBoundary(@TempDir Path dir) throws IOException {
        Path zip = TestData.zipWithEntries(dir.resolve("three.zip"), "1.txt", "2.txt", "3.txt");
        Path ok = dir.resolve("ok");
        Path tooMany = dir.resolve("tooMany");
        assertEquals(3, new SafeExtractor(new ExtractionLimits(3, 1 << 20, 200)).extract(zip, ok, false).entryCount());
        assertThrows(UnsafeArchiveException.class,
                () -> new SafeExtractor(new ExtractionLimits(2, 1 << 20, 200)).extract(zip, tooMany, false));
        assertEquals(0, countFiles(tooMany));
    }

    @Test
    void totalSizeLimitBoundary(@TempDir Path dir) throws IOException {
        Path zip = TestData.zipOfZeros(dir.resolve("z.zip"), "zeros.bin", 1000);
        ExtractionResult ok = new SafeExtractor(new ExtractionLimits(10, 1000, 200)).extract(zip, dir.resolve("ok"), false);
        assertEquals(1000, ok.totalBytes());
        Path over = dir.resolve("over");
        assertThrows(UnsafeArchiveException.class,
                () -> new SafeExtractor(new ExtractionLimits(10, 999, 200)).extract(zip, over, false));
        assertEquals(0, countFiles(over));
        assertDestinationEmpty(over);
    }

    @Test
    void decompressionBombIsStoppedByRatioLimit(@TempDir Path dir) throws IOException {
        long size = 64L << 20; // 64 MiB of zeros compresses to roughly 64 KiB (ratio about 1000)
        Path zip = TestData.zipOfZeros(dir.resolve("bomb.zip"), "zeros.bin", size);
        Path dest = dir.resolve("dest");
        UnsafeArchiveException e = assertThrows(UnsafeArchiveException.class, () -> extractor.extract(zip, dest, false));
        assertTrue(e.getMessage().contains("ratio"));
        assertDestinationEmpty(dest);
    }

    @Test
    void smallHighlyCompressibleFilesAreNotFlaggedAsBombs(@TempDir Path dir) throws IOException {
        Path zip = TestData.zipOfZeros(dir.resolve("small.zip"), "zeros.bin", 1 << 20);
        assertEquals(1 << 20, extractor.extract(zip, dir.resolve("dest"), false).totalBytes());
    }

    @Test
    void lyingDeclaredSizeDoesNotBypassLimits(@TempDir Path dir) throws IOException {
        // Limits are enforced on bytes actually produced, not on sizes the archive declares.
        Path zip = TestData.zipOfZeros(dir.resolve("z.zip"), "zeros.bin", 5000);
        assertThrows(UnsafeArchiveException.class,
                () -> new SafeExtractor(new ExtractionLimits(10, 4000, 200)).extract(zip, dir.resolve("d"), false));
    }

    // ---- Overwrite behaviour -------------------------------------------------------------

    @Test
    void refusesToOverwriteWithoutForceAndExtractsNothing(@TempDir Path dir) throws IOException {
        Path zip = TestData.zipWithEntries(dir.resolve("a.zip"), "new.txt", "existing.txt");
        Path dest = Files.createDirectory(dir.resolve("dest"));
        TestData.writeText(dest, "existing.txt", "original");

        InvalidInputException e = assertThrows(InvalidInputException.class, () -> extractor.extract(zip, dest, false));

        assertTrue(e.getMessage().contains("--force"));
        assertEquals("original", Files.readString(dest.resolve("existing.txt")));
        assertFalse(Files.exists(dest.resolve("new.txt")), "extraction must be all-or-nothing");
    }

    @Test
    void overwritesWithForce(@TempDir Path dir) throws IOException {
        Path zip = TestData.zipWithEntries(dir.resolve("a.zip"), "existing.txt");
        Path dest = Files.createDirectory(dir.resolve("dest"));
        TestData.writeText(dest, "existing.txt", "original");
        extractor.extract(zip, dest, true);
        assertEquals("existing.txt", Files.readString(dest.resolve("existing.txt")));
    }

    @Test
    void cannotReplaceDirectoryWithFile(@TempDir Path dir) throws IOException {
        Path zip = TestData.zipWithEntries(dir.resolve("a.zip"), "thing");
        Path dest = Files.createDirectory(dir.resolve("dest"));
        Files.createDirectory(dest.resolve("thing"));
        assertThrows(InvalidInputException.class, () -> extractor.extract(zip, dest, true));
    }

    // ---- Malformed archives --------------------------------------------------------------

    @Test
    void notAZipIsRejected(@TempDir Path dir) throws IOException {
        Path file = TestData.writeText(dir, "fake.zip", "this is not a zip file at all");
        assertThrows(CorruptDataException.class, () -> extractor.extract(file, dir.resolve("d"), false));
    }

    @Test
    void emptyFileIsRejected(@TempDir Path dir) throws IOException {
        Path file = TestData.write(dir, "empty.zip", new byte[0]);
        assertThrows(CorruptDataException.class, () -> extractor.extract(file, dir.resolve("d"), false));
    }

    @Test
    void truncatedZipIsRejected(@TempDir Path dir) throws IOException {
        Path zip = dir.resolve("a.zip");
        new ZipArchiver().create(List.of(TestData.write(dir, "data.bin", TestData.randomBytes(9, 50_000))), zip, false, 6);
        try (RandomAccessFile raf = new RandomAccessFile(zip.toFile(), "rw")) {
            raf.setLength(raf.length() / 2);
        }
        Path dest = dir.resolve("dest");
        assertThrows(CorruptDataException.class, () -> extractor.extract(zip, dest, false));
        assertEquals(0, countFiles(dest));
    }

    @Test
    void corruptedEntryDataIsRejectedAndNothingIsExtracted(@TempDir Path dir) throws IOException {
        Path data = TestData.writeText(dir, "text.txt", "A fairly long line of text for compression. ".repeat(3000));
        Path zip = dir.resolve("a.zip");
        new ZipArchiver().create(List.of(data), zip, false, 6);
        byte[] bytes = Files.readAllBytes(zip);
        bytes[60] ^= 0x7F; // inside the compressed data of the only entry
        Files.write(zip, bytes);
        Path dest = dir.resolve("dest");
        assertThrows(CorruptDataException.class, () -> extractor.extract(zip, dest, false));
        assertEquals(0, countFiles(dest));
        assertDestinationEmpty(dest);
    }

    @Test
    void emptyArchiveExtractsNothing(@TempDir Path dir) throws IOException {
        Path zip = TestData.zipWithEntries(dir.resolve("empty.zip"));
        ExtractionResult result = extractor.extract(zip, dir.resolve("dest"), false);
        assertEquals(0, result.entryCount());
    }

    // ---- Destination safety --------------------------------------------------------------

    @Test
    void refusesFilesystemRoot(@TempDir Path dir) throws IOException {
        Path zip = TestData.zipWithEntries(dir.resolve("a.zip"), "x.txt");
        Path root = dir.toAbsolutePath().getRoot();
        assertThrows(InvalidInputException.class, () -> extractor.extract(zip, root, true));
    }

    @Test
    void refusesSystemDirectoriesOnUnix(@TempDir Path dir) throws IOException {
        assumeTrue(Path.of("/etc").toFile().isDirectory() && java.io.File.separatorChar == '/');
        Path zip = TestData.zipWithEntries(dir.resolve("a.zip"), "x.txt");
        assertThrows(InvalidInputException.class, () -> extractor.extract(zip, Path.of("/etc/piperzip-test"), true));
        assertFalse(Files.exists(Path.of("/etc/piperzip-test")));
    }

    @Test
    void destinationThatIsAFileIsRejected(@TempDir Path dir) throws IOException {
        Path zip = TestData.zipWithEntries(dir.resolve("a.zip"), "x.txt");
        Path file = TestData.writeText(dir, "file", "x");
        assertThrows(InvalidInputException.class, () -> extractor.extract(zip, file, false));
    }

    @Test
    void createsMissingDestinationDirectories(@TempDir Path dir) throws IOException {
        Path zip = TestData.zipWithEntries(dir.resolve("a.zip"), "deep/er/file.txt");
        extractor.extract(zip, dir.resolve("new/nested/dest"), false);
        assertTrue(Files.exists(dir.resolve("new/nested/dest/deep/er/file.txt")));
    }

    @Test
    void unicodeEntryNamesExtractCorrectly(@TempDir Path dir) throws IOException {
        assumeTrue(TestData.supportsUnicodeFileNames(), "JVM file-name encoding is not UTF-8");
        Path zip = TestData.zipWithEntries(dir.resolve("u.zip"), "日本語/ファイル.txt", "émoji 🚀.txt");
        extractor.extract(zip, dir.resolve("dest"), false);
        assertTrue(Files.exists(dir.resolve("dest/日本語/ファイル.txt")));
        assertTrue(Files.exists(dir.resolve("dest/émoji 🚀.txt")));
    }

    @Test
    void stagingDirectoryIsRemovedAfterSuccess(@TempDir Path dir) throws IOException {
        Path zip = TestData.zipWithEntries(dir.resolve("a.zip"), "file.txt");
        Path dest = dir.resolve("dest");
        extractor.extract(zip, dest, false);
        try (Stream<Path> children = Files.list(dest)) {
            assertEquals(List.of("file.txt"), children.map(p -> p.getFileName().toString()).toList());
        }
    }
}
