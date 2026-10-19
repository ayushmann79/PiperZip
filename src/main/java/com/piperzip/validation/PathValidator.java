package com.piperzip.validation;

import com.piperzip.exception.InvalidInputException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Validates user-supplied paths before any work starts, so failures are early and readable. */
public final class PathValidator {

    private PathValidator() {
    }

    /**
     * Requires an existing, readable, regular file.
     *
     * @param path the path to check
     * @throws InvalidInputException if the path is missing, a directory, special or unreadable
     */
    public static void requireReadableFile(Path path) {
        if (!Files.exists(path)) {
            throw new InvalidInputException("Input file does not exist: " + path);
        }
        if (Files.isDirectory(path)) {
            throw new InvalidInputException("Expected a file but found a directory: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new InvalidInputException("Not a regular file: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new InvalidInputException("Permission denied: cannot read " + path);
        }
    }

    /**
     * Requires that an output path can be written without surprises.
     *
     * @param output the intended output file
     * @param overwrite whether replacing an existing file is allowed
     * @param inputs paths that must not be the same file as the output
     * @throws IOException if file identity cannot be determined
     * @throws InvalidInputException if the output is unusable
     */
    public static void requireWritableTarget(Path output, boolean overwrite, Path... inputs)
            throws IOException {
        Path absolute = output.toAbsolutePath();
        if (Files.isDirectory(absolute)) {
            throw new InvalidInputException("Output path is a directory: " + output);
        }
        Path parent = absolute.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new InvalidInputException("Output directory does not exist: " + parent);
        }
        if (!Files.isWritable(parent)) {
            throw new InvalidInputException("Permission denied: cannot write to directory " + parent);
        }
        if (Files.exists(absolute)) {
            for (Path input : inputs) {
                if (Files.exists(input) && Files.isSameFile(input, absolute)) {
                    throw new InvalidInputException("Output must be different from input: " + output);
                }
            }
            if (!overwrite) {
                throw new InvalidInputException(
                        "Output already exists: " + output + " (use --force to overwrite)");
            }
            if (!Files.isWritable(absolute)) {
                throw new InvalidInputException("Permission denied: cannot overwrite " + output);
            }
        }
    }
}
