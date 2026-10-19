package com.piperzip.config;

/**
 * Resource limits applied while extracting an archive.
 *
 * <p>These limits bound the damage a hostile archive can do (decompression bombs, inode
 * exhaustion). They are heuristics, not guarantees: a determined attacker can stay just under
 * any limit, so extract untrusted archives only into disposable, quota-limited locations.
 *
 * @param maxEntries maximum number of entries (files and directories)
 * @param maxTotalBytes maximum total uncompressed bytes written
 * @param maxCompressionRatio maximum overall uncompressed/archive size ratio, checked only once
 *     more than {@link #RATIO_CHECK_FLOOR_BYTES} have been produced
 */
public record ExtractionLimits(int maxEntries, long maxTotalBytes, long maxCompressionRatio) {

    /** Default maximum entry count. */
    public static final int DEFAULT_MAX_ENTRIES = 10_000;

    /** Default maximum total uncompressed size: 1 GiB. */
    public static final long DEFAULT_MAX_TOTAL_BYTES = 1L << 30;

    /** Default maximum compression ratio. */
    public static final long DEFAULT_MAX_RATIO = 200;

    /** Ratio checks start only after this many bytes, to avoid flagging small, repetitive files. */
    public static final long RATIO_CHECK_FLOOR_BYTES = 16L << 20;

    /** Validates the limits. */
    public ExtractionLimits {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("max entries must be at least 1");
        }
        if (maxTotalBytes < 1) {
            throw new IllegalArgumentException("max total bytes must be at least 1");
        }
        if (maxCompressionRatio < 1) {
            throw new IllegalArgumentException("max ratio must be at least 1");
        }
    }

    /**
     * Returns the default limits.
     *
     * @return default limits
     */
    public static ExtractionLimits defaults() {
        return new ExtractionLimits(DEFAULT_MAX_ENTRIES, DEFAULT_MAX_TOTAL_BYTES, DEFAULT_MAX_RATIO);
    }
}
