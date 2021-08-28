package com.github.avano.pr.workflow.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility class for I/O stuff.
 */
public final class IOUtils {
    private IOUtils() {
    }

    /**
     * Reads the given file into a string.
     * @param p path to file
     * @return file content as string
     */
    public static String readFile(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read file " + p.toAbsolutePath(), e);
        }
    }
}
