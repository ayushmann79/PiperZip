package com.piperzip.cli;

import com.piperzip.compression.FileCompressor;
import com.piperzip.compression.GzipCodec;
import com.piperzip.statistics.CompressionStats;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

/** {@code piperzip stats}. */
@Command(name = "stats", mixinStandardHelpOptions = true,
        description = "Show how well a file would compress with GZIP, without writing any file.")
final class StatsCommand extends BaseCommand {

    @Parameters(index = "0", paramLabel = "<input>", description = "File to analyse.")
    private Path input;

    @Mixin
    private Mixins.Level level;

    @Mixin
    private Mixins.JsonOutput json;

    @Override
    public Integer call() throws Exception {
        int gzipLevel = level.validated(spec());
        CompressionStats stats = new FileCompressor(new GzipCodec(gzipLevel)).estimate(input);
        if (json.json) {
            out().println("{\"operation\":\"stats\",\"input\":" + Json.quote(input.toString())
                    + ",\"algorithm\":\"gzip\",\"level\":" + gzipLevel + ",\"stats\":" + stats.toJson() + "}");
        } else {
            out().println("Estimated GZIP result for '" + input + "' (level " + gzipLevel + ", nothing written)");
            out().print(StatsFormatter.human(stats, "Original size", "Compressed size"));
        }
        out().flush();
        return ExitCodes.OK;
    }
}
