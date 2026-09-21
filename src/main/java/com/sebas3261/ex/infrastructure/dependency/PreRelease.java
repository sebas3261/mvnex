package com.sebas3261.ex.infrastructure.dependency;

import java.util.List;
import java.util.Locale;

/** Pre-release detection carried over from the C++ resolver (substring markers, preserved as-is). */
public final class PreRelease {

    private static final List<String> MARKERS = List.of("-m", "-rc", "-alpha", "-beta", "-snapshot");

    private PreRelease() {
    }

    public static boolean isPreRelease(String version) {
        String lower = version.toLowerCase(Locale.ROOT);
        return MARKERS.stream().anyMatch(lower::contains);
    }
}
