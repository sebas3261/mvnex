package com.sebas3261.ex.infrastructure.project;

import java.nio.file.Files;
import java.nio.file.Path;

/** Finds the POM that {@code ex:add} edits. */
public final class PomLocator {

    public static final String NOT_FOUND_MESSAGE = "pom.xml not found. Run this command inside a Maven project.";

    private PomLocator() {
    }

    /**
     * @param requestPom    the POM Maven was pointed at ({@code -f}, or {@code pom.xml} in the execution
     *                      directory); may be null
     * @param executionRoot the directory Maven was invoked from
     */
    public static Path locate(Path requestPom, Path executionRoot) {
        if (requestPom != null && Files.isRegularFile(requestPom)) {
            return requestPom;
        }
        for (Path dir = executionRoot.toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("pom.xml");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(NOT_FOUND_MESSAGE);
    }
}
