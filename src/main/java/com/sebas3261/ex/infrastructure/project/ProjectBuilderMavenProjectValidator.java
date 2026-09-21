package com.sebas3261.ex.infrastructure.project;

import com.sebas3261.ex.application.ports.MavenProjectValidator;
import java.nio.file.Path;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;

/**
 * Validates the edited POM in-process by building its model with Maven's {@link ProjectBuilder}
 * (design D10). Unlike {@code mvn validate}, plugins bound to the validate phase are not run.
 */
public final class ProjectBuilderMavenProjectValidator implements MavenProjectValidator {

    private final ProjectBuilder projectBuilder;
    private final MavenSession session;
    private final Path pom;

    public ProjectBuilderMavenProjectValidator(ProjectBuilder projectBuilder, MavenSession session, Path pom) {
        this.projectBuilder = projectBuilder;
        this.session = session;
        this.pom = pom;
    }

    @Override
    public Status validate() {
        ProjectBuildingRequest request = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        request.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_3_0);
        request.setResolveDependencies(false);
        request.setProject(null);
        try {
            ProjectBuildingResult result = projectBuilder.build(pom.toFile(), request);
            boolean hasErrors = result.getProblems().stream()
                    .anyMatch(problem -> problem.getSeverity() != ModelProblem.Severity.WARNING);
            return hasErrors ? Status.FAILED : Status.PASSED;
        } catch (ProjectBuildingException e) {
            return Status.FAILED;
        }
    }
}
