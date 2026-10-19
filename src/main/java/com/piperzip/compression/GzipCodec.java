package com.piperzip.compression;

import com.piperzip.io.CloseShieldOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * GZIP (RFC 1952) codec backed by {@code java.util.zip}. GZIP wraps a single Deflate stream with
 * a small header and a CRC-32 trailer; it is a compression format, not an archive format.
 */
public final class GzipCodec implements CompressionCodec {

    private static final int BUFFER_SIZE = 64 * 1024;

    private final int level;

    /** Creates a codec using the default compression level (6). */
    public GzipCodec() {
        this(Deflater.DEFAULT_COMPRESSION);
    }

    /**
     * Creates a codec with an explicit level.
     *
     * @param level {@code -1} (default) or 0 (store) to 9 (smallest)
     */
    public GzipCodec(int level) {
        if (level != Deflater.DEFAULT_COMPRESSION && (level < 0 || level > 9)) {
            throw new IllegalArgumentException("compression level must be -1 or between 0 and 9: " + level);
        }
        this.level = level;
    }

    @Override
    public String name() {
        return "gzip";
    }

    @Override
    public void compress(InputStream in, OutputStream out) throws IOException {
        try (GZIPOutputStream gzip = new LevelGzipOutputStream(new CloseShieldOutputStream(out), level)) {
            in.transferTo(gzip);
        }
    }

    @Override
    public void decompress(InputStream in, OutputStream out) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(in, BUFFER_SIZE)) {
            gzip.transferTo(out);
        }
    }

    /** {@link GZIPOutputStream} does not expose the level, but its protected deflater does. */
    private static final class LevelGzipOutputStream extends GZIPOutputStream {

        LevelGzipOutputStream(OutputStream out, int level) throws IOException {
            super(out, BUFFER_SIZE);
            def.setLevel(level);
        }
    }
}
