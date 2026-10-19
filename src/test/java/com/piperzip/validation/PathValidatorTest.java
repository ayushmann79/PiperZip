package com.piperzip.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.piperzip.exception.InvalidInputException;
import com.piperzip.testutil.TestData;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PathValidatorTest {

    @Test
    void acceptsRegularFile(@TempDir Path dir) throws IOException {
        Path file = TestData.writeText(dir, "a", "x");
        assertDoesNotThrow(() -> PathValidator.requireReadableFile(file));
    }

    @Test
    void rejectsMissingAndDirectory(@TempDir Path dir) {
        assertThrows(InvalidInputException.class, () -> PathValidator.requireReadableFile(dir.resolve("x")));
        assertThrows(InvalidInputException.class, () -> PathValidator.requireReadableFile(dir));
    }

    @Test
    void writableTargetRules(@TempDir Path dir) throws IOException {
        Path existing = TestData.writeText(dir, "e", "x");
        assertDoesNotThrow(() -> PathValidator.requireWritableTarget(dir.resolve("new"), false));
        assertThrows(InvalidInputException.class, () -> PathValidator.requireWritableTarget(existing, false));
        assertDoesNotThrow(() -> PathValidator.requireWritableTarget(existing, true));
        assertThrows(InvalidInputException.class, () -> PathValidator.requireWritableTarget(existing, true, existing));
        assertThrows(InvalidInputException.class, () -> PathValidator.requireWritableTarget(dir, true));
        assertThrows(InvalidInputException.class,
                () -> PathValidator.requireWritableTarget(dir.resolve("no/such/dir/x"), false));
    }
}
