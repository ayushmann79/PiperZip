package com.piperzip.exception;

/** Base class for all expected, user-facing PiperZip failures. */
public class PiperZipException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message human-readable description shown to the user
     */
    public PiperZipException(String message) {
        super(message);
    }

    /**
     * Creates the exception with an underlying cause.
     *
     * @param message human-readable description shown to the user
     * @param cause the underlying failure
     */
    public PiperZipException(String message, Throwable cause) {
        super(message, cause);
    }
}
