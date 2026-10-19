package com.piperzip.archive;

import com.piperzip.exception.InvalidInputException;
import com.piperzip.io.AtomicFileWriter;
import com.piperzip.statistics.CompressionStats;
import com.piperzip.validation.PathValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates ZIP archives from files and directories.
 *
 * <p>ZIP is a container: it stores many named entries, each compressed individually with Deflate.
 * Entry names are UTF-8 and use {@code /} separators. Symbolic links and special files are
 * rejected rather than silently followed or skipped.
 */
public final class ZipArchiver {

    /**
     * Creates an archive.
     *
     * @param inputs files and/or directories to add (directories are added recursively)
     * @param output the archive to create
     * @param overwrite whether an existing archive may be replaced
     * @param level Deflate level 0-9, or -1 for the default
     * @return statistics and entry count
     * @throws IOException on I/O failure
     * @throws InvalidInputException if inputs or output are unusable
     */
    public ArchiveResult create(List<Path> inputs, Path output, boolean overwrite, int level)
            throws IOException {
        if (inputs.isEmpty()) {
            throw new InvalidInputException("No input files or directories were given");
        }
        if (level < -1 || level > 9) {
            throw new InvalidInputException("Compression level must be between 0 and 9: " + level);
        }
        PathValidator.requireWritableTarget(output, overwrite, inputs.toArray(new Path[0]));
        long start = System.nanoTime();
        List<PlannedEntry> entries = plan(inputs, output);

        long[] originalBytes = new long[1];
        AtomicFileWriter.write(output, overwrite, out -> {
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                zip.setLevel(level);
                for (PlannedEntry entry : entries) {
                    ZipEntry zipEntry = new ZipEntry(entry.name());
                    zipEntry.setTime(Files.getLastModifiedTime(entry.path(), LinkOption.NOFOLLOW_LINKS).toMillis());
                    zip.putNextEntry(zipEntry);
                    if (!entry.directory()) {
                        originalBytes[0] += Files.copy(entry.path(), zip);
                    }
                    zip.closeEntry();
                }
            }
        });
        CompressionStats stats = new CompressionStats(originalBytes[0], Files.size(output),
                Duration.ofNanos(System.nanoTime() - start));
        return new ArchiveResult(stats, entries.size());
    }

    private static List<PlannedEntry> plan(List<Path> inputs, Path output) throws IOException {
        Path outputReal = output.toAbsolutePath().getParent().toRealPath().resolve(output.getFileName());
        List<PlannedEntry> entries = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Path input : inputs) {
            if (!Files.exists(input, LinkOption.NOFOLLOW_LINKS)) {
                throw new InvalidInputException("Input does not exist: " + input);
            }
            Path normalized = input.toAbsolutePath().normalize();
            Path fileName = normalized.getFileName();
            if (fileName == null) {
                throw new InvalidInputException("Cannot archive a filesystem root: " + input);
            }
            if (Files.isDirectory(input) && !Files.isSymbolicLink(input)
                    && outputReal.startsWith(input.toRealPath())) {
                throw new InvalidInputException(
                        "The output archive would be inside the input directory: " + input);
            }
            addPath(input, fileName.toString(), entries, names);
        }
        return entries;
    }

    private static void addPath(Path path, String name, List<PlannedEntry> entries, Set<String> names)
            throws IOException {
        BasicFileAttributes attributes =
                Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink()) {
            throw new InvalidInputException("Symbolic links are not supported: " + path);
        }
        if (name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0) {
            throw new InvalidInputException("File name contains an unsupported character: " + path);
        }
        if (attributes.isDirectory()) {
            register(name + "/", path, true, entries, names);
            List<Path> children;
            try (Stream<Path> stream = Files.list(path)) {
                children = stream.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
            }
            for (Path child : children) {
                addPath(child, name + "/" + child.getFileName(), entries, names);
            }
        } else if (attributes.isRegularFile()) {
            register(name, path, false, entries, names);
        } else {
            throw new InvalidInputException("Not a regular file or directory: " + path);
        }
    }

    private static void register(String name, Path path, boolean directory, List<PlannedEntry> entries,
            Set<String> names) {
        if (!names.add(name)) {
            throw new InvalidInputException(
                    "Two inputs would produce the same archive entry name '" + name + "'; rename one of them");
        }
        entries.add(new PlannedEntry(name, path, directory));
    }

    private record PlannedEntry(String name, Path path, boolean directory) {
    }
}
