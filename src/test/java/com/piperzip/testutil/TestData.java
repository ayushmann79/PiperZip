package com.piperzip.testutil;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Deterministic test data helpers. No test depends on personal files or the network. */
public final class TestData {

    private TestData() {
    }

    public static byte[] randomBytes(long seed, int length) {
        byte[] bytes = new byte[length];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }

    public static Path write(Path dir, String name, byte[] content) throws IOException {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
        return file;
    }

    public static Path writeText(Path dir, String name, String text) throws IOException {
        return write(dir, name, text.getBytes(StandardCharsets.UTF_8));
    }

    /** Writes a (possibly hostile) ZIP with exactly the given entry names; contents are the names. */
    public static Path zipWithEntries(Path file, String... names) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            for (String name : names) {
                zip.putNextEntry(new ZipEntry(name));
                if (!name.endsWith("/")) {
                    zip.write(name.getBytes(StandardCharsets.UTF_8));
                }
                zip.closeEntry();
            }
        }
        return file;
    }

    /** Writes a ZIP with one entry of {@code size} zero bytes (highly compressible). */
    public static Path zipOfZeros(Path file, String name, long size) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry(name));
            byte[] chunk = new byte[1 << 16];
            long remaining = size;
            while (remaining > 0) {
                int n = (int) Math.min(chunk.length, remaining);
                zip.write(chunk, 0, n);
                remaining -= n;
            }
            zip.closeEntry();
        }
        return file;
    }

    /** Deterministic compressible stream used to exercise streaming without holding it in memory. */
    public static void writeLargeFile(Path file, long size) throws IOException {
        Random random = new Random(42);
        byte[] block = new byte[1 << 16];
        try (OutputStream out = Files.newOutputStream(file)) {
            long remaining = size;
            while (remaining > 0) {
                random.nextBytes(block);
                for (int i = 0; i < block.length; i += 2) {
                    block[i] &= 0x0F; // lower entropy so Deflate has something to do
                }
                int n = (int) Math.min(block.length, remaining);
                out.write(block, 0, n);
                remaining -= n;
            }
        }
    }

    /** True if the JVM can map non-ASCII file names to the file system (false under the POSIX "C" locale). */
    public static boolean supportsUnicodeFileNames() {
        String encoding = System.getProperty("sun.jnu.encoding", "UTF-8");
        try {
            return java.nio.charset.Charset.forName(encoding).newEncoder().canEncode("日本語é🚀");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean isPosix(Path path) {
        return path.getFileSystem().supportedFileAttributeViews().contains("posix");
    }
}
