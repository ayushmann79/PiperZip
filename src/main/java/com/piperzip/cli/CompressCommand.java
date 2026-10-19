package com.piperzip.cli;

import com.piperzip.compression.FileCompressor;
import com.piperzip.compression.GzipCodec;
import com.piperzip.statistics.CompressionStats;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** {@code piperzip compress}. */
@Command(name = "compress", mixinStandardHelpOptions = true,
        description = "Compress a single file with GZIP.")
final class CompressCommand extends BaseCommand {

    @Parameters(index = "0", paramLabel = "<input>", description = "File to compress.")
    private Path input;

    @Parameters(index = "1", paramLabel = "<output.gz>", description = "GZIP file to create.")
    private Path output;

    @Mixin
    private Mixins.Force force;

    @Mixin
    private Mixins.Level level;

    @Mixin
    private Mixins.JsonOutput json;

    @Option(names = "--verify",
            description = "After compressing, decompress in memory and confirm the SHA-256 matches the original.")
    private boolean verify;

    @Override
    public Integer call() throws Exception {
        int gzipLevel = level.validated(spec());
        FileCompressor compressor = new FileCompressor(new GzipCodec(gzipLevel));
        CompressionStats stats = compressor.compress(input, output, force.force);
        boolean verified = false;
        if (verify) {
            if (!compressor.matchesOriginal(input, output)) {
                throw new com.piperzip.exception.CorruptDataException(
                        "Verification failed: the compressed file does not restore to the original: " + output);
            }
            verified = true;
        }
        if (json.json) {
            out().println("{\"operation\":\"compress\",\"input\":" + Json.quote(input.toString())
                    + ",\"output\":" + Json.quote(output.toString()) + ",\"algorithm\":\"gzip\",\"level\":"
                    + gzipLevel + ",\"verified\":" + verified + ",\"stats\":" + stats.toJson() + "}");
        } else {
            out().println("Compressed '" + input + "' -> '" + output + "' (gzip, level " + gzipLevel + ")");
            out().print(StatsFormatter.human(stats, "Original size", "Compressed size"));
            if (verified) {
                out().println("  Verified          : restored data matches the original (SHA-256)");
            }
        }
        out().flush();
        return ExitCodes.OK;
    }
}
