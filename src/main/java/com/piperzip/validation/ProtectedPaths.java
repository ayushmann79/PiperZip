package com.piperzip.validation;

import com.piperzip.exception.InvalidInputException;
import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Refuses extraction into sensitive system locations.
 *
 * <p>This is a safety net against typos and copy-pasted commands, not a security boundary: a
 * user with enough privileges can always do damage, and the list is intentionally short.
 */
public final class ProtectedPaths {

    private static final Set<String> UNIX_PROTECTED_TOP_LEVEL = Set.of(
            "bin", "sbin", "boot", "dev", "etc", "lib", "lib32", "lib64", "proc", "sys", "usr", "System");

    private static final List<String> WINDOWS_PROTECTED_ENV =
            List.of("SystemRoot", "ProgramFiles", "ProgramFiles(x86)");

    private ProtectedPaths() {
    }

    /**
     * Throws if the destination is a filesystem root or inside a protected system directory.
     *
     * @param path an absolute, normalized path
     * @throws InvalidInputException if the path is protected
     */
    public static void requireSafeDestination(Path path) {
        if (path.getParent() == null) {
            throw new InvalidInputException("Refusing to extract into a filesystem root: " + path);
        }
        if (File.separatorChar == '/') {
            if (path.getRoot() != null && path.getNameCount() > 0
                    && UNIX_PROTECTED_TOP_LEVEL.contains(path.getName(0).toString())) {
                throw new InvalidInputException("Refusing to extract into a system directory: " + path);
            }
            if (path.startsWith("/private/etc")) {
                throw new InvalidInputException("Refusing to extract into a system directory: " + path);
            }
        } else {
            for (String variable : WINDOWS_PROTECTED_ENV) {
                String value = System.getenv(variable);
                if (value != null && isUnder(path, value)) {
                    throw new InvalidInputException("Refusing to extract into a system directory: " + path);
                }
            }
        }
    }

    private static boolean isUnder(Path path, String protectedDir) {
        try {
            return path.startsWith(Path.of(protectedDir).toAbsolutePath().normalize());
        } catch (InvalidPathException e) {
            return false;
        }
    }
}
