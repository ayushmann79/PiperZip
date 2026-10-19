package com.piperzip.cli;

import picocli.CommandLine.Command;

/** {@code piperzip archive} parent command; only its subcommands do work. */
@Command(name = "archive", mixinStandardHelpOptions = true,
        description = "Create and extract ZIP archives.",
        subcommands = {ArchiveCreateCommand.class, ArchiveExtractCommand.class})
final class ArchiveCommand extends BaseCommand {

    @Override
    public Integer call() {
        spec().commandLine().usage(err());
        err().flush();
        return ExitCodes.USAGE;
    }
}
