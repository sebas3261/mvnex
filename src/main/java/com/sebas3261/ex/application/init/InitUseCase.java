package com.sebas3261.ex.application.init;

import com.sebas3261.ex.application.ports.ProjectCreator;
import com.sebas3261.ex.domain.project.ProjectConfig;
import com.sebas3261.ex.domain.project.ProjectValidator;

/** Validates the configuration, then creates the project. */
public final class InitUseCase {

    private final ProjectCreator projectCreator;

    public InitUseCase(ProjectCreator projectCreator) {
        this.projectCreator = projectCreator;
    }

    public void execute(ProjectConfig config, boolean skipWrapper) {
        ProjectValidator.validate(config);
        projectCreator.create(config, skipWrapper);
    }
}
