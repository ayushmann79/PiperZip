package com.piperzip.compression;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.piperzip.testutil.TestData;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GzipCodecTest {

    private static byte[] roundTrip(GzipCodec codec, byte[] data) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        codec.compress(new ByteArrayInputStream(data), compressed);
        ByteArrayOutputStream restored = new ByteArrayOutputStream();
        codec.decompress(new ByteArrayInputStream(compressed.toByteArray()), restored);
        return restored.toByteArray();
    }

    @Test
    void emptyInputRoundTrips() throws IOException {
        assertArrayEquals(new byte[0], roundTrip(new GzipCodec(), new byte[0]));
    }

    @Test
    void binaryDataRoundTrips() throws IOException {
        byte[] data = TestData.randomBytes(7, 300_000);
        assertArrayEquals(data, roundTrip(new GzipCodec(), data));
    }

    @Test
    void unicodeTextRoundTrips() throws IOException {
        byte[] data = "héllo wörld, 日本語, русский, 🚀".repeat(1000).getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(data, roundTrip(new GzipCodec(), data));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 6, 9})
    void everyValidLevelRoundTrips(int level) throws IOException {
        byte[] data = "abcabcabc".repeat(5000).getBytes(StandardCharsets.US_ASCII);
        assertArrayEquals(data, roundTrip(new GzipCodec(level), data));
    }

    @Test
    void higherLevelIsNotLargerOnCompressibleData() throws IOException {
        byte[] data = "the quick brown fox jumps over the lazy dog ".repeat(5000).getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream fast = new ByteArrayOutputStream();
        ByteArrayOutputStream best = new ByteArrayOutputStream();
        new GzipCodec(1).compress(new ByteArrayInputStream(data), fast);
        new GzipCodec(9).compress(new ByteArrayInputStream(data), best);
        assertTrue(best.size() <= fast.size());
    }

    @ParameterizedTest
    @ValueSource(ints = {-2, 10, 100})
    void rejectsInvalidLevels(int level) {
        assertThrows(IllegalArgumentException.class, () -> new GzipCodec(level));
    }

    @Test
    void nonGzipInputIsRejected() {
        assertThrows(ZipException.class, () -> new GzipCodec().decompress(
                new ByteArrayInputStream("definitely not gzip".getBytes(StandardCharsets.US_ASCII)),
                new ByteArrayOutputStream()));
    }

    @Test
    void doesNotCloseCallerStream() throws IOException {
        boolean[] closed = new boolean[1];
        ByteArrayOutputStream sink = new ByteArrayOutputStream() {
            @Override
            public void close() throws IOException {
                closed[0] = true;
                super.close();
            }
        };
        new GzipCodec().compress(new ByteArrayInputStream(new byte[10]), sink);
        assertTrue(!closed[0], "compress() must leave closing to the caller");
    }
}
