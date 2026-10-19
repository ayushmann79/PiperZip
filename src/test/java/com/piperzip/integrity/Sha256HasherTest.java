package com.piperzip.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.piperzip.testutil.TestData;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Sha256HasherTest {

    @Test
    void emptyInputMatchesKnownVector() throws IOException {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                Sha256Hasher.hash(new ByteArrayInputStream(new byte[0])));
    }

    @Test
    void abcMatchesKnownVector() throws IOException {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Sha256Hasher.hash(new ByteArrayInputStream("abc".getBytes(StandardCharsets.US_ASCII))));
    }

    @Test
    void fileHashEqualsStreamHash(@TempDir Path dir) throws IOException {
        byte[] data = TestData.randomBytes(1, 200_000);
        Path file = TestData.write(dir, "data.bin", data);
        assertEquals(Sha256Hasher.hash(new ByteArrayInputStream(data)), Sha256Hasher.hash(file));
    }
}
