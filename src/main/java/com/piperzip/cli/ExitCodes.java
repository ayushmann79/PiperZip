package com.piperzip.cli;

import com.piperzip.exception.CorruptDataException;
import com.piperzip.exception.InvalidInputException;
import com.piperzip.exception.PiperZipException;
import com.piperzip.exception.UnsafeArchiveException;

/** Process exit codes. Documented in the README so scripts can rely on them. */
public final class ExitCodes {

    /** Success. */
    public static final int OK = 0;
    /** I/O error (missing file, permission denied, disk full). */
    public static final int IO_ERROR = 1;
    /** Command-line usage error (unknown option, missing argument). */
    public static final int USAGE = 2;
    /** Invalid input (bad path, refused overwrite, unusable output). */
    public static final int INVALID_INPUT = 3;
    /** Corrupt, truncated or non-matching compressed data. */
    public static final int CORRUPT_DATA = 4;
    /** Archive rejected for safety reasons or limits exceeded. */
    public static final int UNSAFE_ARCHIVE = 5;
    /** Verification found that two files differ. */
    public static final int VERIFY_MISMATCH = 6;
    /** Unexpected internal error. */
    public static final int INTERNAL_ERROR = 70;

    private ExitCodes() {
    }

    /**
     * Maps an exception to an exit code.
     *
     * @param error the failure
     * @return the exit code
     */
    public static int forException(Throwable error) {
        if (error instanceof InvalidInputException) {
            return INVALID_INPUT;
        }
        if (error instanceof CorruptDataException) {
            return CORRUPT_DATA;
        }
        if (error instanceof UnsafeArchiveException) {
            return UNSAFE_ARCHIVE;
        }
        if (error instanceof PiperZipException || error instanceof java.io.IOException) {
            return IO_ERROR;
        }
        return INTERNAL_ERROR;
    }
}
