package com.sebas3261.ex.infrastructure.dependency;

import java.util.Set;

/**
 * The versions of an artifact published on Maven Central, used to skip mirror-only builds.
 * Backed by deps.dev, whose version lists match Central's metadata; search.maven.org's index
 * stopped updating in 2025 and cannot be used for this.
 */
public interface CentralVersions {

    /** All published versions; empty if the artifact is unknown. Throws if the source can't be queried. */
    Set<String> versions(String groupId, String artifactId);
}
