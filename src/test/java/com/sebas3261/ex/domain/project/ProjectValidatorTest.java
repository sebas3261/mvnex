package com.sebas3261.ex.domain.project;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProjectValidatorTest {

    /** Copied verbatim from specs/project-init "Java identifier segment rules". */
    private static final String SPEC_RESERVED_WORDS = "abstract assert boolean break byte case catch char class const "
            + "continue default do double else enum extends final finally float for goto if implements import "
            + "instanceof int interface long native new package private protected public return short static "
            + "strictfp super switch synchronized this throw throws transient try void volatile while true false "
            + "null exports module non-sealed open opens permits provides record requires sealed to transitive "
            + "uses var with yield";

    private static final String GENERIC_NAME_ERROR = "Invalid project name. Use lowercase letters, numbers and hyphens.";

    static Stream<String> reservedWords() {
        return Arrays.stream(SPEC_RESERVED_WORDS.split(" "));
    }

    private static String message(Runnable validation) {
        return assertThrows(IllegalArgumentException.class, validation::run).getMessage();
    }

    // ---- project name ----

    @Test
    void validHyphenatedNameDerivesPackageSegment() {
        assertDoesNotThrow(() -> ProjectValidator.validateProjectName("my-app", "21"));
        assertEquals("myapp", ProjectNaming.toPackageName("my-app"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MyApp", "1app", "my--app", "app-", "-app", "my_app", "my app", ""})
    void invalidNamesUseGenericMessage(String name) {
        assertEquals(GENERIC_NAME_ERROR, message(() -> ProjectValidator.validateProjectName(name, "21")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"class", "int"})
    void nameCollapsingToKeywordNamesTheKeyword(String name) {
        assertEquals("Invalid project name. '" + name + "' is a reserved Java keyword.",
                message(() -> ProjectValidator.validateProjectName(name, "21")));
    }

    @Test
    void hyphenatedNameCollapsingToKeywordNamesTheCollapsedValue() {
        assertEquals("Invalid project name. 'record' is a reserved Java keyword.",
                message(() -> ProjectValidator.validateProjectName("rec-ord", "21")));
    }

    // ---- reserved words and segments ----

    @ParameterizedTest
    @MethodSource("reservedWords")
    void everySpecReservedWordIsRejectedAsGroupSegment(String word) {
        // Includes "non-sealed": the reserved check runs before the character check.
        assertEquals("Invalid group ID. '" + word + "' is a reserved Java keyword.",
                message(() -> ProjectValidator.validateGroupId("com." + word, "21")));
    }

    @Test
    void contextualKeywordRejected() {
        assertEquals("Invalid group ID. 'record' is a reserved Java keyword.",
                message(() -> ProjectValidator.validateGroupId("com.record.app", "21")));
    }

    @Test
    void underscoreSegmentAllowedOnlyOnJava8() {
        assertDoesNotThrow(() -> ProjectValidator.validateGroupId("com._", "8"));
        assertEquals("Invalid group ID. Use lowercase package segments separated by dots.",
                message(() -> ProjectValidator.validateGroupId("com._", "21")));
    }

    @Test
    void underscorePrefixedSegmentsAreFineOnAnyVersion() {
        assertDoesNotThrow(() -> ProjectValidator.validateGroupId("com._internal", "21"));
    }

    // ---- group ID ----

    @ParameterizedTest
    @ValueSource(strings = {"com..example", "com.example.", ".com", ""})
    void emptyGroupSegments(String groupId) {
        assertEquals("Invalid group ID. Package segments cannot be empty.",
                message(() -> ProjectValidator.validateGroupId(groupId, "21")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"com.Example", "com.1example", "com.exa-mple"})
    void invalidGroupSegments(String groupId) {
        assertEquals("Invalid group ID. Use lowercase package segments separated by dots.",
                message(() -> ProjectValidator.validateGroupId(groupId, "21")));
    }

    @Test
    void groupSegmentsAreCheckedLeftToRight() {
        // The empty segment comes first, so it wins over the later reserved word.
        assertEquals("Invalid group ID. Package segments cannot be empty.",
                message(() -> ProjectValidator.validateGroupId("com..class", "21")));
        // The reserved word comes first, so it wins over the later invalid segment.
        assertEquals("Invalid group ID. 'class' is a reserved Java keyword.",
                message(() -> ProjectValidator.validateGroupId("class.Bad", "21")));
    }

    // ---- package name (delta: messages name the package) ----

    @Test
    void invalidExplicitPackageNamesThePackage() {
        assertEquals("Invalid package name. Use lowercase package segments separated by dots.",
                message(() -> ProjectValidator.validatePackageName("com.example.New", "21")));
        assertEquals("Invalid package name. Package segments cannot be empty.",
                message(() -> ProjectValidator.validatePackageName("com..example", "21")));
        assertEquals("Invalid package name. 'int' is a reserved Java keyword.",
                message(() -> ProjectValidator.validatePackageName("com.int", "21")));
    }

    // ---- Java version ----

    @ParameterizedTest
    @ValueSource(strings = {"8", "11", "17", "21", "25"})
    void supportedJavaVersions(String version) {
        assertDoesNotThrow(() -> ProjectValidator.validateJavaVersion(version));
    }

    @ParameterizedTest
    @ValueSource(strings = {"22", "7", "1.8", "", "21.0"})
    void unsupportedJavaVersions(String version) {
        assertEquals("Invalid Java version. Supported: 8, 11, 17, 21, 25",
                message(() -> ProjectValidator.validateJavaVersion(version)));
    }

    // ---- full configuration ----

    @ParameterizedTest
    @CsvSource({
            "my-app, com.example, com.example.myapp, 21",
            "my-cool-app, org.acme, org.acme.custom, 8",
            "svc, com.example, com.example.svc, 25"})
    void validConfigurations(String name, String groupId, String packageName, String java) {
        assertDoesNotThrow(() -> ProjectValidator.validate(new ProjectConfig(name, groupId, packageName, java)));
    }

    @Test
    void fullValidationChecksNameFirst() {
        assertEquals(GENERIC_NAME_ERROR, message(() ->
                ProjectValidator.validate(new ProjectConfig("Bad", "com.Bad", "com.Bad", "99"))));
    }

    @Test
    void derivedPackage() {
        assertEquals("com.example.mycoolapp", "com.example." + ProjectNaming.toPackageName("my-cool-app"));
    }
}
