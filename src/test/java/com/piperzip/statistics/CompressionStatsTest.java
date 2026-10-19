package com.piperzip.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CompressionStatsTest {

    @Test
    void computesSavingsRatioAndPercentage() {
        CompressionStats stats = new CompressionStats(1000, 250, Duration.ofMillis(5));
        assertEquals(750, stats.bytesSaved());
        assertEquals(4.0, stats.ratio(), 1e-9);
        assertEquals(75.0, stats.percentReduction(), 1e-9);
    }

    @Test
    void emptyOriginalHasZeroReduction() {
        CompressionStats stats = new CompressionStats(0, 20, Duration.ZERO);
        assertEquals(-20, stats.bytesSaved());
        assertEquals(0.0, stats.percentReduction(), 1e-9);
        assertEquals(0.0, stats.ratio(), 1e-9);
    }

    @Test
    void growthIsReportedAsNegativeSavings() {
        CompressionStats stats = new CompressionStats(100, 123, Duration.ZERO);
        assertEquals(-23, stats.bytesSaved());
        assertTrue(stats.percentReduction() < 0);
        assertTrue(stats.ratio() < 1);
    }

    @Test
    void zeroCompressedSizeDoesNotDivideByZero() {
        assertEquals(0.0, new CompressionStats(10, 0, Duration.ZERO).ratio(), 1e-9);
    }

    @Test
    void rejectsNegativeInputs() {
        assertThrows(IllegalArgumentException.class, () -> new CompressionStats(-1, 0, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new CompressionStats(0, -1, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new CompressionStats(0, 0, Duration.ofMillis(-1)));
        assertThrows(IllegalArgumentException.class, () -> new CompressionStats(0, 0, null));
    }

    @Test
    void jsonUsesLocaleIndependentNumbers() {
        String json = new CompressionStats(1000, 250, Duration.ofMillis(12)).toJson();
        assertEquals("{\"originalBytes\":1000,\"compressedBytes\":250,\"bytesSaved\":750,"
                + "\"ratio\":4.0000,\"percentReduction\":75.00,\"elapsedMillis\":12}", json);
    }
}
