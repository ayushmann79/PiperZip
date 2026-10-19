package com.piperzip.io;

import com.piperzip.exception.InvalidInputException;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * Writes a file "all or nothing". Output goes to a hidden temporary file in the target's directory
 * and is moved into place only after the writer finishes successfully. A failure at any point
 * leaves no partial output behind, and an existing target is never half-overwritten.
 */
public final class AtomicFileWriter {

    private static final int BUFFER_SIZE = 64 * 1024;

    /** Produces the content of a file. */
    @FunctionalInterface
    public interface ContentWriter {
        /**
         * Writes the content.
         *
         * @param out destination stream (closed by the caller)
         * @throws IOException on I/O failure
         */
        void write(OutputStream out) throws IOException;
    }

    private AtomicFileWriter() {
    }

    /**
     * Writes {@code target} atomically.
     *
     * @param target final destination path
     * @param overwrite whether an existing target may be replaced
     * @param writer produces the file content
     * @throws IOException if writing fails
     * @throws InvalidInputException if the target exists and overwriting is not allowed
     */
    public static void write(Path target, boolean overwrite, ContentWriter writer) throws IOException {
        Path absolute = target.toAbsolutePath();
        Path parent = absolute.getParent();
        Path temp = parent.resolve(".piperzip-" + UUID.randomUUID() + ".part");
        try {
            try (OutputStream out = new BufferedOutputStream(
                    Files.newOutputStream(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                    BUFFER_SIZE)) {
                writer.write(out);
            }
            commit(temp, absolute, overwrite);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void commit(Path temp, Path target, boolean overwrite) throws IOException {
        if (!overwrite) {
            try {
                // Without REPLACE_EXISTING this fails if the target appeared in the meantime.
                Files.move(temp, target);
            } catch (FileAlreadyExistsException e) {
                throw new InvalidInputException(
                        "Output already exists: " + target + " (use --force to overwrite)", e);
            }
            return;
        }
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
