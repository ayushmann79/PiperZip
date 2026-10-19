package com.piperzip.cli;

import java.io.PrintWriter;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.ParseResult;

/**
 * Root command and factory. Keeping construction in {@link #create()} lets tests run the whole
 * CLI in-process with captured output, with no operating-system process per test.
 */
@Command(name = "piperzip", mixinStandardHelpOptions = true, versionProvider = VersionProvider.class,
        description = "Lossless file compression, ZIP archives and integrity checks.",
        subcommands = {CompressCommand.class, DecompressCommand.class, ArchiveCommand.class,
            StatsCommand.class, VerifyCommand.class},
        exitCodeListHeading = "%nExit codes:%n",
        exitCodeList = {
            "0:Success",
            "1:I/O error (missing file, permission denied, disk full)",
            "2:Usage error (unknown option, missing argument)",
            "3:Invalid input (bad path, output exists, unusable destination)",
            "4:Corrupt or truncated data",
            "5:Unsafe archive rejected (path traversal, limits exceeded)",
            "6:Verification mismatch",
            "70:Internal error"})
public final class PiperZipCli extends BaseCommand {

    @Override
    public Integer call() {
        spec().commandLine().usage(err());
        err().flush();
        return ExitCodes.USAGE;
    }

    /**
     * Builds a fully configured command line.
     *
     * @return the command line, ready for {@code execute(args)}
     */
    public static CommandLine create() {
        CommandLine commandLine = new CommandLine(new PiperZipCli());
        commandLine.setExecutionExceptionHandler(new ErrorHandler());
        return commandLine;
    }

    /** Converts exceptions into a one-line message and a meaningful exit code. */
    private static final class ErrorHandler implements IExecutionExceptionHandler {

        @Override
        public int handleExecutionException(Exception ex, CommandLine commandLine, ParseResult parseResult) {
            PrintWriter err = commandLine.getErr();
            err.println("Error: " + describe(ex));
            if (System.getenv("PIPERZIP_DEBUG") != null) {
                ex.printStackTrace(err);
            }
            err.flush();
            return ExitCodes.forException(ex);
        }

        private static String describe(Exception ex) {
            if (ex instanceof AccessDeniedException e) {
                return "Permission denied: " + e.getFile();
            }
            if (ex instanceof NoSuchFileException e) {
                return "File or directory not found: " + e.getFile();
            }
            if (ex instanceof FileSystemException e && e.getReason() != null) {
                return e.getFile() + ": " + e.getReason();
            }
            if (ex instanceof java.io.IOException) {
                return "I/O error: " + ex.getMessage();
            }
            if (ex instanceof com.piperzip.exception.PiperZipException) {
                return ex.getMessage();
            }
            return "Internal error (" + ex.getClass().getSimpleName() + "): " + ex.getMessage()
                    + " (set PIPERZIP_DEBUG=1 for a stack trace)";
        }
    }
}
