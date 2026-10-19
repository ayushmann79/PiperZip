package com.piperzip.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Output stream decorator whose {@code close()} only flushes. This lets a codec close its own
 * compressor (releasing native memory) without closing the caller's stream.
 */
public final class CloseShieldOutputStream extends FilterOutputStream {

    /**
     * Wraps the given stream.
     *
     * @param out the stream to delegate to
     */
    public CloseShieldOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    @Override
    public void close() throws IOException {
        out.flush();
    }
}
