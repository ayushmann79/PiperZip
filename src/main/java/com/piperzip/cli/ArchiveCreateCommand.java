package com.piperzip.cli;

import com.piperzip.archive.ArchiveResult;
import com.piperzip.archive.ZipArchiver;
import java.nio.file.Path;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

/** {@code piperzip archive create}. */
@Command(name = "create", mixinStandardHelpOptions = true,
        description = "Create a ZIP archive from files and directories.")
final class ArchiveCreateCommand extends BaseCommand {

    @Parameters(index = "0", paramLabel = "<output.zip>", description = "Archive to create.")
    private Path output;

    @Parameters(index = "1..*", arity = "1..*", paramLabel = "<input>",
            description = "Files and directories to add (directories are added recursively).")
    private List<Path> inputs;

    @Mixin
    private Mixins.Force force;

    @Mixin
    private Mixins.Level level;

    @Mixin
    private Mixins.JsonOutput json;

    @Override
    public Integer call() throws Exception {
        int zipLevel = level.validated(spec());
        ArchiveResult result = new ZipArchiver().create(inputs, output, force.force, zipLevel);
        if (json.json) {
            out().println("{\"operation\":\"archive-create\",\"output\":" + Json.quote(output.toString())
                    + ",\"format\":\"zip\",\"level\":" + zipLevel + ",\"entries\":" + result.entryCount()
                    + ",\"stats\":" + result.stats().toJson() + "}");
        } else {
            out().println("Created '" + output + "' with " + result.entryCount() + " entries (zip, level "
                    + zipLevel + ")");
            out().print(StatsFormatter.human(result.stats(), "Original size", "Archive size"));
            out().println("  (archive size includes ZIP container overhead)");
        }
        out().flush();
        return ExitCodes.OK;
    }
}
