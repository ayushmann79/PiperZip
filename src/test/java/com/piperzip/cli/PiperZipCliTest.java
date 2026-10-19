package com.piperzip.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.piperzip.testutil.TestData;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;

/** In-process CLI tests: no operating-system process is started. */
class PiperZipCliTest {

    private record Result(int exitCode, String out, String err) {
    }

    private static Result run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cli = PiperZipCli.create();
        cli.setOut(new PrintWriter(out));
        cli.setErr(new PrintWriter(err));
        int exit = cli.execute(args);
        return new Result(exit, out.toString(), err.toString());
    }

    private static String p(Path path) {
        return path.toString();
    }

    // ---- help, version, usage ------------------------------------------------------------

    @Test
    void helpListsAllCommandsAndExitCodes() {
        Result r = run("--help");
        assertEquals(0, r.exitCode());
        for (String word : new String[] {"compress", "decompress", "archive", "stats", "verify", "Exit codes"}) {
            assertTrue(r.out().contains(word), "help should mention " + word);
        }
    }

    @Test
    void versionIsPrinted() {
        Result r = run("--version");
        assertEquals(0, r.exitCode());
        assertTrue(r.out().startsWith("piperzip "), r.out());
    }

    @Test
    void noArgumentsShowsUsageWithUsageExitCode() {
        Result r = run();
        assertEquals(ExitCodes.USAGE, r.exitCode());
        assertTrue(r.err().contains("Usage: piperzip"));
    }

    @Test
    void archiveWithoutSubcommandShowsUsage() {
        Result r = run("archive");
        assertEquals(ExitCodes.USAGE, r.exitCode());
        assertTrue(r.err().contains("create"));
    }

    @Test
    void unknownCommandIsAUsageError() {
        assertEquals(ExitCodes.USAGE, run("frobnicate").exitCode());
    }

    @Test
    void missingArgumentsAreAUsageError() {
        assertEquals(ExitCodes.USAGE, run("compress", "only-one-arg").exitCode());
        assertEquals(ExitCodes.USAGE, run("archive", "create", "out.zip").exitCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "10", "-3"})
    void invalidLevelIsAUsageError(String level, @TempDir Path dir) throws IOException {
        Path in = TestData.writeText(dir, "a.txt", "x");
        Result r = run("compress", "--level", level, p(in), p(dir.resolve("a.gz")));
        assertEquals(ExitCodes.USAGE, r.exitCode());
        assertTrue(r.err().contains("--level"));
        assertFalse(Files.exists(dir.resolve("a.gz")));
    }

    // ---- complete workflows --------------------------------------------------------------

    @Test
    void compressDecompressVerifyWorkflow(@TempDir Path dir) throws IOException {
        Path original = TestData.writeText(dir, "input.txt", "PiperZip workflow test. ".repeat(5000));
        Path gz = dir.resolve("input.gz");
        Path restored = dir.resolve("restored.txt");

        Result compress = run("compress", p(original), p(gz));
        assertEquals(0, compress.exitCode(), compress.err());
        assertTrue(compress.out().contains("Compression ratio"));
        assertTrue(compress.out().contains("Size reduction"));

        Result decompress = run("decompress", p(gz), p(restored));
        assertEquals(0, decompress.exitCode(), decompress.err());
        assertArrayEquals(Files.readAllBytes(original), Files.readAllBytes(restored));

        Result verify = run("verify", p(original), p(restored));
        assertEquals(0, verify.exitCode());
        assertTrue(verify.out().contains("OK"));
    }

    @Test
    void compressWithVerifyFlagReportsVerification(@TempDir Path dir) throws IOException {
        Path original = TestData.writeText(dir, "a.txt", "abc".repeat(1000));
        Result r = run("compress", "--verify", p(original), p(dir.resolve("a.gz")));
        assertEquals(0, r.exitCode(), r.err());
        assertTrue(r.out().contains("Verified"));
    }

    @Test
    void archiveCreateAndExtractWorkflow(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("src");
        TestData.writeText(src, "a.txt", "alpha");
        TestData.writeText(src, "sub/b.txt", "beta");
        Path zip = dir.resolve("src.zip");
        Path out = dir.resolve("out");

        Result create = run("archive", "create", p(zip), p(src));
        assertEquals(0, create.exitCode(), create.err());
        assertTrue(create.out().contains("4 entries"));

        Result extract = run("archive", "extract", p(zip), p(out));
        assertEquals(0, extract.exitCode(), extract.err());
        assertEquals("alpha", Files.readString(out.resolve("src/a.txt")));
        assertEquals("beta", Files.readString(out.resolve("src/sub/b.txt")));
    }

    @Test
    void statsDoesNotWriteAnyFile(@TempDir Path dir) throws IOException {
        Path original = TestData.writeText(dir, "a.txt", "zzz".repeat(10_000));
        Result r = run("stats", p(original));
        assertEquals(0, r.exitCode(), r.err());
        assertTrue(r.out().contains("nothing written"));
        try (var files = Files.list(dir)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void statsWarnsWhenDataDoesNotCompress(@TempDir Path dir) throws IOException {
        Path random = TestData.write(dir, "r.bin", TestData.randomBytes(1, 2000));
        Result r = run("stats", p(random));
        assertEquals(0, r.exitCode());
        assertTrue(r.out().contains("does not compress well"));
    }

    // ---- JSON ----------------------------------------------------------------------------

    @Test
    void jsonOutputIsMachineReadable(@TempDir Path dir) throws IOException {
        Path original = TestData.writeText(dir, "a quoted.txt", "hello ".repeat(1000));
        Result r = run("compress", "--json", p(original), p(dir.resolve("a.gz")));
        assertEquals(0, r.exitCode(), r.err());
        String json = r.out().trim();
        assertTrue(json.startsWith("{") && json.endsWith("}"));
        assertTrue(json.contains("\"operation\":\"compress\""));
        assertTrue(json.contains("\"originalBytes\":6000"));
        assertTrue(json.contains("a quoted.txt"), "JSON should contain the input path: " + json);
        assertEquals(1, json.lines().count(), "JSON must be one line with no extra text");
    }

    @Test
    void verifyJsonReportsMatch(@TempDir Path dir) throws IOException {
        Path a = TestData.writeText(dir, "a", "same");
        Path b = TestData.writeText(dir, "b", "same");
        Result r = run("verify", "--json", p(a), p(b));
        assertEquals(0, r.exitCode());
        assertTrue(r.out().contains("\"match\":true"));
    }

    // ---- error handling and exit codes -------------------------------------------------

    @Test
    void missingInputExitsWithInvalidInput(@TempDir Path dir) {
        Result r = run("compress", p(dir.resolve("nope.txt")), p(dir.resolve("o.gz")));
        assertEquals(ExitCodes.INVALID_INPUT, r.exitCode());
        assertTrue(r.err().startsWith("Error: Input file does not exist"));
        assertEquals("", r.out());
    }

    @Test
    void existingOutputIsRefusedThenAllowedWithForce(@TempDir Path dir) throws IOException {
        Path in = TestData.writeText(dir, "a.txt", "x".repeat(100));
        Path gz = dir.resolve("a.gz");
        assertEquals(0, run("compress", p(in), p(gz)).exitCode());
        Result refused = run("compress", p(in), p(gz));
        assertEquals(ExitCodes.INVALID_INPUT, refused.exitCode());
        assertTrue(refused.err().contains("--force"));
        assertEquals(0, run("compress", "--force", p(in), p(gz)).exitCode());
    }

    @Test
    void corruptGzipExitsWithCorruptDataCode(@TempDir Path dir) throws IOException {
        Path bad = TestData.writeText(dir, "bad.gz", "not gzip");
        Result r = run("decompress", p(bad), p(dir.resolve("out")));
        assertEquals(ExitCodes.CORRUPT_DATA, r.exitCode());
        assertFalse(Files.exists(dir.resolve("out")));
    }

    @Test
    void zipSlipArchiveExitsWithUnsafeArchiveCode(@TempDir Path dir) throws IOException {
        Path zip = TestData.zipWithEntries(dir.resolve("evil.zip"), "../evil.txt");
        Result r = run("archive", "extract", p(zip), p(dir.resolve("dest")));
        assertEquals(ExitCodes.UNSAFE_ARCHIVE, r.exitCode());
        assertTrue(r.err().contains("path traversal"));
        assertFalse(Files.exists(dir.resolve("evil.txt")));
    }

    @Test
    void extractionLimitOptionsAreHonoured(@TempDir Path dir) throws IOException {
        Path zip = TestData.zipWithEntries(dir.resolve("a.zip"), "1.txt", "2.txt", "3.txt");
        Result r = run("archive", "extract", "--max-entries", "2", p(zip), p(dir.resolve("dest")));
        assertEquals(ExitCodes.UNSAFE_ARCHIVE, r.exitCode());
        assertTrue(r.err().contains("--max-entries"));
    }

    @Test
    void nonPositiveLimitIsInvalidInput(@TempDir Path dir) throws IOException {
        Path zip = TestData.zipWithEntries(dir.resolve("a.zip"), "1.txt");
        Result r = run("archive", "extract", "--max-entries", "0", p(zip), p(dir.resolve("dest")));
        assertEquals(ExitCodes.INVALID_INPUT, r.exitCode());
    }

    @Test
    void verifyMismatchHasItsOwnExitCode(@TempDir Path dir) throws IOException {
        Path a = TestData.writeText(dir, "a", "one");
        Path b = TestData.writeText(dir, "b", "two");
        Result r = run("verify", p(a), p(b));
        assertEquals(ExitCodes.VERIFY_MISMATCH, r.exitCode());
        assertTrue(r.out().contains("MISMATCH"));
    }

    @Test
    void verifyOfMissingFileIsInvalidInput(@TempDir Path dir) throws IOException {
        Path a = TestData.writeText(dir, "a", "one");
        assertEquals(ExitCodes.INVALID_INPUT, run("verify", p(a), p(dir.resolve("missing"))).exitCode());
    }

    @Test
    void exitCodeMappingCoversEveryCategory() {
        assertEquals(ExitCodes.IO_ERROR, ExitCodes.forException(new IOException("x")));
        assertEquals(ExitCodes.IO_ERROR, ExitCodes.forException(new com.piperzip.exception.PiperZipException("x")));
        assertEquals(ExitCodes.INTERNAL_ERROR, ExitCodes.forException(new IllegalStateException("x")));
    }

    @Test
    void versionFallsBackToDevWhenResourceIsNotFiltered() {
        String version = VersionProvider.version();
        assertFalse(version.contains("${"));
    }
}
