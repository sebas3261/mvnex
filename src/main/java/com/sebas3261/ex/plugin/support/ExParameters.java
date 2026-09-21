package com.sebas3261.ex.plugin.support;

import java.util.Map;
import java.util.Set;

/** User-property names of every goal parameter (design D3), in one place for the parameter guard. */
public final class ExParameters {

    public static final String NAME = "ex.name";
    public static final String GROUP_ID = "ex.groupId";
    public static final String PACKAGE = "ex.package";
    public static final String JAVA = "ex.java";
    public static final String WRAPPER = "ex.wrapper";

    public static final String DEPS = "ex.deps";
    public static final String VERSION = "ex.version";
    public static final String SCOPE = "ex.scope";

    /** Undocumented test-only overrides (design D13), e.g. {@code ex.internal.searchMavenUrl}. */
    public static final String INTERNAL_PREFIX = "ex.internal.";

    /** Parameters accepted by each goal. */
    public static final Map<String, Set<String>> BY_GOAL = Map.of(
            "init", Set.of(NAME, GROUP_ID, PACKAGE, JAVA, WRAPPER),
            "add", Set.of(DEPS, VERSION, SCOPE),
            "setup", Set.of(),
            "uninstall", Set.of());

    private ExParameters() {
    }
}
