package com.piperzip.compression;

import com.piperzip.exception.CorruptDataException;
import com.piperzip.integrity.Sha256Hasher;
import com.piperzip.io.AtomicFileWriter;
import com.piperzip.io.CountingInputStream;
import com.piperzip.io.CountingOutputStream;
import com.piperzip.statistics.CompressionStats;
import com.piperzip.validation.PathValidator;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipException;

/**
 * Compresses and decompresses single files with a {@link CompressionCodec}. All file output goes
 * through {@link AtomicFileWriter}, so a failure never leaves a partial file behind.
 */
public final class FileCompressor {

    private final CompressionCodec codec;

    /**
     * Creates a compressor.
     *
     * @param codec the algorithm to use
     */
    public FileCompressor(CompressionCodec codec) {
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    /**
     * Compresses {@code input} into {@code output}.
     *
     * @param input file to compress
     * @param output destination file
     * @param overwrite whether an existing output may be replaced
     * @return size and timing statistics
     * @throws IOException on I/O failure
     */
    public CompressionStats compress(Path input, Path output, boolean overwrite) throws IOException {
        PathValidator.requireReadableFile(input);
        PathValidator.requireWritableTarget(output, overwrite, input);
        long start = System.nanoTime();
        AtomicLong originalBytes = new AtomicLong();
        AtomicFileWriter.write(output, overwrite, out -> {
            try (CountingInputStream in = new CountingInputStream(Files.newInputStream(input))) {
                codec.compress(in, out);
                originalBytes.set(in.count());
            }
        });
        return new CompressionStats(originalBytes.get(), Files.size(output), elapsedSince(start));
    }

    /**
     * Computes what compression would produce, without writing any file.
     *
     * @param input file to analyse
     * @return estimated statistics
     * @throws IOException on I/O failure
     */
    public CompressionStats estimate(Path input) throws IOException {
        PathValidator.requireReadableFile(input);
        long start = System.nanoTime();
        try (CountingInputStream in = new CountingInputStream(Files.newInputStream(input));
                CountingOutputStream sink = new CountingOutputStream(OutputStream.nullOutputStream())) {
            codec.compress(in, sink);
            return new CompressionStats(in.count(), sink.count(), elapsedSince(start));
        }
    }

    /**
     * Decompresses {@code input} into {@code output}.
     *
     * @param input compressed file
     * @param output destination file
     * @param overwrite whether an existing output may be replaced
     * @return statistics where "original" is the decompressed size
     * @throws IOException on I/O failure
     * @throws CorruptDataException if the input is not valid, complete compressed data
     */
    public CompressionStats decompress(Path input, Path output, boolean overwrite) throws IOException {
        PathValidator.requireReadableFile(input);
        PathValidator.requireWritableTarget(output, overwrite, input);
        long start = System.nanoTime();
        AtomicLong decompressedBytes = new AtomicLong();
        try {
            AtomicFileWriter.write(output, overwrite, out -> {
                try (InputStream in = Files.newInputStream(input);
                        CountingOutputStream counting = new CountingOutputStream(out)) {
                    codec.decompress(in, counting);
                    decompressedBytes.set(counting.count());
                }
            });
        } catch (ZipException | EOFException e) {
            throw corrupt(input, e);
        }
        return new CompressionStats(decompressedBytes.get(), Files.size(input), elapsedSince(start));
    }

    /**
     * Checks that decompressing {@code compressed} reproduces {@code original} exactly, by comparing
     * SHA-256 digests. Nothing is written to disk.
     *
     * @param original the original file
     * @param compressed the compressed file
     * @return {@code true} if the restored bytes are identical to the original
     * @throws IOException on I/O failure
     * @throws CorruptDataException if the compressed file is invalid
     */
    public boolean matchesOriginal(Path original, Path compressed) throws IOException {
        PathValidator.requireReadableFile(original);
        PathValidator.requireReadableFile(compressed);
        String expected = Sha256Hasher.hash(original);
        MessageDigest digest = Sha256Hasher.newDigest();
        try (InputStream in = Files.newInputStream(compressed);
                DigestOutputStream sink = new DigestOutputStream(OutputStream.nullOutputStream(), digest)) {
            codec.decompress(in, sink);
        } catch (ZipException | EOFException e) {
            throw corrupt(compressed, e);
        }
        return expected.equals(Sha256Hasher.toHex(digest.digest()));
    }

    private CorruptDataException corrupt(Path input, IOException cause) {
        return new CorruptDataException(
                "Not a valid or complete " + codec.name() + " file: " + input + " (" + cause.getMessage() + ")",
                cause);
    }

    private static Duration elapsedSince(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }
}
