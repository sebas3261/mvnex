package com.sebas3261.ex.infrastructure.dependency;

import java.util.List;

/** Reads the Maven repository (through the user's mirrors). */
public interface RepositoryLookup {

    /** All versions of {@code groupId:artifactId}, ascending by Maven version ordering. */
    List<String> listVersions(String groupId, String artifactId);

    /** Whether the POM of {@code groupId:artifactId:version} exists. */
    boolean pomExists(String groupId, String artifactId, String version);
}
