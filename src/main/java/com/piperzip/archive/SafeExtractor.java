package com.piperzip.archive;

import com.piperzip.config.ExtractionLimits;
import com.piperzip.exception.CorruptDataException;
import com.piperzip.exception.InvalidInputException;
import com.piperzip.exception.UnsafeArchiveException;
import com.piperzip.validation.PathValidator;
import com.piperzip.validation.ProtectedPaths;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Extracts ZIP archives defensively. Extraction proceeds in three phases:
 *
 * <ol>
 *   <li><b>Plan</b> - read the central directory, validate every entry name (ZIP Slip, absolute
 *       paths, backslashes, drive letters), enforce the entry-count limit, detect duplicates and
 *       conflicts, and refuse to write through symbolic links. Nothing is written yet.
 *   <li><b>Stage</b> - decompress into a private staging directory inside the destination while
 *       enforcing the byte and ratio limits on bytes actually produced (declared sizes in the
 *       archive are never trusted) and verifying each entry's CRC-32.
 *   <li><b>Commit</b> - move staged files into place. If anything failed earlier, the staging
 *       directory is deleted and the destination is untouched.
 * </ol>
 *
 * <p>Extracted files get default permissions; permission bits and symbolic links stored in the
 * archive are ignored. Residual risk: a local attacker who can modify the destination while
 * extraction runs can still race the symlink checks (TOCTOU).
 */
public final class SafeExtractor {

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_NAME_IN_MESSAGE = 100;

    private final ExtractionLimits limits;

    /**
     * Creates an extractor.
     *
     * @param limits resource limits to enforce
     */
    public SafeExtractor(ExtractionLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /**
     * Extracts {@code archive} into {@code destination}.
     *
     * @param archive the ZIP file
     * @param destination target directory (created if missing)
     * @param overwrite whether existing files may be replaced
     * @return extraction summary
     * @throws IOException on I/O failure
     * @throws UnsafeArchiveException if the archive is hostile or exceeds limits
     * @throws CorruptDataException if the archive is malformed or truncated
     * @throws InvalidInputException if the destination is unusable or files would be overwritten
     */
    public ExtractionResult extract(Path archive, Path destination, boolean overwrite) throws IOException {
        long start = System.nanoTime();
        PathValidator.requireReadableFile(archive);
        requireZipSignature(archive);
        Path root = prepareDestination(destination);
        long archiveSize = Files.size(archive);

        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            List<PlannedEntry> plan = plan(zip, root, overwrite);
            long total = stageAndCommit(zip, plan, root, overwrite, archiveSize);
            return new ExtractionResult(plan.size(), total, Duration.ofNanos(System.nanoTime() - start));
        } catch (ZipException | EOFException e) {
            throw new CorruptDataException(
                    "Not a valid or complete ZIP archive: " + archive + " (" + e.getMessage() + ")", e);
        }
    }

    private static void requireZipSignature(Path archive) throws IOException {
        byte[] header = new byte[4];
        int read;
        try (InputStream in = Files.newInputStream(archive)) {
            read = in.readNBytes(header, 0, header.length);
        }
        boolean local = read == 4 && header[0] == 'P' && header[1] == 'K' && header[2] == 3 && header[3] == 4;
        boolean empty = read == 4 && header[0] == 'P' && header[1] == 'K' && header[2] == 5 && header[3] == 6;
        if (!local && !empty) {
            throw new CorruptDataException("Not a ZIP archive (missing ZIP signature): " + archive);
        }
    }

    private static Path prepareDestination(Path destination) throws IOException {
        Path absolute = destination.toAbsolutePath().normalize();
        ProtectedPaths.requireSafeDestination(absolute);
        if (Files.exists(absolute)) {
            if (!Files.isDirectory(absolute)) {
                throw new InvalidInputException("Destination exists and is not a directory: " + destination);
            }
            ProtectedPaths.requireSafeDestination(absolute.toRealPath());
        }
        Files.createDirectories(absolute);
        if (!Files.isWritable(absolute)) {
            throw new InvalidInputException("Permission denied: cannot write to " + destination);
        }
        return absolute.toRealPath();
    }

