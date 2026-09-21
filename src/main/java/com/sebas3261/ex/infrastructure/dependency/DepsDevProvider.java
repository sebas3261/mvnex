package com.sebas3261.ex.infrastructure.dependency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebas3261.ex.application.errors.DependencyNotFoundException;
import com.sebas3261.ex.application.errors.DependencyResolutionException;
import com.sebas3261.ex.application.ports.HttpClient;
import com.sebas3261.ex.infrastructure.transport.LookupUrls;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * deps.dev package API: a coordinate fallback that never answers search terms, and the source of
 * Maven Central version lists for canonical version selection.
 */
public final class DepsDevProvider implements SearchProvider, CentralVersions {

    public static final String BASE_URL = "https://api.deps.dev/v3/systems/MAVEN/packages/";

    private final HttpClient http;
    private final String baseUrl;
    private final ObjectMapper json = new ObjectMapper();

    public DepsDevProvider(HttpClient http, String baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl;
    }

    private record PackageVersion(String version, boolean isDefault) {
    }

    @Override
    public List<Candidate> searchCandidates(String term) {
        throw new DependencyNotFoundException(term);
    }

    @Override
    public Candidate coordinate(String groupId, String artifactId) {
        List<PackageVersion> versions = fetch(groupId, artifactId);
        if (versions.isEmpty()) {
            throw new DependencyNotFoundException(groupId + ":" + artifactId);
        }
        String provisional = versions.stream().filter(PackageVersion::isDefault).map(PackageVersion::version)
                .findFirst()
                // Never the first listed: deps.dev lists versions oldest first.
                .orElseGet(() -> VersionOrdering.highestPreferringStable(
                        versions.stream().map(PackageVersion::version).toList()));
        return new Candidate(groupId, artifactId, provisional);
    }

    @Override
    public void verifyVersion(String groupId, String artifactId, String version) {
        List<PackageVersion> versions = fetch(groupId, artifactId);
        if (versions.isEmpty()) {
            throw new DependencyNotFoundException(groupId + ":" + artifactId);
        }
        if (versions.stream().noneMatch(candidate -> candidate.version().equals(version))) {
            throw new DependencyNotFoundException(groupId + ":" + artifactId + ":" + version);
        }
    }

    @Override
    public Set<String> versions(String groupId, String artifactId) {
        Set<String> versions = new HashSet<>();
        for (PackageVersion version : fetch(groupId, artifactId)) {
            versions.add(version.version());
        }
        return versions;
    }

    private List<PackageVersion> fetch(String groupId, String artifactId) {
        HttpClient.HttpResponse response = http.get(LookupUrls.depsDevPackage(baseUrl, groupId, artifactId));
        if (response.statusCode() == 404) {
            return List.of();
        }
        if (!response.isSuccessful()) {
            throw new DependencyResolutionException("deps.dev request failed.");
        }
        try {
            List<PackageVersion> versions = new ArrayList<>();
            for (JsonNode node : json.readTree(response.body()).path("versions")) {
                String version = node.path("versionKey").path("version").asText();
                if (!version.isEmpty()) {
                    versions.add(new PackageVersion(version, node.path("isDefault").asBoolean(false)));
                }
            }
            return versions;
        } catch (IOException e) {
            throw new DependencyResolutionException("deps.dev request failed.", e);
        }
    }
}
