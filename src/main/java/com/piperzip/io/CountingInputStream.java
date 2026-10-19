package com.piperzip.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Input stream decorator that counts the bytes read through it. */
public final class CountingInputStream extends FilterInputStream {

    private long count;

    /**
     * Wraps the given stream.
     *
     * @param in the stream to delegate to
     */
    public CountingInputStream(InputStream in) {
        super(in);
    }

    @Override
    public int read() throws IOException {
        int b = in.read();
        if (b >= 0) {
            count++;
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = in.read(b, off, len);
        if (n > 0) {
            count += n;
        }
        return n;
    }

    /**
     * Returns the number of bytes read so far.
     *
     * @return byte count
     */
    public long count() {
        return count;
    }
}
