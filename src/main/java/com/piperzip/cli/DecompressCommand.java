package com.piperzip.cli;

import com.piperzip.compression.FileCompressor;
import com.piperzip.compression.GzipCodec;
import com.piperzip.statistics.CompressionStats;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

/** {@code piperzip decompress}. */
@Command(name = "decompress", mixinStandardHelpOptions = true,
        description = "Decompress a GZIP file.")
final class DecompressCommand extends BaseCommand {

    @Parameters(index = "0", paramLabel = "<input.gz>", description = "GZIP file to decompress.")
    private Path input;

    @Parameters(index = "1", paramLabel = "<output>", description = "File to create.")
    private Path output;

    @Mixin
    private Mixins.Force force;

    @Mixin
    private Mixins.JsonOutput json;

    @Override
    public Integer call() throws Exception {
        CompressionStats stats = new FileCompressor(new GzipCodec()).decompress(input, output, force.force);
        if (json.json) {
            out().println("{\"operation\":\"decompress\",\"input\":" + Json.quote(input.toString())
                    + ",\"output\":" + Json.quote(output.toString()) + ",\"algorithm\":\"gzip\",\"stats\":"
                    + stats.toJson() + "}");
        } else {
            out().println("Decompressed '" + input + "' -> '" + output + "'");
            out().print(StatsFormatter.human(stats, "Decompressed size", "Compressed size"));
        }
        out().flush();
        return ExitCodes.OK;
    }
}
