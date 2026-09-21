package com.sebas3261.ex.domain.dependency;

import java.util.Objects;

/**
 * An exact Maven dependency that can be written to a POM. An empty scope means the POM entry
 * gets no {@code <scope>} element.
 */
public record ResolvedDependency(String groupId, String artifactId, String version, String scope) {

    public ResolvedDependency {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(version, "version");
        scope = scope == null ? "" : scope;
    }

    public ResolvedDependency(String groupId, String artifactId, String version) {
        this(groupId, artifactId, version, "");
    }

    public ResolvedDependency withScope(String newScope) {
        return new ResolvedDependency(groupId, artifactId, version, newScope);
    }

    public ResolvedDependency withVersion(String newVersion) {
        return new ResolvedDependency(groupId, artifactId, newVersion, scope);
    }

    /** {@code groupId:artifactId}, the identity used for duplicate detection. */
    public String key() {
        return groupId + ":" + artifactId;
    }

    /** {@code groupId:artifactId:version}, as shown in selectors and summaries. */
    public String coordinates() {
        return groupId + ":" + artifactId + ":" + version;
    }
}
