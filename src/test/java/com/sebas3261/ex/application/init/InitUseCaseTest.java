package com.sebas3261.ex.application.init;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sebas3261.ex.domain.project.ProjectConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InitUseCaseTest {

    private final List<String> created = new ArrayList<>();
    private final InitUseCase useCase = new InitUseCase((config, skipWrapper) ->
            created.add(config.name() + " wrapper=" + !skipWrapper));

    @Test
    void validConfigurationIsCreated() {
        useCase.execute(new ProjectConfig("my-app", "com.example", "com.example.myapp", "21"), false);

        assertEquals(List.of("my-app wrapper=true"), created);
    }

    @Test
    void finalValidationUsesTheChosenJavaVersion() {
        // "_" passes early validation only on Java 8; with 21 the final check must reject it.
        assertThrows(IllegalArgumentException.class, () ->
                useCase.execute(new ProjectConfig("demo", "com._", "com._.demo", "21"), true));
        assertTrue(created.isEmpty());

        useCase.execute(new ProjectConfig("demo", "com._", "com._.demo", "8"), true);
        assertEquals(List.of("demo wrapper=false"), created);
    }

    @Test
    void invalidConfigurationCreatesNothing() {
        assertThrows(IllegalArgumentException.class, () ->
                useCase.execute(new ProjectConfig("Bad", "com.example", "com.example.bad", "21"), false));
        assertTrue(created.isEmpty());
    }
}
