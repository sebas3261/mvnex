package com.sebas3261.ex.domain.dependency;

import java.util.Objects;

/**
 * What the user asked for, before resolution. An empty scope means "no scope requested";
 * an empty version means "no version requested".
 */
public sealed interface DependencyRequest {

    /** Requested scope, or an empty string. */
    String scope();

    /** Requested version, or an empty string. */
    String version();

    /** {@code lombok}. */
    record SearchTerm(String query, String scope) implements DependencyRequest {
        public SearchTerm {
            Objects.requireNonNull(query, "query");
            scope = scope == null ? "" : scope;
        }

        @Override
        public String version() {
            return "";
        }
    }

    /** {@code lombok:1.18.48}. */
    record SearchTermWithVersion(String query, String version, String scope) implements DependencyRequest {
        public SearchTermWithVersion {
            Objects.requireNonNull(query, "query");
            Objects.requireNonNull(version, "version");
            scope = scope == null ? "" : scope;
        }
    }

    /** {@code org.projectlombok:lombok}. */
    record Coordinate(String groupId, String artifactId, String scope) implements DependencyRequest {
        public Coordinate {
            Objects.requireNonNull(groupId, "groupId");
            Objects.requireNonNull(artifactId, "artifactId");
            scope = scope == null ? "" : scope;
        }

        @Override
        public String version() {
            return "";
        }
    }

    /** {@code org.projectlombok:lombok:1.18.48}. */
    record CoordinateWithVersion(String groupId, String artifactId, String version, String scope)
            implements DependencyRequest {
        public CoordinateWithVersion {
            Objects.requireNonNull(groupId, "groupId");
            Objects.requireNonNull(artifactId, "artifactId");
            Objects.requireNonNull(version, "version");
            scope = scope == null ? "" : scope;
        }
    }
}
