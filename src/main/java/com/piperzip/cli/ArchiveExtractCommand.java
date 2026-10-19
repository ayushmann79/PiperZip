package com.piperzip.cli;

import com.piperzip.archive.ExtractionResult;
import com.piperzip.archive.SafeExtractor;
import com.piperzip.config.ExtractionLimits;
import com.piperzip.exception.InvalidInputException;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** {@code piperzip archive extract}. */
@Command(name = "extract", mixinStandardHelpOptions = true,
        description = "Safely extract a ZIP archive. Path traversal, symbolic-link tricks and "
                + "decompression bombs are rejected.")
final class ArchiveExtractCommand extends BaseCommand {

    @Parameters(index = "0", paramLabel = "<input.zip>", description = "Archive to extract.")
    private Path archive;

    @Parameters(index = "1", paramLabel = "<directory>", description = "Destination directory (created if missing).")
    private Path destination;

    @Mixin
    private Mixins.Force force;

    @Mixin
    private Mixins.JsonOutput json;

    @Option(names = "--max-entries", paramLabel = "<n>",
            description = "Maximum number of entries (default: 10000).")
    private Integer maxEntries;

    @Option(names = "--max-total-bytes", paramLabel = "<n>",
            description = "Maximum total uncompressed bytes (default: 1073741824, i.e. 1 GiB).")
    private Long maxTotalBytes;

    @Option(names = "--max-ratio", paramLabel = "<n>",
            description = "Maximum overall compression ratio, checked after 16 MiB (default: 200).")
    private Long maxRatio;

    @Override
    public Integer call() throws Exception {
        ExtractionLimits limits;
        try {
            limits = new ExtractionLimits(
                    maxEntries != null ? maxEntries : ExtractionLimits.DEFAULT_MAX_ENTRIES,
                    maxTotalBytes != null ? maxTotalBytes : ExtractionLimits.DEFAULT_MAX_TOTAL_BYTES,
                    maxRatio != null ? maxRatio : ExtractionLimits.DEFAULT_MAX_RATIO);
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException(e.getMessage(), e);
        }
        ExtractionResult result = new SafeExtractor(limits).extract(archive, destination, force.force);
        if (json.json) {
            out().println("{\"operation\":\"archive-extract\",\"archive\":" + Json.quote(archive.toString())
                    + ",\"destination\":" + Json.quote(destination.toString()) + ",\"entries\":"
                    + result.entryCount() + ",\"totalBytes\":" + result.totalBytes() + ",\"elapsedMillis\":"
                    + result.elapsed().toMillis() + "}");
        } else {
            out().println("Extracted " + result.entryCount() + " entries (" + String.format("%,d", result.totalBytes())
                    + " bytes) from '" + archive + "' into '" + destination + "' in "
                    + result.elapsed().toMillis() + " ms");
        }
        out().flush();
        return ExitCodes.OK;
    }
}
