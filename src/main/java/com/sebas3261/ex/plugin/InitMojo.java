package com.sebas3261.ex.plugin;

import com.sebas3261.ex.application.init.InitUseCase;
import com.sebas3261.ex.application.ports.ReportSink;
import com.sebas3261.ex.infrastructure.project.ProjectGenerator;
import com.sebas3261.ex.infrastructure.wrapper.WrapperGenerator;
import com.sebas3261.ex.plugin.support.ConsoleInteraction;
import com.sebas3261.ex.plugin.support.ExParameters;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.codehaus.plexus.components.interactivity.InputHandler;
import org.codehaus.plexus.components.interactivity.OutputHandler;

/**
 * Create a new Maven project.
 *
 * <p>Creates a standard Maven project in a new directory under the current directory: a
 * {@code pom.xml}, a {@code Main} class, an empty test source folder, and (unless disabled) the
 * Maven Wrapper. Missing values are prompted for interactively; in batch mode ({@code -B}) the
 * project name is required and the other values use their defaults.
 *
 * <p>Examples:
 * <pre>
 * mvn ex:init
 * mvn ex:init -Dex.name=my-app
 * mvn ex:init -Dex.name=my-app -Dex.java=21
 * mvn ex:init -Dex.name=my-app -Dex.groupId=com.example -Dex.package=com.example.app
 * mvn ex:init -Dex.name=my-app -Dex.wrapper=false
 * </pre>
 */
@Mojo(name = "init", requiresProject = false, aggregator = true, threadSafe = true)
public class InitMojo extends AbstractExMojo {

    /**
     * Project name: lowercase letters, digits and single hyphens, starting with a letter. Also used
     * as the artifactId and directory name. Prompted for when missing (default {@code my-project}).
     */
    @Parameter(property = ExParameters.NAME)
    private String name;

    /** Maven groupId. Prompted for when missing (default {@code com.example}). */
    @Parameter(property = ExParameters.GROUP_ID)
    private String groupId;

    /**
     * Java package of the generated {@code Main} class. Prompted for when missing, defaulting to the
     * groupId plus the project name without hyphens (the batch-mode value).
     */
    @Parameter(property = ExParameters.PACKAGE)
    private String packageName;

    /** Java release for {@code maven.compiler.release}: 8, 11, 17, 21 or 25. Prompted for when missing (default 21). */
    @Parameter(property = ExParameters.JAVA)
    private String java;

    /**
     * Set to {@code false} to skip generating the Maven Wrapper ({@code mvnw}, {@code mvnw.cmd}).
     * When unset, interactive runs ask unless name, groupId and Java version were all given.
     */
    @Parameter(property = ExParameters.WRAPPER)
    private Boolean wrapper;

    private final InputHandler input;
    private final OutputHandler output;
    private final RuntimeInformation runtime;

    @Inject
    public InitMojo(InputHandler input, OutputHandler output, RuntimeInformation runtime) {
        this.input = input;
        this.output = output;
        this.runtime = runtime;
    }

    @Override
    protected String goal() {
        return "init";
    }

    @Override
    protected void run(ReportSink report) {
        Path executionRoot = Path.of(session.getExecutionRootDirectory());
        List<WrapperGenerator.Mirror> mirrors = session.getSettings().getMirrors().stream()
                .map(mirror -> new WrapperGenerator.Mirror(mirror.getMirrorOf(), mirror.getUrl()))
                .toList();
        String repositoryUrl = WrapperGenerator.repositoryUrl(System.getenv("MVNW_REPOURL"), mirrors);
        String lineSeparator = System.lineSeparator();

        WrapperGenerator wrapperGenerator = new WrapperGenerator(runtime.getMavenVersion(), repositoryUrl, lineSeparator);
        InitUseCase useCase = new InitUseCase(new ProjectGenerator(executionRoot, wrapperGenerator, lineSeparator));
        ConsoleInteraction interaction =
                new ConsoleInteraction(input, output, session.getRequest().isInteractiveMode());

        new InitFlow(interaction, report, useCase)
                .run(new InitFlow.Parameters(name, groupId, packageName, java, wrapper));
    }
}
