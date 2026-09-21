package com.sebas3261.ex.infrastructure.dependency;

import java.util.List;

/**
 * One dependency search service. Methods throw the application's dependency-resolution
 * exceptions: not found, unavailable (transport), or a generic resolution error.
 */
public interface SearchProvider {

    /** Artifacts whose artifactId equals {@code term}, in the service's order; never empty. */
    List<Candidate> searchCandidates(String term);

    /** Confirms {@code groupId:artifactId} exists and returns it with a provisional version. */
    Candidate coordinate(String groupId, String artifactId);

    /** Returns normally if {@code groupId:artifactId:version} exists; throws not-found otherwise. */
    void verifyVersion(String groupId, String artifactId, String version);
}
