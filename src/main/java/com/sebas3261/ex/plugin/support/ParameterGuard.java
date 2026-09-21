package com.sebas3261.ex.plugin.support;

import com.sebas3261.ex.application.ports.ReportSink;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Replaces the C++ {@code Unknown option} error for Maven's {@code -D} properties (design D17):
 * an {@code ex.*} key that no goal knows fails with a did-you-mean suggestion; a key that belongs to
 * another goal only warns, so team defaults in {@code .mvn/maven.config} keep working.
 */
public final class ParameterGuard {

    private static final int MAX_SUGGESTION_DISTANCE = 2;

    private ParameterGuard() {
    }

    /**
     * @param goal the running goal ({@code init}, {@code add}, {@code setup}, {@code uninstall})
     * @param keys property names from the session's user and system properties
     */
    public static void check(String goal, Collection<String> keys, ReportSink report) {
        Set<String> own = ExParameters.BY_GOAL.getOrDefault(goal, Set.of());
        Set<String> known = ExParameters.BY_GOAL.values().stream()
                .flatMap(Set::stream).collect(Collectors.toCollection(TreeSet::new));

        for (String key : new TreeSet<>(keys)) {
            if (!key.startsWith("ex.") || key.startsWith(ExParameters.INTERNAL_PREFIX) || own.contains(key)) {
                continue;
            }
            if (known.contains(key)) {
                report.warn("Parameter " + key + " is not used by ex:" + goal + " and will be ignored.");
                continue;
            }
            String message = "Unknown parameter: " + key;
            Optional<String> suggestion = suggest(key, known);
            if (suggestion.isPresent()) {
                message += " (did you mean " + suggestion.get() + "?)";
            }
            throw new IllegalArgumentException(message);
        }
    }

    static Optional<String> suggest(String key, Set<String> known) {
        return known.stream()
                .filter(candidate -> distance(key, candidate) <= MAX_SUGGESTION_DISTANCE)
                .min(Comparator.comparingInt((String candidate) -> distance(key, candidate))
                        .thenComparing(Comparator.naturalOrder()));
    }

    /** Levenshtein distance. */
    static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
