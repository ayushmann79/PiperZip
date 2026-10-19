package com.piperzip.cli;

/** Process entry point. All logic lives in {@link PiperZipCli} so it can be tested in-process. */
public final class Main {

    private Main() {
    }

    /**
     * Runs the CLI and exits with its status code.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        System.exit(PiperZipCli.create().execute(args));
    }
}
