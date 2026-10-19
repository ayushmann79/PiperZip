package com.piperzip.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.piperzip.exception.InvalidInputException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFileWriterTest {

    @Test
    void writesFile(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("out.txt");
        AtomicFileWriter.write(target, false, out -> out.write("hi".getBytes(StandardCharsets.UTF_8)));
        assertEquals("hi", Files.readString(target));
    }

    @Test
    void failureRemovesTemporaryFileAndKeepsExistingTarget(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("out.txt");
        Files.writeString(target, "old");
        assertThrows(IOException.class, () -> AtomicFileWriter.write(target, true, out -> {
            out.write(1);
            throw new IOException("boom");
        }));
        assertEquals("old", Files.readString(target));
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void refusesExistingTargetWithoutOverwrite(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("out.txt");
        Files.writeString(target, "old");
        assertThrows(InvalidInputException.class, () -> AtomicFileWriter.write(target, false, out -> out.write(1)));
        assertEquals("old", Files.readString(target));
    }

    @Test
    void countingStreamsCountBytes() throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (CountingOutputStream out = new CountingOutputStream(sink);
                CountingInputStream in = new CountingInputStream(new ByteArrayInputStream(new byte[1234]))) {
            in.transferTo(out);
            out.write(7);
            assertEquals(1234, in.count());
            assertEquals(1235, out.count());
        }
    }

    @Test
    void closeShieldDoesNotCloseUnderlyingStream() throws IOException {
        boolean[] closed = new boolean[1];
        ByteArrayOutputStream sink = new ByteArrayOutputStream() {
            @Override
            public void close() throws IOException {
                closed[0] = true;
            }
        };
        CloseShieldOutputStream shield = new CloseShieldOutputStream(sink);
        shield.write(new byte[] {1, 2, 3}, 0, 3);
        shield.close();
        assertFalse(closed[0]);
        assertEquals(3, sink.size());
    }
}
