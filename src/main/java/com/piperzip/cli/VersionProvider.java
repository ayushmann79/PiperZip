package com.piperzip.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import picocli.CommandLine.IVersionProvider;

/** Reads the application version from the Maven-filtered {@code piperzip.properties} resource. */
public final class VersionProvider implements IVersionProvider {

    @Override
    public String[] getVersion() {
        return new String[] {"piperzip " + version()};
    }

    static String version() {
        Properties properties = new Properties();
        try (InputStream in = VersionProvider.class.getResourceAsStream("/piperzip.properties")) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            return "unknown";
        }
        String value = properties.getProperty("version", "unknown");
        // An unfiltered resource (for example when run from an IDE) still contains the placeholder.
        return value.contains("${") ? "dev" : value;
    }
}
