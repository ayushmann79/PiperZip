package com.piperzip.compression;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.piperzip.exception.CorruptDataException;
import com.piperzip.exception.InvalidInputException;
import com.piperzip.integrity.Sha256Hasher;
import com.piperzip.statistics.CompressionStats;
import com.piperzip.testutil.TestData;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileCompressorTest {

    private final FileCompressor compressor = new FileCompressor(new GzipCodec());

    private static void assertNoLeftovers(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(0, files.filter(p -> p.getFileName().toString().endsWith(".part")).count(),
                    "temporary .part files must be cleaned up");
        }
    }

    @Test
    void roundTripRestoresIdenticalBytes(@TempDir Path dir) throws IOException {
        Path original = TestData.write(dir, "data.bin", TestData.randomBytes(3, 500_000));
        Path gz = dir.resolve("data.gz");
        Path restored = dir.resolve("restored.bin");

        CompressionStats compressStats = compressor.compress(original, gz, false);
        CompressionStats decompressStats = compressor.decompress(gz, restored, false);

        assertArrayEquals(Files.readAllBytes(original), Files.readAllBytes(restored));
        assertEquals(500_000, compressStats.originalBytes());
        assertEquals(Files.size(gz), compressStats.compressedBytes());
        assertEquals(500_000, decompressStats.originalBytes());
        assertEquals(Files.size(gz), decompressStats.compressedBytes());
        assertNoLeftovers(dir);
    }

    @Test
    void emptyFileRoundTrips(@TempDir Path dir) throws IOException {
        Path original = TestData.write(dir, "empty", new byte[0]);
        CompressionStats stats = compressor.compress(original, dir.resolve("empty.gz"), false);
        compressor.decompress(dir.resolve("empty.gz"), dir.resolve("empty.out"), false);
        assertEquals(0, Files.size(dir.resolve("empty.out")));
        assertEquals(0, stats.originalBytes());
        assertTrue(stats.compressedBytes() > 0, "gzip always has a header and trailer");
    }

    @Test
    void unicodeAndUnusualFileNamesWork(@TempDir Path dir) throws IOException {
        assumeTrue(TestData.supportsUnicodeFileNames(), "JVM file-name encoding is not UTF-8");
        Path original = TestData.writeText(dir, "résumé 日本語 (final) #1.txt", "content ✓");
        Path gz = dir.resolve("résumé 日本語.gz");
        Path restored = dir.resolve("restored ünï.txt");
        compressor.compress(original, gz, false);
        compressor.decompress(gz, restored, false);
        assertArrayEquals(Files.readAllBytes(original), Files.readAllBytes(restored));
    }

    @Test
    void largeFileIsStreamedAndRoundTrips(@TempDir Path dir) throws IOException {
        Path original = dir.resolve("large.bin");
        TestData.writeLargeFile(original, 48L * 1024 * 1024);
        Path gz = dir.resolve("large.gz");
        Path restored = dir.resolve("large.out");

        compressor.compress(original, gz, false);
        compressor.decompress(gz, restored, false);

        assertEquals(Files.size(original), Files.size(restored));
        assertEquals(Sha256Hasher.hash(original), Sha256Hasher.hash(restored));
    }

    @Test
    void estimateMatchesRealCompressionAndWritesNothing(@TempDir Path dir) throws IOException {
        Path original = TestData.writeText(dir, "a.txt", "repeat ".repeat(10_000));
        CompressionStats estimate = compressor.estimate(original);
        CompressionStats real = compressor.compress(original, dir.resolve("a.gz"), false);
        assertEquals(real.originalBytes(), estimate.originalBytes());
        assertEquals(real.compressedBytes(), estimate.compressedBytes());
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(2, files.count());
        }
    }

    @Test
    void refusesToOverwriteWithoutForce(@TempDir Path dir) throws IOException {
        Path original = TestData.writeText(dir, "a.txt", "hello");
        Path gz = TestData.writeText(dir, "a.gz", "precious existing data");
        InvalidInputException e = assertThrows(InvalidInputException.class,
                () -> compressor.compress(original, gz, false));
        assertTrue(e.getMessage().contains("--force"));
        assertEquals("precious existing data", Files.readString(gz));
        assertNoLeftovers(dir);
    }

    @Test
    void overwritesWithForce(@TempDir Path dir) throws IOException {
        Path original = TestData.writeText(dir, "a.txt", "hello hello hello");
        Path gz = TestData.writeText(dir, "a.gz", "old");
        compressor.compress(original, gz, true);
        Path restored = dir.resolve("r.txt");
        compressor.decompress(gz, restored, false);
        assertEquals("hello hello hello", Files.readString(restored));
    }

    @Test
    void missingInputIsReported(@TempDir Path dir) {
        InvalidInputException e = assertThrows(InvalidInputException.class,
                () -> compressor.compress(dir.resolve("nope.txt"), dir.resolve("out.gz"), false));
        assertTrue(e.getMessage().contains("does not exist"));
    }

    @Test
    void directoryInputIsRejected(@TempDir Path dir) {
        assertThrows(InvalidInputException.class,
                () -> compressor.compress(dir, dir.resolve("out.gz"), false));
    }

    @Test
    void outputDirectoryMustExist(@TempDir Path dir) throws IOException {
        Path original = TestData.writeText(dir, "a.txt", "x");
        assertThrows(InvalidInputException.class,
                () -> compressor.compress(original, dir.resolve("missing/out.gz"), false));
    }

    @Test
    void outputMayNotBeTheInputEvenWithForce(@TempDir Path dir) throws IOException {
        Path original = TestData.writeText(dir, "a.txt", "keep me");
        assertThrows(InvalidInputException.class, () -> compressor.compress(original, original, true));
        assertEquals("keep me", Files.readString(original));
    }

    @Test
    void outputMayNotBeADirectory(@TempDir Path dir) throws IOException {
        Path original = TestData.writeText(dir, "a.txt", "x");
        Path targetDir = Files.createDirectory(dir.resolve("sub"));
        assertThrows(InvalidInputException.class, () -> compressor.compress(original, targetDir, true));
    }

    @Test
    void nonGzipInputIsCorruptAndLeavesNoOutput(@TempDir Path dir) throws IOException {
        Path bad = TestData.writeText(dir, "bad.gz", "this is plain text, not gzip");
        Path out = dir.resolve("out.txt");
        assertThrows(CorruptDataException.class, () -> compressor.decompress(bad, out, false));
        assertFalse(Files.exists(out));
        assertNoLeftovers(dir);
    }

    @Test
    void emptyInputIsCorrupt(@TempDir Path dir) throws IOException {
        Path empty = TestData.write(dir, "empty.gz", new byte[0]);
        assertThrows(CorruptDataException.class, () -> compressor.decompress(empty, dir.resolve("o"), false));
    }

    @Test
    void truncatedGzipIsCorruptAndLeavesNoOutput(@TempDir Path dir) throws IOException {
        Path original = TestData.write(dir, "data.bin", TestData.randomBytes(5, 100_000));
        Path gz = dir.resolve("data.gz");
        compressor.compress(original, gz, false);
        try (RandomAccessFile raf = new RandomAccessFile(gz.toFile(), "rw")) {
            raf.setLength(raf.length() / 2);
        }
        Path out = dir.resolve("out.bin");
        assertThrows(CorruptDataException.class, () -> compressor.decompress(gz, out, false));
        assertFalse(Files.exists(out), "no partial output may remain");
        assertNoLeftovers(dir);
    }

    @Test
    void corruptedGzipFailsCrcCheck(@TempDir Path dir) throws IOException {
        Path original = TestData.writeText(dir, "a.txt", "some reasonably long text. ".repeat(2000));
        Path gz = dir.resolve("a.gz");
        compressor.compress(original, gz, false);
        byte[] bytes = Files.readAllBytes(gz);
        bytes[bytes.length / 2] ^= 0x55;
        Files.write(gz, bytes);
        assertThrows(CorruptDataException.class, () -> compressor.decompress(gz, dir.resolve("o.txt"), false));
        assertNoLeftovers(dir);
    }

    @Test
    void failedForcedDecompressKeepsExistingOutput(@TempDir Path dir) throws IOException {
        Path bad = TestData.writeText(dir, "bad.gz", "not gzip");
        Path out = TestData.writeText(dir, "out.txt", "existing content");
        assertThrows(CorruptDataException.class, () -> compressor.decompress(bad, out, true));
        assertEquals("existing content", Files.readString(out));
    }

    @Test
    void matchesOriginalDetectsGoodAndBadPairs(@TempDir Path dir) throws IOException {
        Path a = TestData.writeText(dir, "a.txt", "alpha ".repeat(100));
        Path b = TestData.writeText(dir, "b.txt", "beta ".repeat(100));
        Path gzA = dir.resolve("a.gz");
        compressor.compress(a, gzA, false);
        assertTrue(compressor.matchesOriginal(a, gzA));
        assertFalse(compressor.matchesOriginal(b, gzA));
        Path garbage = TestData.writeText(dir, "g.gz", "garbage");
        assertThrows(CorruptDataException.class, () -> compressor.matchesOriginal(a, garbage));
    }

    @Test
    void unreadableInputIsReported(@TempDir Path dir) throws IOException {
        assumeTrue(TestData.isPosix(dir));
        Path original = TestData.writeText(dir, "secret.txt", "x");
        Files.setPosixFilePermissions(original, PosixFilePermissions.fromString("---------"));
        assumeFalse(Files.isReadable(original), "running as a user that bypasses permissions (e.g. root)");
        InvalidInputException e = assertThrows(InvalidInputException.class,
                () -> compressor.compress(original, dir.resolve("out.gz"), false));
        assertTrue(e.getMessage().contains("Permission denied"));
    }

    @Test
    void readOnlyOutputDirectoryIsReported(@TempDir Path dir) throws IOException {
        assumeTrue(TestData.isPosix(dir));
        Path original = TestData.writeText(dir, "a.txt", "x");
        Path locked = Files.createDirectory(dir.resolve("locked"));
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            assumeFalse(Files.isWritable(locked), "running as a user that bypasses permissions (e.g. root)");
            assertThrows(InvalidInputException.class,
                    () -> compressor.compress(original, locked.resolve("out.gz"), false));
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }
}
