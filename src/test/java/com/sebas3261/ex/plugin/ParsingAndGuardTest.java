package com.sebas3261.ex.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sebas3261.ex.application.errors.OperationCancelledException;
import com.sebas3261.ex.domain.dependency.DependencyRequest;
import com.sebas3261.ex.plugin.support.ConsoleInteraction;
import com.sebas3261.ex.plugin.support.ParameterGuard;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import org.codehaus.plexus.components.interactivity.InputHandler;
import org.codehaus.plexus.components.interactivity.OutputHandler;
import org.junit.jupiter.api.Test;

class ParsingAndGuardTest {

    private static String parseError(List<String> deps, String version, String scope) {
        return assertThrows(IllegalArgumentException.class,
                () -> DependencyArgumentsParser.parse(deps, version, scope)).getMessage();
    }

    private static DependencyRequest parseOne(String expression, String version) {
        return DependencyArgumentsParser.parse(List.of(expression), version, "").get(0);
    }

    // ---- dependency grammar ----

    @Test
    void inlineVersionFollowsTheGrammar() {
        assertTrue(DependencyArgumentsParser.hasInlineVersion("lombok:1.18.32"));
        assertTrue(DependencyArgumentsParser.hasInlineVersion(" org.projectlombok:lombok:1.18.32 "));
        assertTrue(DependencyArgumentsParser.hasInlineVersion("junit:junit"), "dotless first part is term:version");
        assertEquals(false, DependencyArgumentsParser.hasInlineVersion("lombok"));
        assertEquals(false, DependencyArgumentsParser.hasInlineVersion("org.projectlombok:lombok"));
    }

    @Test
    void grammar() {
        assertEquals(new DependencyRequest.SearchTerm("lombok", ""), parseOne("lombok", ""));
        assertEquals(new DependencyRequest.SearchTermWithVersion("lombok", "1.18.32", ""), parseOne("lombok:1.18.32", ""));
        assertEquals(new DependencyRequest.Coordinate("org.projectlombok", "lombok", ""),
                parseOne("org.projectlombok:lombok", ""));
        assertEquals(new DependencyRequest.CoordinateWithVersion("org.projectlombok", "lombok", "1.18.32", ""),
                parseOne("org.projectlombok:lombok:1.18.32", ""));
        assertEquals(new DependencyRequest.SearchTermWithVersion("lombok", "1.18.32", ""), parseOne("lombok", "1.18.32"));
        assertEquals(new DependencyRequest.CoordinateWithVersion("org.x", "y", "2", ""), parseOne("org.x:y", "2"));
    }

    @Test
    void dotlessGroupIsASearchTermPreservedQuirk() {
        assertEquals(new DependencyRequest.SearchTermWithVersion("junit", "junit", ""), parseOne("junit:junit", ""));
    }

    @Test
    void invalidFormats() {
        assertEquals("Invalid dependency format: a:b:c:d", parseError(List.of("a:b:c:d"), "", ""));
        assertEquals("Invalid dependency format: org.x::1.0", parseError(List.of("org.x::1.0"), "", ""));
        assertEquals("Invalid dependency format: ", parseError(List.of("lombok", ""), "", ""));
    }

    @Test
    void versions() {
        assertEquals(new DependencyRequest.SearchTermWithVersion("lombok", "1.18.32", ""),
                parseOne("lombok:1.18.32", "1.18.32"));
        assertEquals("Dependency version was provided twice with different values.",
                parseError(List.of("lombok:1.18.32"), "1.18.30", ""));
    }

    @Test
    void itemsAreTrimmedAndScopeAttached() {
        List<DependencyRequest> requests = DependencyArgumentsParser.parse(List.of(" junit-jupiter "), "", "test");
        assertEquals(List.of(new DependencyRequest.SearchTerm("junit-jupiter", "test")), requests);
    }

