package com.piperzip.cli;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

/** Reusable option groups. */
final class Mixins {

    private Mixins() {
    }

    /** {@code --json} flag. */
    static final class JsonOutput {
        @Option(names = "--json", description = "Print the result as a JSON object instead of text.")
        boolean json;
    }

    /** {@code --force} flag. */
    static final class Force {
        @Option(names = {"-f", "--force"}, description = "Overwrite existing output files.")
        boolean force;
    }

    /** {@code --level} option. */
    static final class Level {
        @Option(names = {"-l", "--level"}, paramLabel = "<1-9>", defaultValue = "6",
                description = "Compression level: 1 (fastest) to 9 (smallest). Default: ${DEFAULT-VALUE}.")
        int level;

        int validated(CommandSpec spec) {
            if (level < 1 || level > 9) {
                throw new ParameterException(spec.commandLine(),
                        "Invalid value '" + level + "' for option '--level': must be between 1 and 9.");
            }
            return level;
        }
    }
}
