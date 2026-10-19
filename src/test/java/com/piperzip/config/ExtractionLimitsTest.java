package com.piperzip.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ExtractionLimitsTest {

    @Test
    void defaultsAreDocumentedValues() {
        ExtractionLimits limits = ExtractionLimits.defaults();
        assertEquals(10_000, limits.maxEntries());
        assertEquals(1L << 30, limits.maxTotalBytes());
        assertEquals(200, limits.maxCompressionRatio());
    }

    @Test
    void rejectsNonPositiveValues() {
        assertThrows(IllegalArgumentException.class, () -> new ExtractionLimits(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionLimits(1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionLimits(1, 1, 0));
    }
}
