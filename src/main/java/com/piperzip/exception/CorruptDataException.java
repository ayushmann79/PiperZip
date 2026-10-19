package com.piperzip.exception;

/** Input data is not a valid, complete GZIP or ZIP file. */
public class CorruptDataException extends PiperZipException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message human-readable description shown to the user
     */
    public CorruptDataException(String message) {
        super(message);
    }

    /**
     * Creates the exception with an underlying cause.
     *
     * @param message human-readable description shown to the user
     * @param cause the underlying failure
     */
    public CorruptDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
