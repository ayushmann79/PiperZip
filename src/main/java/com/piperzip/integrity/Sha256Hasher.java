package com.piperzip.integrity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 helper. Integrity hashing is deliberately separate from compression: a hash answers
 * "are these bytes identical?", which says nothing about how well they were compressed.
 */
public final class Sha256Hasher {

    private Sha256Hasher() {
    }

    /**
     * Creates a new SHA-256 digest.
     *
     * @return a fresh digest instance
     */
    public static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform is required to provide SHA-256.
            throw new IllegalStateException("SHA-256 is not available on this JVM", e);
        }
    }

    /**
     * Hashes a file by streaming it.
     *
     * @param file the file to hash
     * @return lowercase hex digest
     * @throws IOException if the file cannot be read
     */
    public static String hash(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return hash(in);
        }
    }

    /**
     * Hashes a stream until end of input. The stream is not closed.
     *
     * @param in the stream to read
     * @return lowercase hex digest
     * @throws IOException if reading fails
     */
    public static String hash(InputStream in) throws IOException {
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[64 * 1024];
        int n;
        while ((n = in.read(buffer)) != -1) {
            digest.update(buffer, 0, n);
        }
        return toHex(digest.digest());
    }

    /**
     * Encodes bytes as lowercase hex.
     *
     * @param bytes the bytes
     * @return hex string
     */
    public static String toHex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