    @Test
    void usageAndSingleDependencyOptions() {
        assertEquals("Missing dependency. Usage: mvn ex:add -Dex.deps=<dependency>[,<dependency>...] "
                + "[-Dex.version=<version>] [-Dex.scope=<scope>]", parseError(null, "", ""));
        assertEquals("ex.version can only be used when adding a single dependency.",
                parseError(List.of("lombok", "guava"), "1.0", "test"));
        assertEquals("ex.scope can only be used when adding a single dependency.",
                parseError(List.of("lombok", "guava"), "", "test"));
        assertEquals("Invalid dependency scope: testing", parseError(List.of("lombok"), "", "testing"));
    }

    // ---- unknown parameters ----

    @Test
    void typoFailsWithSuggestion() {
        ScriptedInteraction io = ScriptedInteraction.batch();
        assertEquals("Unknown parameter: ex.scop (did you mean ex.scope?)", assertThrows(IllegalArgumentException.class,
                () -> ParameterGuard.check("add", Set.of("ex.deps", "ex.scop"), io)).getMessage());
    }

    @Test
    void unknownWithoutCloseMatch() {
        assertEquals("Unknown parameter: ex.frobnicate", assertThrows(IllegalArgumentException.class,
                () -> ParameterGuard.check("add", Set.of("ex.frobnicate"), ScriptedInteraction.batch())).getMessage());
    }

    @Test
    void otherGoalsParameterWarns() {
        ScriptedInteraction io = ScriptedInteraction.batch();
        ParameterGuard.check("add", Set.of("ex.groupId", "ex.deps"), io);
        assertEquals(List.of("WARN Parameter ex.groupId is not used by ex:add and will be ignored."), io.output);
    }

    @Test
    void internalAndForeignKeysAreIgnored() {
        ScriptedInteraction io = ScriptedInteraction.batch();
        ParameterGuard.check("add", Set.of("ex.internal.sonatypeUrl", "maven.repo.local", "exotic"), io);
        assertTrue(io.output.isEmpty());
    }

    @Test
    void typoOfDepsSuggestsDeps() {
        assertEquals("Unknown parameter: ex.dep (did you mean ex.deps?)", assertThrows(IllegalArgumentException.class,
                () -> ParameterGuard.check("add", Set.of("ex.dep"), ScriptedInteraction.batch())).getMessage());
    }

    // ---- console interaction ----

    private static final class Console implements InputHandler, OutputHandler {
        final Deque<String> lines;
        final StringBuilder written = new StringBuilder();

        Console(String... lines) {
            this.lines = new ArrayDeque<>(Arrays.asList(lines));
        }

        @Override
        public String readLine() {
            return lines.isEmpty() ? null : lines.pop();
        }

        @Override
        public String readPassword() {
            return readLine();
        }

        @Override
        public List<String> readMultipleLines() {
            return List.of();
        }

        @Override
        public void write(String line) {
            written.append(line);
        }

        /** Like plexus-interactivity's DefaultOutputHandler (1.6.0): the text is dropped. */
        @Override
        public void writeLine(String line) {
            written.append(System.lineSeparator());
        }
    }

    @Test
    void textUsesDefaultOnEmptyAndCancelsOnEof() {
        Console console = new Console("");
        ConsoleInteraction interaction = new ConsoleInteraction(console, console, true);

        assertEquals("my-project", interaction.text("Project name", "my-project"));
        assertEquals("Project name (my-project): ", console.written.toString());
        assertThrows(OperationCancelledException.class, () -> interaction.text("Group ID", "com.example"));
    }

    @Test
    void selectAcceptsNumberTextOrDefaultAndRejectsOthers() {
        Console console = new Console("9", "banana", "3", "", "21");
        ConsoleInteraction interaction = new ConsoleInteraction(console, console, true);
        List<String> versions = List.of("8", "11", "17", "21", "25");

        assertEquals("17", interaction.select("Java version", versions, "21"));
        assertEquals("21", interaction.select("Java version", versions, "21"));
        assertEquals("21", interaction.select("Java version", versions, "8"));
        assertTrue(console.written.toString().startsWith("Java version" + System.lineSeparator() + "  1) 8"));
        assertTrue(console.written.toString().contains("  4) 21 (default)"));
        assertTrue(console.written.toString().contains("Invalid selection."));
        assertThrows(OperationCancelledException.class, () -> interaction.select("Java version", versions, "21"));
    }
}
