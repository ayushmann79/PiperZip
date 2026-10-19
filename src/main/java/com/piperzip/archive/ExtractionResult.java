package com.piperzip.archive;

import java.time.Duration;

/**
 * Result of extracting a ZIP archive.
 *
 * @param entryCount number of entries (files and directories) extracted
 * @param totalBytes total uncompressed bytes written
 * @param elapsed processing time
 */
public record ExtractionResult(int entryCount, long totalBytes, Duration elapsed) {
}
