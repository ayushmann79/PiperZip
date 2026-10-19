package com.piperzip.exception;

/** The user supplied an invalid path, option or combination of arguments. */
public class InvalidInputException extends PiperZipException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message human-readable description shown to the user
     */
    public InvalidInputException(String message) {
        super(message);
    }

    /**
     * Creates the exception with an underlying cause.
     *
     * @param message human-readable description shown to the user
     * @param cause the underlying failure
     */
    public InvalidInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
