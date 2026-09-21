package com.sebas3261.ex.plugin;

import com.sebas3261.ex.domain.dependency.DependencyRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Parses {@code ex:add} dependency expressions; the grammar and messages follow the C++ parser. */
public final class DependencyArgumentsParser {

    public static final String USAGE =
            "mvn ex:add -Dex.deps=<dependency>[,<dependency>...] [-Dex.version=<version>] [-Dex.scope=<scope>]";

    private static final Set<String> SCOPES = Set.of("compile", "provided", "runtime", "test", "system", "import");

    private DependencyArgumentsParser() {
    }

    /**
     * @param dependencies expressions from {@code ex.deps}; each is trimmed
     * @param version      {@code ex.version}, or null/empty
     * @param scope        {@code ex.scope}, or null/empty
     */
    public static List<DependencyRequest> parse(List<String> dependencies, String version, String scope) {
        String optionVersion = version == null ? "" : version.trim();
        String optionScope = scope == null ? "" : scope.trim();

        if (dependencies == null || dependencies.isEmpty()) {
            throw new IllegalArgumentException("Missing dependency. Usage: " + USAGE);
        }
        if (!optionVersion.isEmpty() && dependencies.size() > 1) {
            throw new IllegalArgumentException("ex.version can only be used when adding a single dependency.");
        }
        if (!optionScope.isEmpty() && dependencies.size() > 1) {
            throw new IllegalArgumentException("ex.scope can only be used when adding a single dependency.");
        }
        if (!optionScope.isEmpty() && !SCOPES.contains(optionScope)) {
            throw new IllegalArgumentException("Invalid dependency scope: " + optionScope);
        }

        List<DependencyRequest> requests = new ArrayList<>();
        for (String dependency : dependencies) {
            requests.add(parseOne(dependency == null ? "" : dependency.trim(), optionVersion, optionScope));
        }
        return requests;
    }

    private static DependencyRequest parseOne(String expression, String optionVersion, String scope) {
        String[] parts = expression.split(":", -1);
        if (parts.length > 3) {
            throw invalid(expression);
        }
        for (String part : parts) {
            if (part.isEmpty()) {
                throw invalid(expression);
            }
        }

        if (parts.length == 1) {
            return optionVersion.isEmpty()
                    ? new DependencyRequest.SearchTerm(parts[0], scope)
                    : new DependencyRequest.SearchTermWithVersion(parts[0], optionVersion, scope);
        }
        if (parts.length == 2) {
            if (parts[0].contains(".")) {
                return optionVersion.isEmpty()
                        ? new DependencyRequest.Coordinate(parts[0], parts[1], scope)
                        : new DependencyRequest.CoordinateWithVersion(parts[0], parts[1], optionVersion, scope);
            }
            // Preserved C++ quirk: a dotless first part is a search term with an inline version (junit:junit).
            return new DependencyRequest.SearchTermWithVersion(parts[0], version(parts[1], optionVersion), scope);
        }
        return new DependencyRequest.CoordinateWithVersion(parts[0], parts[1], version(parts[2], optionVersion), scope);
    }

    /**
     * Whether a well-formed expression carries its own version ({@code g:a:v}, or {@code term:v}
     * with a dotless first part), using the same split rules as {@link #parse}.
     */
    public static boolean hasInlineVersion(String expression) {
        String[] parts = expression.trim().split(":", -1);
        return parts.length == 3 || parts.length == 2 && !parts[0].contains(".");
    }

    private static String version(String inline, String option) {
        if (!option.isEmpty() && !inline.equals(option)) {
            throw new IllegalArgumentException("Dependency version was provided twice with different values.");
        }
        return inline;
    }

    private static IllegalArgumentException invalid(String expression) {
        return new IllegalArgumentException("Invalid dependency format: " + expression);
    }
}
