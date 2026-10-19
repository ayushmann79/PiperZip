package com.piperzip.compression;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A lossless stream compression algorithm. This is the extension point for future algorithms
 * (for example LZ4 or Zstandard): implement this interface and the rest of the application
 * keeps working unchanged.
 */
public interface CompressionCodec {

    /**
     * Short lowercase identifier, for example {@code "gzip"}.
     *
     * @return the codec name
     */
    String name();

    /**
     * Compresses all of {@code in} into {@code out}. Neither stream is closed.
     *
     * @param in uncompressed data
     * @param out receives compressed data
     * @throws IOException on I/O failure
     */
    void compress(InputStream in, OutputStream out) throws IOException;

    /**
     * Decompresses all of {@code in} into {@code out}. Neither stream is closed.
     *
     * @param in compressed data
     * @param out receives uncompressed data
     * @throws IOException on I/O failure or malformed input
     */
    void decompress(InputStream in, OutputStream out) throws IOException;
}
