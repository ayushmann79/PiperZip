package com.piperzip.cli;

import java.io.PrintWriter;
import java.util.concurrent.Callable;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** Shared plumbing for commands: output streams come from picocli so tests can capture them. */
abstract class BaseCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    PrintWriter out() {
        return spec.commandLine().getOut();
    }

    PrintWriter err() {
        return spec.commandLine().getErr();
    }

    CommandSpec spec() {
        return spec;
    }
}
