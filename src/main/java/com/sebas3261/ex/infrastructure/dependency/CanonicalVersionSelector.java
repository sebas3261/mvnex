package com.sebas3261.ex.infrastructure.dependency;

import com.sebas3261.ex.application.errors.LookupNotPossibleException;
import java.util.List;
import java.util.Set;

/**
 * Chooses the version of a versionless dependency deterministically, independent of which
 * search provider answered first (design D8):
 * <ol>
 *   <li>list all versions through the user's mirrors, in Maven version order;</li>
 *   <li>walk the non-pre-releases (or, if there are none, all versions) from the highest down and
 *       take the first one published on Maven Central, which skips mirror-only builds;</li>
 *   <li>if confirmation fails or confirms nothing, take the highest of that pool;</li>
 *   <li>if the listing fails or is empty, keep the provider's provisional version.</li>
 * </ol>
 */
public final class CanonicalVersionSelector {

    private final RepositoryLookup repository;
    private final CentralVersions central;

    public CanonicalVersionSelector(RepositoryLookup repository, CentralVersions central) {
        this.repository = repository;
        this.central = central;
    }

    public String select(String groupId, String artifactId, String provisionalVersion) {
        List<String> versions;
        try {
            versions = VersionOrdering.ascending(repository.listVersions(groupId, artifactId));
        } catch (LookupNotPossibleException e) {
            throw e;
        } catch (RuntimeException e) {
            return provisionalVersion;
        }
        if (versions.isEmpty()) {
            return provisionalVersion;
        }

        List<String> stable = versions.stream().filter(version -> !PreRelease.isPreRelease(version)).toList();
        List<String> pool = stable.isEmpty() ? versions : stable;
        String highest = pool.get(pool.size() - 1);

        Set<String> onCentral;
        try {
            onCentral = central.versions(groupId, artifactId);
        } catch (LookupNotPossibleException e) {
            throw e;
        } catch (RuntimeException e) {
            return highest;
        }
        for (int i = pool.size() - 1; i >= 0; i--) {
            if (onCentral.contains(pool.get(i))) {
                return pool.get(i);
            }
        }
        return highest;
    }
}
