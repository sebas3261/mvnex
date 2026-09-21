package com.sebas3261.ex.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sebas3261.ex.application.errors.OperationCancelledException;
import com.sebas3261.ex.application.init.InitUseCase;
import com.sebas3261.ex.domain.project.ProjectConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InitFlowTest {

    private final List<String> created = new ArrayList<>();
    private final InitUseCase useCase = new InitUseCase((config, skipWrapper) ->
            created.add(describe(config) + " wrapper=" + !skipWrapper));

    private static String describe(ProjectConfig c) {
        return c.name() + "|" + c.groupId() + "|" + c.packageName() + "|" + c.javaVersion();
    }

    private void run(ScriptedInteraction io, String name, String groupId, String pkg, String java, Boolean wrapper) {
        new InitFlow(io, io, useCase).run(new InitFlow.Parameters(name, groupId, pkg, java, wrapper));
    }

    @Test
    void fullySpecifiedBatchInit() {
        ScriptedInteraction io = ScriptedInteraction.batch();
        run(io, "my-app", "com.example", "com.example.app", "17", false);

        assertEquals(List.of("my-app|com.example|com.example.app|17 wrapper=false"), created);
        assertTrue(io.prompts.isEmpty());
    }

    @Test
    void allPromptedWithDefaultsAccepted() {
        ScriptedInteraction io = new ScriptedInteraction(true, "", "", "", "", "");
        run(io, null, null, null, null, null);

        assertEquals(List.of("my-project|com.example|com.example.myproject|21 wrapper=true"), created);
        assertEquals(List.of(
                "Project name [my-project]",
                "Group ID [com.example]",
                "Package [com.example.myproject]",
                "Java version [8, 11, 17, 21, 25] [21]",
                "Maven Wrapper [Yes, No] [Yes]"), io.prompts);
    }

    @Test
    void summaryAndSuccessOutput() {
        ScriptedInteraction io = ScriptedInteraction.batch();
        run(io, "svc", null, null, null, false);

        assertEquals(List.of(
                "INFO Creating Maven project",
                "INFO   Project   svc",
                "INFO   Group     com.example",
                "INFO   Package   com.example.svc",
                "INFO   Java      21",
                "INFO   Wrapper   None",
                "INFO Project created successfully",
                "INFO   cd svc",
                "INFO   mvn package"), io.output);
    }

    @Test
    void wrapperSuccessHint() {
        ScriptedInteraction io = ScriptedInteraction.batch();
        run(io, "svc", null, null, null, null);
        assertTrue(io.output.contains("INFO   ./mvnw package"));
        assertTrue(io.output.contains("INFO   Wrapper   Maven Wrapper"));
    }

    @Test
    void promptedValueFailsFinalValidation() {
        ScriptedInteraction io = new ScriptedInteraction(true, "My App", "", "", "", "");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> run(io, null, null, null, null, null));
        assertEquals("Invalid project name. Use lowercase letters, numbers and hyphens.", error.getMessage());
        assertTrue(created.isEmpty());
    }

    @Test
    void inputClosedAtGroupIdCancels() {
        ScriptedInteraction io = new ScriptedInteraction(true, "demo");
        assertEquals("Operation cancelled.",
                assertThrows(OperationCancelledException.class, () -> run(io, null, null, null, null, null))
                        .getMessage());
        assertTrue(created.isEmpty());
    }

    @Test
    void fullySpecifiedParametersSkipTheWrapperPrompt() {
        ScriptedInteraction io = new ScriptedInteraction(true, "");
        run(io, "my-app", "com.example", null, "21", null);

        assertEquals(List.of("Package [com.example.myapp]"), io.prompts, "the package alone does not bring back the wrapper prompt");
        assertEquals(List.of("my-app|com.example|com.example.myapp|21 wrapper=true"), created);
    }

    @Test
    void partiallySpecifiedPromptsForTheRestThenTheWrapper() {
        ScriptedInteraction io = new ScriptedInteraction(true, "org.acme", "", "17", "No");
        run(io, "tool", null, null, null, null);

        assertEquals(List.of("Group ID [com.example]", "Package [org.acme.tool]", "Java version [8, 11, 17, 21, 25] [21]",
                "Maven Wrapper [Yes, No] [Yes]"), io.prompts);
        assertEquals(List.of("tool|org.acme|org.acme.tool|17 wrapper=false"), created);
    }

    @Test
    void explicitOptOutNeverAsksAboutTheWrapper() {
        ScriptedInteraction io = new ScriptedInteraction(true, "", "", "", "");
        run(io, null, null, null, null, false);

        assertTrue(io.prompts.stream().noneMatch(p -> p.startsWith("Maven Wrapper")));
        assertTrue(created.get(0).endsWith("wrapper=false"));
    }

    @Test
    void explicitTrueIsTreatedLikeUnset() {
        ScriptedInteraction io = new ScriptedInteraction(true, "", "", "", "No");
        run(io, "tool", null, null, null, true);

        assertTrue(io.prompts.contains("Maven Wrapper [Yes, No] [Yes]"));
        assertTrue(created.get(0).endsWith("wrapper=false"));
    }

    @Test
    void packageDefaultFollowsTheAnswers() {
        ScriptedInteraction io = new ScriptedInteraction(true, "inventory-api", "org.acme", "", "", "");
        run(io, null, null, null, null, null);

        assertTrue(io.prompts.contains("Package [org.acme.inventoryapi]"));
        assertEquals(List.of("inventory-api|org.acme|org.acme.inventoryapi|21 wrapper=true"), created);
    }

    @Test
    void customPackageAnswered() {
        ScriptedInteraction io = new ScriptedInteraction(true, "org.acme.inventory", "");
        run(io, "inventory-api", "org.acme", null, "17", null);

        assertEquals(List.of("inventory-api|org.acme|org.acme.inventory|17 wrapper=true"), created);
    }

    @Test
    void invalidAnsweredPackageFailsWithThePackageMessage() {
        ScriptedInteraction io = new ScriptedInteraction(true, "org.acme.Inventory");
        assertEquals("Invalid package name. Use lowercase package segments separated by dots.",
                assertThrows(IllegalArgumentException.class, () -> run(io, "inventory-api", "org.acme", null, "17",
                        null)).getMessage());
        assertTrue(created.isEmpty());
    }

    @Test
    void givenPackageIsNotAskedFor() {
        ScriptedInteraction io = new ScriptedInteraction(true, "", "", "", "");
        run(io, null, null, "com.example.app", null, null);

        assertTrue(io.prompts.stream().noneMatch(p -> p.startsWith("Package")));
        assertEquals(List.of("my-project|com.example|com.example.app|21 wrapper=true"), created);
    }

    @Test
    void badGroupIdFailsBeforeAnyPrompt() {
        ScriptedInteraction io = new ScriptedInteraction(true, "demo");
        assertEquals("Invalid group ID. Use lowercase package segments separated by dots.",
                assertThrows(IllegalArgumentException.class, () -> run(io, null, "com.Bad", null, null, null))
                        .getMessage());
        assertTrue(io.prompts.isEmpty());
    }

    @Test
    void underscoreSegmentFailsEarlyAssumingJava21() {
        ScriptedInteraction io = new ScriptedInteraction(true, "demo", "8");
        assertThrows(IllegalArgumentException.class, () -> run(io, "demo", "com._", null, null, null));
        assertTrue(io.prompts.isEmpty(), "no Java version prompt");
    }

    @Test
    void invalidExplicitPackageNamesThePackage() {
        assertEquals("Invalid package name. Use lowercase package segments separated by dots.",
                assertThrows(IllegalArgumentException.class, () -> run(ScriptedInteraction.batch(), "demo",
                        "com.example", "com.example.New", null, null)).getMessage());
    }

    @Test
    void batchWithoutNameFails() {
        ScriptedInteraction io = ScriptedInteraction.batch();
        assertEquals("Missing project name. Provide -Dex.name=<name>.",
                assertThrows(IllegalArgumentException.class, () -> run(io, null, "com.example", null, null, null))
                        .getMessage());
        assertTrue(created.isEmpty());
    }

    @Test
    void batchDefaults() {
        run(ScriptedInteraction.batch(), "svc", null, null, null, null);
        assertEquals(List.of("svc|com.example|com.example.svc|21 wrapper=true"), created);
    }

    @Test
    void blankParametersCountAsMissing() {
        ScriptedInteraction io = ScriptedInteraction.batch();
        assertThrows(IllegalArgumentException.class, () -> run(io, " ", "", null, null, null));
    }
}
