package com.piperzip.archive;

import com.piperzip.statistics.CompressionStats;

/**
 * Result of creating a ZIP archive.
 *
 * @param stats sizes and timing; "compressed" is the whole archive file, including ZIP overhead
 * @param entryCount number of entries (files and directories) written
 */
public record ArchiveResult(CompressionStats stats, int entryCount) {
}
