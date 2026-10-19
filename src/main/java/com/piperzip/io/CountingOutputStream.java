package com.piperzip.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Output stream decorator that counts the bytes written through it. */
public final class CountingOutputStream extends FilterOutputStream {

    private long count;

    /**
     * Wraps the given stream.
     *
     * @param out the stream to delegate to
     */
    public CountingOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
        count++;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        count += len;
    }

    /**
     * Returns the number of bytes written so far.
     *
     * @return byte count
     */
    public long count() {
        return count;
    }
}