    // ---- Phase 1: plan -------------------------------------------------------------------

    private List<PlannedEntry> plan(ZipFile zip, Path root, boolean overwrite) {
        if (zip.size() > limits.maxEntries()) {
            throw new UnsafeArchiveException("Archive has " + zip.size() + " entries, which exceeds the limit of "
                    + limits.maxEntries() + " (--max-entries)");
        }
        List<PlannedEntry> plan = new ArrayList<>(zip.size());
        Set<Path> seen = new HashSet<>();
        long declaredTotal = 0;
        for (ZipEntry entry : Collections.list(zip.entries())) {
            Path target = resolveSafely(root, entry);
            if (target == null) {
                continue; // an entry such as "./" that refers to the destination itself
            }
            if (!seen.add(target)) {
                throw new UnsafeArchiveException("Archive contains duplicate entries for: " + display(entry.getName()));
            }
            long size = entry.getSize();
            if (size > 0) {
                if (size > limits.maxTotalBytes() - declaredTotal) {
                    throw totalBytesExceeded();
                }
                declaredTotal += size;
            }
            checkAgainstFilesystem(root, target, entry.isDirectory(), overwrite);
            plan.add(new PlannedEntry(entry, target));
        }
        return plan;
    }

    private static Path resolveSafely(Path root, ZipEntry entry) {
        String name = entry.getName();
        if (name.isEmpty()) {
            throw new UnsafeArchiveException("Archive contains an entry with an empty name");
        }
        if (name.indexOf('\0') >= 0) {
            throw new UnsafeArchiveException("Archive entry name contains a NUL character: " + display(name));
        }
        if (name.indexOf('\\') >= 0) {
            throw new UnsafeArchiveException("Archive entry name contains a backslash: " + display(name));
        }
        if (name.startsWith("/")) {
            throw new UnsafeArchiveException("Archive entry has an absolute path: " + display(name));
        }
        if (name.length() >= 2 && name.charAt(1) == ':' && Character.isLetter(name.charAt(0))) {
            throw new UnsafeArchiveException("Archive entry has a drive-letter path: " + display(name));
        }
        for (String segment : name.split("/")) {
            if (segment.equals("..")) {
                throw new UnsafeArchiveException("Archive entry escapes the destination (path traversal): "
                        + display(name));
            }
        }
        Path target;
        try {
            target = root.resolve(name).normalize();
        } catch (InvalidPathException e) {
            throw new UnsafeArchiveException("Archive entry has an invalid path: " + display(name), e);
        }
        if (!target.startsWith(root)) {
            throw new UnsafeArchiveException("Archive entry escapes the destination (path traversal): "
                    + display(name));
        }
        if (target.equals(root)) {
            if (entry.isDirectory()) {
                return null;
            }
            throw new UnsafeArchiveException("Archive entry would replace the destination itself: " + display(name));
        }
        return target;
    }

