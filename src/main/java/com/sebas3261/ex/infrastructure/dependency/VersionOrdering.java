package com.sebas3261.ex.infrastructure.dependency;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionScheme;

/** Orders version strings the way Maven Resolver does. */
public final class VersionOrdering {

    private static final VersionScheme SCHEME = new GenericVersionScheme();

    private VersionOrdering() {
    }

    /** Returns the versions sorted ascending; unparseable versions are dropped. */
    public static List<String> ascending(List<String> versions) {
        List<Version> parsed = new ArrayList<>();
        for (String version : versions) {
            try {
                parsed.add(SCHEME.parseVersion(version));
            } catch (InvalidVersionSpecificationException ignored) {
                // not a version Maven could order
            }
        }
        parsed.sort(Comparator.naturalOrder());
        return parsed.stream().map(Version::toString).toList();
    }

    /** Highest non-pre-release version, else the highest version, else {@code null}. */
    public static String highestPreferringStable(List<String> versions) {
        List<String> sorted = ascending(versions);
        List<String> stable = sorted.stream().filter(v -> !PreRelease.isPreRelease(v)).toList();
        List<String> pool = stable.isEmpty() ? sorted : stable;
        return pool.isEmpty() ? null : pool.get(pool.size() - 1);
    }
}
