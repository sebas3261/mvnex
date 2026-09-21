package com.sebas3261.ex.plugin;

import com.sebas3261.ex.application.add.AddUseCase;
import com.sebas3261.ex.application.errors.LookupNotPossibleException;
import com.sebas3261.ex.application.ports.HttpClient;
import com.sebas3261.ex.application.ports.ReportSink;
import com.sebas3261.ex.domain.dependency.DependencyRequest;
import com.sebas3261.ex.infrastructure.dependency.CanonicalVersionSelector;
import com.sebas3261.ex.infrastructure.dependency.CompositeDependencyResolver;
import com.sebas3261.ex.infrastructure.dependency.DepsDevProvider;
import com.sebas3261.ex.infrastructure.dependency.MavenCentralSearchProvider;
import com.sebas3261.ex.infrastructure.dependency.ResolverRepositoryLookup;
import com.sebas3261.ex.infrastructure.project.PomLocator;
import com.sebas3261.ex.infrastructure.project.PomProjectDependencyRepository;
import com.sebas3261.ex.infrastructure.project.ProjectBuilderMavenProjectValidator;
import com.sebas3261.ex.infrastructure.transport.ProxyChooser;
import com.sebas3261.ex.infrastructure.transport.ResolverHttpClient;
import com.sebas3261.ex.plugin.support.ConsoleInteraction;
import com.sebas3261.ex.plugin.support.ExParameters;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.ProjectBuilder;
import org.codehaus.plexus.components.interactivity.InputHandler;
import org.codehaus.plexus.components.interactivity.OutputHandler;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;

/**
 * Add a dependency to the project.
 *
 * <p>Resolves each dependency expression against Maven Central search services, asks you to pick
 * when a name matches several artifacts, skips dependencies already declared, and adds the rest to
 * the nearest {@code pom.xml} (searching upward from the current directory). The project is then
 * validated. Expressions: {@code lombok}, {@code lombok:1.18.48}, {@code org.projectlombok:lombok},
 * {@code org.projectlombok:lombok:1.18.48}.
 *
 * <p>Run interactively, the goal asks for missing values once the {@code pom.xml} is found: the
 * dependencies, and for a single dependency its version and scope. In batch mode ({@code -B})
 * nothing is asked and {@code ex.deps} is required.
 *
 * <p>Examples:
 * <pre>
 * mvn ex:add
 * mvn ex:add -Dex.deps=lombok
 * mvn ex:add -Dex.deps=junit-jupiter -Dex.scope=test
 * mvn ex:add -Dex.deps=lombok:1.18.48
 * mvn ex:add -Dex.deps=org.postgresql:postgresql
 * mvn ex:add -Dex.deps=org.projectlombok:lombok:1.18.48,org.postgresql:postgresql
 * </pre>
 */
@Mojo(name = "add", requiresProject = false, aggregator = true, threadSafe = true)
public class AddMojo extends AbstractExMojo {

    static final int SONATYPE_TIMEOUT_MILLIS = 5000;
    static final int SEARCH_MAVEN_TIMEOUT_MILLIS = 1500;
    static final int DEPS_DEV_TIMEOUT_MILLIS = 5000;

    /** Comma-separated dependency expressions to add. Prompted for when missing (no default). */
    @Parameter(property = ExParameters.DEPS)
    private List<String> deps;

    /**
     * Version for the single dependency being added. Prompted for when missing and the expression has
     * no inline version (default {@code latest}: the highest stable version).
     */
    @Parameter(property = ExParameters.VERSION)
    private String version;

    /**
     * Scope for the single dependency being added: compile, provided, runtime, test, system or import.
     * Prompted for when missing (default {@code none}: no scope element).
     */
    @Parameter(property = ExParameters.SCOPE)
    private String scope;

    /** The resolver session, for Maven's proxies, mirrors, authentication and offline mode. */
    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    private RepositorySystemSession repositorySession;

    private final InputHandler input;
    private final OutputHandler output;
    private final TransporterProvider transporters;
    private final RepositorySystem repositorySystem;
    private final ProjectBuilder projectBuilder;

    @Inject
    public AddMojo(InputHandler input, OutputHandler output, TransporterProvider transporters,
            RepositorySystem repositorySystem, ProjectBuilder projectBuilder) {
        this.input = input;
        this.output = output;
        this.transporters = transporters;
        this.repositorySystem = repositorySystem;
        this.projectBuilder = projectBuilder;
    }

    @Override
    protected String goal() {
        return "add";
    }

    @Override
    protected void run(ReportSink report) {
        // Order (design D2): locate the POM, collect and parse the values, check offline, resolve.
        // Batch runs have nothing to collect and keep failing on bad parameters before the POM search.
        if (!session.getRequest().isInteractiveMode()) {
            DependencyArgumentsParser.parse(deps, version, scope);
        }
        java.io.File requestPom = session.getRequest().getPom();
        Path pom = PomLocator.locate(requestPom == null ? null : requestPom.toPath(),
                Path.of(session.getExecutionRootDirectory()));

        ProxyChooser proxies = new ProxyChooser(repositorySession.getProxySelector(), System.getenv(),
                System.getProperties());
        String userAgent = "ex-maven-plugin/" + pluginVersion();
        ResolverRepositoryLookup repository = new ResolverRepositoryLookup(repositorySystem, repositorySession, proxies,
                override("centralUrl", ResolverRepositoryLookup.CENTRAL_URL));

        MavenCentralSearchProvider sonatype = new MavenCentralSearchProvider(
                http(proxies, SONATYPE_TIMEOUT_MILLIS, userAgent),
                override("sonatypeUrl", MavenCentralSearchProvider.SONATYPE_CENTRAL_SEARCH), repository);
        MavenCentralSearchProvider searchMaven = new MavenCentralSearchProvider(
                http(proxies, SEARCH_MAVEN_TIMEOUT_MILLIS, userAgent),
                override("searchMavenUrl", MavenCentralSearchProvider.MAVEN_CENTRAL_SEARCH), repository);
        DepsDevProvider depsDev = new DepsDevProvider(http(proxies, DEPS_DEV_TIMEOUT_MILLIS, userAgent),
                override("depsDevUrl", DepsDevProvider.BASE_URL));

        CompositeDependencyResolver resolver = new CompositeDependencyResolver(
                List.of(sonatype, searchMaven, depsDev), new CanonicalVersionSelector(repository, depsDev));
        AddUseCase useCase = new AddUseCase(resolver, new PomProjectDependencyRepository(pom),
                new ProjectBuilderMavenProjectValidator(projectBuilder, session, pom));
        ConsoleInteraction interaction =
                new ConsoleInteraction(input, output, session.getRequest().isInteractiveMode());
        AddFlow flow = new AddFlow(interaction, report, useCase);

        List<DependencyRequest> requests = flow.requests(deps, version, scope);
        if (repositorySession.isOffline()) {
            throw new LookupNotPossibleException(ResolverHttpClient.OFFLINE_MESSAGE);
        }
        flow.run(requests);
    }

    private HttpClient http(ProxyChooser proxies, int timeoutMillis, String userAgent) {
        return new ResolverHttpClient(transporters, repositorySession, proxies, timeoutMillis, userAgent);
    }

    /** Test-only endpoint override, {@code ex.internal.<name>} (design D13). */
    private String override(String name, String defaultUrl) {
        String value = property(ExParameters.INTERNAL_PREFIX + name);
        return value == null || value.isBlank() ? defaultUrl : value;
    }
}