    private static void checkAgainstFilesystem(Path root, Path target, boolean directory, boolean overwrite) {
        requireNoSymlinks(root, target, directory);
        boolean exists = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
        if (directory) {
            if (exists && !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new InvalidInputException("Cannot create directory, a file is in the way: " + target);
            }
        } else if (exists) {
            if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new InvalidInputException("Cannot write file, a directory is in the way: " + target);
            }
            if (!overwrite) {
                throw new InvalidInputException(
                        "File already exists: " + target + " (use --force to overwrite)");
            }
        }
    }

    /** Refuses to write through an existing symbolic link anywhere below the destination root. */
    private static void requireNoSymlinks(Path root, Path target, boolean includeLast) {
        Path relative = root.relativize(target);
        int count = relative.getNameCount();
        int checked = includeLast ? count : count - 1;
        Path current = root;
        for (int i = 0; i < checked; i++) {
            current = current.resolve(relative.getName(i));
            if (Files.isSymbolicLink(current)) {
                throw new UnsafeArchiveException(
                        "Refusing to extract through a symbolic link in the destination: " + current);
            }
        }
    }

    // ---- Phase 2 and 3: stage, then commit -------------------------------------------------

    private long stageAndCommit(ZipFile zip, List<PlannedEntry> plan, Path root, boolean overwrite,
            long archiveSize) throws IOException {
        Path staging = Files.createTempDirectory(root, ".piperzip-staging-");
        Throwable primary = null;
        try {
            long total = 0;
            for (PlannedEntry planned : plan) {
                Path staged = staging.resolve(root.relativize(planned.target()).toString());
                if (planned.entry().isDirectory()) {
                    Files.createDirectories(staged);
                } else {
                    Files.createDirectories(staged.getParent());
                    total = stageFile(zip, planned.entry(), staged, total, archiveSize);
                }
            }
            commit(plan, staging, root, overwrite);
            return total;
        } catch (FileAlreadyExistsException e) {
            primary = new CorruptDataException(
                    "Archive entries conflict with each other (a file and a directory share a path): "
                            + e.getFile(), e);
            throw (CorruptDataException) primary;
        } catch (IOException | RuntimeException e) {
            primary = e;
            throw e;
        } finally {
            cleanUp(staging, primary);
        }
    }

    private long stageFile(ZipFile zip, ZipEntry entry, Path staged, long totalSoFar, long archiveSize)
            throws IOException {
        long total = totalSoFar;
        long entryBytes = 0;
        CRC32 crc = new CRC32();
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream in = zip.getInputStream(entry);
                OutputStream out = Files.newOutputStream(staged, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                entryBytes += read;
                enforceLimits(total, archiveSize);
                crc.update(buffer, 0, read);
                out.write(buffer, 0, read);
            }
        }
        if (entry.getCrc() != -1 && crc.getValue() != entry.getCrc()) {
            throw new CorruptDataException("CRC-32 mismatch, the archive is corrupted: " + display(entry.getName()));
        }
        if (entry.getSize() != -1 && entryBytes != entry.getSize()) {
            throw new CorruptDataException("Entry size does not match its header, the archive is corrupted: "
                    + display(entry.getName()));
        }
        return total;
    }

    private void enforceLimits(long total, long archiveSize) {
        if (total > limits.maxTotalBytes()) {
            throw totalBytesExceeded();
        }
        if (total > ExtractionLimits.RATIO_CHECK_FLOOR_BYTES && archiveSize > 0
                && (double) total / archiveSize > limits.maxCompressionRatio()) {
            throw new UnsafeArchiveException("Compression ratio exceeds " + limits.maxCompressionRatio()
                    + ":1, possible decompression bomb (--max-ratio)");
        }
    }

    private UnsafeArchiveException totalBytesExceeded() {
        return new UnsafeArchiveException("Total uncompressed size exceeds the limit of "
                + limits.maxTotalBytes() + " bytes (--max-total-bytes)");
    }

    private static void commit(List<PlannedEntry> plan, Path staging, Path root, boolean overwrite)
            throws IOException {
        for (PlannedEntry planned : plan) {
            Path target = planned.target();
            boolean directory = planned.entry().isDirectory();
            // Re-check right before writing to narrow (not close) the TOCTOU window.
            requireNoSymlinks(root, target, directory);
            if (directory) {
                Files.createDirectories(target);
            } else {
                Files.createDirectories(target.getParent());
                requireNoSymlinks(root, target, false);
                Path staged = staging.resolve(root.relativize(target).toString());
                try {
                    if (overwrite) {
                        Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        Files.move(staged, target);
                    }
                } catch (FileAlreadyExistsException e) {
                    throw new InvalidInputException("File already exists: " + target + " (use --force to overwrite)", e);
                }
            }
        }
    }

    private static void cleanUp(Path staging, Throwable primary) throws IOException {
        try (Stream<Path> walk = Files.walk(staging)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            if (primary != null) {
                primary.addSuppressed(e);
            } else {
                throw new IOException("Extraction finished but the temporary directory could not be removed: "
                        + staging, e);
            }
        }
    }

    private static String display(String name) {
        String printable = name.replaceAll("\\p{Cntrl}", "?");
        return printable.length() > MAX_NAME_IN_MESSAGE
                ? printable.substring(0, MAX_NAME_IN_MESSAGE) + "..." : printable;
    }

    private record PlannedEntry(ZipEntry entry, Path target) {
    }
}
