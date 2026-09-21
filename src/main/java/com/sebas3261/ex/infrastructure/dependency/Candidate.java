package com.sebas3261.ex.infrastructure.dependency;

/** An artifact a provider found, with the version that provider would pick. */
public record Candidate(String groupId, String artifactId, String provisionalVersion) {
}
