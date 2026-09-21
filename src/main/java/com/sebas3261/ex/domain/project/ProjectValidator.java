package com.sebas3261.ex.domain.project;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates project names, group IDs, package names, and Java versions.
 * Failures are reported as {@link IllegalArgumentException} with a user-facing message.
 */
public final class ProjectValidator {

    public static final Set<String> SUPPORTED_JAVA_VERSIONS = Set.of("8", "11", "17", "21", "25");

    private static final Pattern PROJECT_NAME = Pattern.compile("^[a-z][a-z0-9]*(-[a-z0-9]+)*$");
    private static final Pattern IDENTIFIER_SEGMENT = Pattern.compile("^[a-z_][a-z0-9_]*$");

    private static final Set<String> RESERVED_WORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte",
            "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else",
            "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import",
            "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super",
            "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while",
            "true", "false", "null",
            "exports", "module", "non-sealed", "open", "opens",
            "permits", "provides", "record", "requires", "sealed",
            "to", "transitive", "uses", "var", "with", "yield");

    private ProjectValidator() {
    }

    /** Validates a complete configuration in the order name, groupId, package, Java version. */
    public static void validate(ProjectConfig config) {
        validateProjectName(config.name(), config.javaVersion());
        validateGroupId(config.groupId(), config.javaVersion());
        validatePackageName(config.packageName(), config.javaVersion());
        validateJavaVersion(config.javaVersion());
    }

    public static void validateProjectName(String name, String javaVersion) {
        if (!PROJECT_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid project name. Use lowercase letters, numbers and hyphens.");
        }

        String packageSegment = ProjectNaming.toPackageName(name);

        if (!isValidIdentifierSegment(packageSegment, javaVersion)) {
            if (isReservedWord(packageSegment)) {
                throw new IllegalArgumentException(
                        "Invalid project name. '" + packageSegment + "' is a reserved Java keyword.");
            }
            throw new IllegalArgumentException("Invalid project name. Use lowercase letters, numbers and hyphens.");
        }
    }

    public static void validateGroupId(String groupId, String javaVersion) {
        validateSegments(groupId, javaVersion, "group ID");
    }

    public static void validatePackageName(String packageName, String javaVersion) {
        validateSegments(packageName, javaVersion, "package name");
    }

    public static void validateJavaVersion(String version) {
        if (!SUPPORTED_JAVA_VERSIONS.contains(version)) {
            throw new IllegalArgumentException("Invalid Java version. Supported: 8, 11, 17, 21, 25");
        }
    }

    private static void validateSegments(String value, String javaVersion, String subject) {
        // limit -1 keeps trailing empty segments ("com.example.")
        for (String segment : value.split("\\.", -1)) {
            if (segment.isEmpty()) {
                throw new IllegalArgumentException("Invalid " + subject + ". Package segments cannot be empty.");
            }
            if (isReservedWord(segment)) {
                throw new IllegalArgumentException(
                        "Invalid " + subject + ". '" + segment + "' is a reserved Java keyword.");
            }
            if (!isValidIdentifierSegment(segment, javaVersion)) {
                throw new IllegalArgumentException(
                        "Invalid " + subject + ". Use lowercase package segments separated by dots.");
            }
        }
    }

    private static boolean isValidIdentifierSegment(String value, String javaVersion) {
        // A single underscore became a keyword in Java 9; Java 8 still allows it.
        if (value.equals("_") && !javaVersion.equals("8")) {
            return false;
        }
        return IDENTIFIER_SEGMENT.matcher(value).matches() && !isReservedWord(value);
    }

    private static boolean isReservedWord(String value) {
        return RESERVED_WORDS.contains(value);
    }
}
