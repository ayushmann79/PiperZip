package com.piperzip.exception;

/** An archive was rejected for safety reasons (path traversal, limits, symlinks). */
public class UnsafeArchiveException extends PiperZipException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message human-readable description shown to the user
     */
    public UnsafeArchiveException(String message) {
        super(message);
    }

    /**
     * Creates the exception with an underlying cause.
     *
     * @param message human-readable description shown to the user
     * @param cause the underlying failure
     */
    public UnsafeArchiveException(String message, Throwable cause) {
        super(message, cause);
    }
}
