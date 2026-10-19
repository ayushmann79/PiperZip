package com.piperzip.cli;

import com.piperzip.statistics.CompressionStats;
import java.util.Locale;

/** Renders {@link CompressionStats} for humans. */
final class StatsFormatter {

    private StatsFormatter() {
    }

    static String human(CompressionStats stats, String originalLabel, String compressedLabel) {
        StringBuilder sb = new StringBuilder();
        sb.append(line(originalLabel, bytes(stats.originalBytes())));
        sb.append(line(compressedLabel, bytes(stats.compressedBytes())));
        sb.append(line("Bytes saved", bytes(stats.bytesSaved())));
        sb.append(line("Compression ratio", String.format(Locale.ROOT, "%.2f:1", stats.ratio())));
        sb.append(line("Size reduction", String.format(Locale.ROOT, "%.2f%%", stats.percentReduction())));
        sb.append(line("Elapsed", stats.elapsed().toMillis() + " ms"));
        if (stats.bytesSaved() < 0) {
            sb.append("  Note: the output is larger than the input; this data does not compress well.")
                    .append(System.lineSeparator());
        }
        return sb.toString();
    }

    private static String line(String label, String value) {
        return String.format(Locale.ROOT, "  %-18s: %s%n", label, value);
    }

    private static String bytes(long value) {
        return String.format(Locale.ROOT, "%,d bytes", value);
    }
}
