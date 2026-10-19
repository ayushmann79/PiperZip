package com.piperzip.statistics;

import java.time.Duration;
import java.util.Locale;

/**
 * Immutable size and timing statistics for one operation.
 *
 * <p>For compression, {@code originalBytes} is the uncompressed size and {@code compressedBytes}
 * the compressed size. For decompression the same fields hold the decompressed and compressed
 * sizes respectively.
 *
 * @param originalBytes uncompressed size in bytes
 * @param compressedBytes compressed size in bytes
 * @param elapsed processing time
 */
public record CompressionStats(long originalBytes, long compressedBytes, Duration elapsed) {

    /** Validates the values. */
    public CompressionStats {
        if (originalBytes < 0 || compressedBytes < 0) {
            throw new IllegalArgumentException("sizes must not be negative");
        }
        if (elapsed == null || elapsed.isNegative()) {
            throw new IllegalArgumentException("elapsed must be a non-negative duration");
        }
    }

    /**
     * Bytes saved; negative when the output is larger than the input.
     *
     * @return original minus compressed size
     */
    public long bytesSaved() {
        return originalBytes - compressedBytes;
    }

    /**
     * Compression ratio as original/compressed, or 0 if the compressed size is zero.
     *
     * @return ratio (values above 1 mean the data shrank)
     */
    public double ratio() {
        return compressedBytes == 0 ? 0.0 : (double) originalBytes / compressedBytes;
    }

    /**
     * Size reduction as a percentage of the original; 0 for an empty original, negative when the
     * output grew.
     *
     * @return percentage reduction
     */
    public double percentReduction() {
        return originalBytes == 0 ? 0.0 : 100.0 * bytesSaved() / originalBytes;
    }

    /**
     * Renders the statistics as a JSON object.
     *
     * @return JSON text
     */
    public String toJson() {
        return String.format(Locale.ROOT,
                "{\"originalBytes\":%d,\"compressedBytes\":%d,\"bytesSaved\":%d,"
                        + "\"ratio\":%.4f,\"percentReduction\":%.2f,\"elapsedMillis\":%d}",
                originalBytes, compressedBytes, bytesSaved(), ratio(), percentReduction(),
                elapsed.toMillis());
    }
}
